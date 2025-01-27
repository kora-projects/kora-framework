package ru.tinkoff.kora.cache.redis.lettuce;

import io.lettuce.core.FlushMode;
import io.lettuce.core.GetExArgs;
import io.lettuce.core.KeyValue;
import io.lettuce.core.Value;
import io.lettuce.core.api.async.RedisKeyAsyncCommands;
import io.lettuce.core.api.async.RedisServerAsyncCommands;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.support.AsyncConnectionPoolSupport;
import io.lettuce.core.support.BoundedAsyncPool;
import io.lettuce.core.support.BoundedPoolConfig;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.cache.redis.RedisCacheClient;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

final class LettuceClusterRedisCacheClient implements RedisCacheClient, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(LettuceClusterRedisCacheClient.class);

    private final RedisClusterClient redisClient;

    // use for pipeline commands
    private BoundedAsyncPool<StatefulRedisClusterConnection<byte[], byte[]>> pool;

    // always use async cause sync uses JDK Proxy
    private RedisStringAsyncCommands<byte[], byte[]> stringCommands;
    private RedisServerAsyncCommands<byte[], byte[]> serverCommands;
    private RedisKeyAsyncCommands<byte[], byte[]> keyCommands;

    LettuceClusterRedisCacheClient(RedisClusterClient redisClient) {
        this.redisClient = redisClient;
    }

    @Nonnull
    @Override
    public CompletionStage<byte[]> get(byte[] key) {
        return stringCommands.get(key);
    }

    @Nonnull
    @Override
    public CompletionStage<Map<byte[], byte[]>> mget(byte[][] keys) {
        return stringCommands.mget(keys)
            .thenApply(r -> r.stream()
                .filter(Value::hasValue)
                .collect(Collectors.toMap(KeyValue::getKey, Value::getValue)));
    }

    @Nonnull
    @Override
    public CompletionStage<byte[]> getex(byte[] key, long expireAfterMillis) {
        return stringCommands.getex(key, GetExArgs.Builder.ex(Duration.ofMillis(expireAfterMillis)));
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public CompletionStage<Map<byte[], byte[]>> getex(byte[][] keys, long expireAfterMillis) {
        return pool.acquire().thenCompose(connection -> {
            connection.setAutoFlushCommands(false);

            List<CompletableFuture<?>> futures = new ArrayList<>();

            var async = connection.async();
            for (byte[] key : keys) {
                var future = async.getex(key, GetExArgs.Builder.ex(Duration.ofMillis(expireAfterMillis)))
                    .thenApply(v -> (v == null) ? null : Map.entry(key, v))
                    .toCompletableFuture();

                futures.add(future);
            }

            connection.flushCommands();
            connection.setAutoFlushCommands(true);

            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(_void -> futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .map(v -> ((Map.Entry<byte[], byte[]>) v))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .whenComplete((s, throwable) -> pool.release(connection));
        });
    }

    @Nonnull
    @Override
    public CompletionStage<Boolean> set(byte[] key, byte[] value) {
        return stringCommands.set(key, value).thenApply(r -> true);
    }

    @Override
    public CompletionStage<Boolean> mset(Map<byte[], byte[]> keyAndValue) {
        return stringCommands.mset(keyAndValue).thenApply(r -> true);
    }

    @Nonnull
    @Override
    public CompletionStage<Boolean> psetex(byte[] key, byte[] value, long expireAfterMillis) {
        return stringCommands.psetex(key, expireAfterMillis, value).thenApply(r -> true);
    }

    @Nonnull
    @Override
    public CompletionStage<Boolean> psetex(Map<byte[], byte[]> keyAndValue, long expireAfterMillis) {
        return pool.acquire().thenCompose(connection -> {
            connection.setAutoFlushCommands(false);

            List<CompletableFuture<?>> futures = new ArrayList<>();

            var async = connection.async();
            for (Map.Entry<byte[], byte[]> entry : keyAndValue.entrySet()) {
                var future = async.psetex(entry.getKey(), expireAfterMillis, entry.getValue())
                    .thenApply(v -> true)
                    .toCompletableFuture();

                futures.add(future);
            }

            connection.flushCommands();
            connection.setAutoFlushCommands(true);

            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(_void -> true)
                .whenComplete((s, throwable) -> pool.release(connection));
        });
    }

    @Nonnull
    @Override
    public CompletionStage<Long> del(byte[] key) {
        return keyCommands.del(key);
    }

    @Nonnull
    @Override
    public CompletionStage<Long> del(byte[][] keys) {
        return keyCommands.del(keys);
    }

    @Nonnull
    @Override
    public CompletionStage<Boolean> flushAll() {
        return serverCommands.flushall(FlushMode.SYNC).thenApply(r -> true);
    }

    @Override
    public void init() {
        logger.debug("Redis Client (Lettuce) starting...");
        final long started = TimeUtils.started();

        final BoundedPoolConfig poolConfig = BoundedPoolConfig.builder()
            .maxTotal(Math.max(Runtime.getRuntime().availableProcessors(), 1) * 4)
            .maxIdle(Math.max(Runtime.getRuntime().availableProcessors(), 1) * 4)
            .minIdle(0)
            .testOnAcquire(false)
            .testOnCreate(false)
            .testOnRelease(false)
            .build();

        this.pool = AsyncConnectionPoolSupport.createBoundedObjectPool(() -> redisClient.connectAsync(ByteArrayCodec.INSTANCE), poolConfig, false);
        var redisConnection = redisClient.connect(ByteArrayCodec.INSTANCE);
        var asyncCommands = redisConnection.async();
        this.keyCommands = asyncCommands;
        this.serverCommands = asyncCommands;
        this.stringCommands = asyncCommands;

        logger.info("Redis Client (Lettuce) started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        logger.debug("Redis Client (Lettuce) stopping...");
        final long started = TimeUtils.started();

        this.pool.close();
        this.redisClient.shutdown();

        logger.info("Redis Client (Lettuce) stopped in {}", TimeUtils.tookForLogging(started));
    }
}
