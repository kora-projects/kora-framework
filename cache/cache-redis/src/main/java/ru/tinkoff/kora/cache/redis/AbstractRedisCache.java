package ru.tinkoff.kora.cache.redis;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.cache.AsyncCache;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractRedisCache<K, V> implements AsyncCache<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(RedisCache.class);

    private final String name;
    private final RedisCacheClient redisClient;
    private final RedisCacheTelemetry telemetry;
    private final byte[] keyPrefix;

    private final RedisCacheKeyMapper<K> keyMapper;
    private final RedisCacheValueMapper<V> valueMapper;

    private final Long expireAfterAccessMillis;
    private final Long expireAfterWriteMillis;

    protected AbstractRedisCache(String name,
                                 RedisCacheConfig config,
                                 RedisCacheClient redisClient,
                                 RedisCacheTelemetry telemetry,
                                 RedisCacheKeyMapper<K> keyMapper,
                                 RedisCacheValueMapper<V> valueMapper) {
        this.name = name;
        this.redisClient = redisClient;
        this.telemetry = telemetry;
        this.keyMapper = keyMapper;
        this.valueMapper = valueMapper;
        this.expireAfterAccessMillis = (config.expireAfterAccess() == null)
            ? null
            : config.expireAfterAccess().toMillis();
        this.expireAfterWriteMillis = (config.expireAfterWrite() == null)
            ? null
            : config.expireAfterWrite().toMillis();

        if (config.keyPrefix().isEmpty()) {
            this.keyPrefix = null;
        } else {
            var prefixRaw = config.keyPrefix().getBytes(StandardCharsets.UTF_8);
            this.keyPrefix = new byte[prefixRaw.length + RedisCacheKeyMapper.DELIMITER.length];
            System.arraycopy(prefixRaw, 0, this.keyPrefix, 0, prefixRaw.length);
            System.arraycopy(RedisCacheKeyMapper.DELIMITER, 0, this.keyPrefix, prefixRaw.length, RedisCacheKeyMapper.DELIMITER.length);
        }
    }

    @Override
    public V get(@Nonnull K key) {
        if (key == null) {
            return null;
        }

        var telemetryContext = telemetry.create("GET", name);
        try {
            final byte[] keyAsBytes = mapKey(key);
            final byte[] jsonAsBytes = (expireAfterAccessMillis == null)
                ? redisClient.get(keyAsBytes).toCompletableFuture().join()
                : redisClient.getex(keyAsBytes, expireAfterAccessMillis).toCompletableFuture().join();

            final V value = valueMapper.read(jsonAsBytes);
            telemetryContext.recordSuccess(value);
            return value;
        } catch (CompletionException e) {
            telemetryContext.recordFailure(e.getCause());
            return null;
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
            return null;
        }
    }

    @Nonnull
    @Override
    public Map<K, V> get(@Nonnull Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }

        var telemetryContext = telemetry.create("GET_MANY", name);
        try {
            final Map<K, byte[]> keysByKeyBytes = keys.stream()
                .collect(Collectors.toMap(k -> k, this::mapKey, (v1, v2) -> v2));

            final byte[][] keysByBytes = keysByKeyBytes.values().toArray(byte[][]::new);
            final Map<byte[], byte[]> valueByKeys = (expireAfterAccessMillis == null)
                ? redisClient.mget(keysByBytes).toCompletableFuture().join()
                : redisClient.getex(keysByBytes, expireAfterAccessMillis).toCompletableFuture().join();

            final Map<K, V> keyToValue = new HashMap<>();
            for (var entry : keysByKeyBytes.entrySet()) {
                valueByKeys.forEach((k, v) -> {
                    if (Arrays.equals(entry.getValue(), k)) {
                        var value = valueMapper.read(v);
                        keyToValue.put(entry.getKey(), value);
                    }
                });
            }

            telemetryContext.recordSuccess(keyToValue);
            return keyToValue;
        } catch (CompletionException e) {
            telemetryContext.recordFailure(e.getCause());
            return Collections.emptyMap();
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
            return Collections.emptyMap();
        }
    }

    @Nonnull
    @Override
    public V put(@Nonnull K key, @Nonnull V value) {
        if (key == null || value == null) {
            return null;
        }

        var telemetryContext = telemetry.create("PUT", name);

        try {
            final byte[] keyAsBytes = mapKey(key);
            final byte[] valueAsBytes = valueMapper.write(value);
            if (expireAfterWriteMillis == null) {
                redisClient.set(keyAsBytes, valueAsBytes).toCompletableFuture().join();
            } else {
                redisClient.psetex(keyAsBytes, valueAsBytes, expireAfterWriteMillis).toCompletableFuture().join();
            }
            telemetryContext.recordSuccess();
            return value;
        } catch (CompletionException e) {
            telemetryContext.recordFailure(e.getCause());
            return value;
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
            return value;
        }
    }

    @Nonnull
    @Override
    public Map<K, V> put(@Nonnull Map<K, V> keyAndValues) {
        if (keyAndValues == null || keyAndValues.isEmpty()) {
            return Collections.emptyMap();
        }

        var telemetryContext = telemetry.create("PUT_MANY", name);

        try {
            var keyAndValuesAsBytes = new HashMap<byte[], byte[]>();
            keyAndValues.forEach((k, v) -> {
                final byte[] keyAsBytes = mapKey(k);
                final byte[] valueAsBytes = valueMapper.write(v);
                keyAndValuesAsBytes.put(keyAsBytes, valueAsBytes);
            });

            if (expireAfterWriteMillis == null) {
                redisClient.mset(keyAndValuesAsBytes).toCompletableFuture().join();
            } else {
                redisClient.psetex(keyAndValuesAsBytes, expireAfterWriteMillis).toCompletableFuture().join();
            }

            telemetryContext.recordSuccess();
            return keyAndValues;
        } catch (CompletionException e) {
            telemetryContext.recordFailure(e.getCause());
            return keyAndValues;
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
            return keyAndValues;
        }
    }

    @Override
    public V computeIfAbsent(@Nonnull K key, @Nonnull Function<K, V> mappingFunction) {
        if (key == null) {
            return mappingFunction.apply(key);
        }

        var telemetryContext = telemetry.create("COMPUTE_IF_ABSENT", name);

        V fromCache = null;
        try {
            final byte[] keyAsBytes = mapKey(key);
            final byte[] jsonAsBytes = (expireAfterAccessMillis == null)
                ? redisClient.get(keyAsBytes).toCompletableFuture().join()
                : redisClient.getex(keyAsBytes, expireAfterAccessMillis).toCompletableFuture().join();

            fromCache = valueMapper.read(jsonAsBytes);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        if (fromCache != null) {
            telemetryContext.recordSuccess();
            return fromCache;
        }

        try {
            var value = mappingFunction.apply(key);
            if (value != null) {
                try {
                    final byte[] keyAsBytes = mapKey(key);
                    final byte[] valueAsBytes = valueMapper.write(value);
                    if (expireAfterWriteMillis == null) {
                        redisClient.set(keyAsBytes, valueAsBytes).toCompletableFuture().join();
                    } else {
                        redisClient.psetex(keyAsBytes, valueAsBytes, expireAfterWriteMillis).toCompletableFuture().join();
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }

            telemetryContext.recordSuccess();
            return value;
        } catch (CompletionException e) {
            telemetryContext.recordFailure(e.getCause());
            return null;
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
            return null;
        }
    }

    @Nonnull
    @Override
    public Map<K, V> computeIfAbsent(@Nonnull Collection<K> keys, @Nonnull Function<Set<K>, Map<K, V>> mappingFunction) {
        if (keys == null || keys.isEmpty()) {
            return mappingFunction.apply(Collections.emptySet());
        }

        var telemetryContext = telemetry.create("COMPUTE_IF_ABSENT_MANY", name);

        final Map<K, V> fromCache = new HashMap<>();
        try {
            final Map<K, byte[]> keysByKeyBytes = keys.stream()
                .collect(Collectors.toMap(k -> k, this::mapKey, (v1, v2) -> v2));

            final byte[][] keysByBytes = keysByKeyBytes.values().toArray(byte[][]::new);
            final Map<byte[], byte[]> valueByKeys = (expireAfterAccessMillis == null)
                ? redisClient.mget(keysByBytes).toCompletableFuture().join()
                : redisClient.getex(keysByBytes, expireAfterAccessMillis).toCompletableFuture().join();

            for (var entry : keysByKeyBytes.entrySet()) {
                valueByKeys.forEach((k, v) -> {
                    if (Arrays.equals(entry.getValue(), k)) {
                        var value = valueMapper.read(v);
                        fromCache.put(entry.getKey(), value);
                    }
                });
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        if (fromCache.size() == keys.size()) {
            telemetryContext.recordSuccess();
            return fromCache;
        }

        var missingKeys = keys.stream()
            .filter(k -> !fromCache.containsKey(k))
            .collect(Collectors.toSet());

        try {
            var values = mappingFunction.apply(missingKeys);
            if (!values.isEmpty()) {
                try {
                    var keyAndValuesAsBytes = new HashMap<byte[], byte[]>();
                    values.forEach((k, v) -> {
                        final byte[] keyAsBytes = mapKey(k);
                        final byte[] valueAsBytes = valueMapper.write(v);
                        keyAndValuesAsBytes.put(keyAsBytes, valueAsBytes);
                    });

                    if (expireAfterWriteMillis == null) {
                        redisClient.mset(keyAndValuesAsBytes).toCompletableFuture().join();
                    } else {
                        redisClient.psetex(keyAndValuesAsBytes, expireAfterWriteMillis).toCompletableFuture().join();
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }

            telemetryContext.recordSuccess();
            fromCache.putAll(values);
            return fromCache;
        } catch (CompletionException e) {
            telemetryContext.recordFailure(e.getCause());
            return fromCache;
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
            return fromCache;
        }
    }

    @Override
    public void invalidate(@Nonnull K key) {
        if (key != null) {
            final byte[] keyAsBytes = mapKey(key);
            var telemetryContext = telemetry.create("INVALIDATE", name);

            try {
                redisClient.del(keyAsBytes).toCompletableFuture().join();
                telemetryContext.recordSuccess();
            } catch (CompletionException e) {
                telemetryContext.recordFailure(e.getCause());
            } catch (Exception e) {
                telemetryContext.recordFailure(e);
            }
        }
    }

    @Override
    public void invalidate(@Nonnull Collection<K> keys) {
        if (keys != null && !keys.isEmpty()) {
            var telemetryContext = telemetry.create("INVALIDATE_MANY", name);

            try {
                final byte[][] keysAsBytes = keys.stream()
                    .map(this::mapKey)
                    .toArray(byte[][]::new);

                redisClient.del(keysAsBytes).toCompletableFuture().join();
                telemetryContext.recordSuccess();
            } catch (CompletionException e) {
                telemetryContext.recordFailure(e.getCause());
            } catch (Exception e) {
                telemetryContext.recordFailure(e);
            }
        }
    }

    @Override
    public void invalidateAll() {
        var telemetryContext = telemetry.create("INVALIDATE_ALL", name);

        try {
            redisClient.flushAll().toCompletableFuture().join();
            telemetryContext.recordSuccess();
        } catch (CompletionException e) {
            telemetryContext.recordFailure(e.getCause());
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
        }
    }

    @Nonnull
    @Override
    public CompletionStage<V> getAsync(@Nonnull K key) {
        if (key == null) {
            return CompletableFuture.completedFuture(null);
        }

        var telemetryContext = telemetry.create("GET", name);
        final byte[] keyAsBytes = mapKey(key);

        CompletionStage<byte[]> responseCompletionStage = (expireAfterAccessMillis == null)
            ? redisClient.get(keyAsBytes)
            : redisClient.getex(keyAsBytes, expireAfterAccessMillis);

        return responseCompletionStage
            .thenApply(jsonAsBytes -> {
                final V value = valueMapper.read(jsonAsBytes);
                telemetryContext.recordSuccess(value);
                return value;
            })
            .exceptionally(e -> {
                telemetryContext.recordFailure(e);
                return null;
            });
    }

    @Nonnull
    @Override
    public CompletionStage<Map<K, V>> getAsync(@Nonnull Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        var telemetryContext = telemetry.create("GET_MANY", name);
        var keysByKeyByte = keys.stream()
            .collect(Collectors.toMap(k -> k, this::mapKey, (v1, v2) -> v2));

        var keysAsBytes = keysByKeyByte.values().toArray(byte[][]::new);
        var responseCompletionStage = (expireAfterAccessMillis == null)
            ? redisClient.mget(keysAsBytes)
            : redisClient.getex(keysAsBytes, expireAfterAccessMillis);

        return responseCompletionStage
            .thenApply(valuesByKeys -> {
                final Map<K, V> keyToValue = new HashMap<>();
                for (var entry : keysByKeyByte.entrySet()) {
                    valuesByKeys.forEach((k, v) -> {
                        if (Arrays.equals(entry.getValue(), k)) {
                            var value = valueMapper.read(v);
                            keyToValue.put(entry.getKey(), value);
                        }
                    });
                }
                telemetryContext.recordSuccess(keyToValue);
                return keyToValue;
            })
            .exceptionally(e -> {
                telemetryContext.recordFailure(e);
                return Collections.emptyMap();
            });
    }

    @Nonnull
    @Override
    public CompletionStage<V> putAsync(@Nonnull K key, @Nonnull V value) {
        if (key == null) {
            return CompletableFuture.completedFuture(value);
        }

        var telemetryContext = telemetry.create("PUT", name);
        final byte[] keyAsBytes = mapKey(key);
        final byte[] valueAsBytes = valueMapper.write(value);
        final CompletionStage<Boolean> responseCompletionStage = (expireAfterWriteMillis == null)
            ? redisClient.set(keyAsBytes, valueAsBytes)
            : redisClient.psetex(keyAsBytes, valueAsBytes, expireAfterWriteMillis);

        return responseCompletionStage
            .thenApply(r -> {
                telemetryContext.recordSuccess();
                return value;
            })
            .exceptionally(e -> {
                telemetryContext.recordFailure(e);
                return value;
            });
    }

    @Nonnull
    @Override
    public CompletionStage<Map<K, V>> putAsync(@Nonnull Map<K, V> keyAndValues) {
        if (keyAndValues == null || keyAndValues.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        var telemetryContext = telemetry.create("PUT_MANY", name);
        var keyAndValuesAsBytes = new HashMap<byte[], byte[]>();
        keyAndValues.forEach((k, v) -> {
            final byte[] keyAsBytes = mapKey(k);
            final byte[] valueAsBytes = valueMapper.write(v);
            keyAndValuesAsBytes.put(keyAsBytes, valueAsBytes);
        });

        var responseCompletionStage = (expireAfterWriteMillis == null)
            ? redisClient.mset(keyAndValuesAsBytes)
            : redisClient.psetex(keyAndValuesAsBytes, expireAfterAccessMillis);

        return responseCompletionStage
            .thenApply(r -> {
                telemetryContext.recordSuccess();
                return keyAndValues;
            })
            .exceptionally(e -> {
                telemetryContext.recordFailure(e);
                return keyAndValues;
            });
    }

    @Override
    public CompletionStage<V> computeIfAbsentAsync(@Nonnull K key, @Nonnull Function<K, CompletionStage<V>> mappingFunction) {
        if (key == null) {
            return CompletableFuture.completedFuture(null);
        }

        var telemetryContext = telemetry.create("COMPUTE_IF_ABSENT", name);
        final byte[] keyAsBytes = mapKey(key);
        final CompletionStage<byte[]> responseCompletionStage = (expireAfterAccessMillis == null)
            ? redisClient.get(keyAsBytes)
            : redisClient.getex(keyAsBytes, expireAfterAccessMillis);

        return responseCompletionStage
            .thenApply(valueMapper::read)
            .exceptionally(e -> null)
            .thenCompose(fromCache -> {
                if (fromCache != null) {
                    return CompletableFuture.completedFuture(fromCache);
                }

                return mappingFunction.apply(key)
                    .thenCompose(value -> {
                        if (value == null) {
                            return CompletableFuture.completedFuture(null);
                        }

                        final byte[] valueAsBytes = valueMapper.write(value);
                        var putFutureResponse = (expireAfterWriteMillis == null)
                            ? redisClient.set(keyAsBytes, valueAsBytes)
                            : redisClient.psetex(keyAsBytes, valueAsBytes, expireAfterWriteMillis);

                        return putFutureResponse
                            .thenApply(v -> {
                                telemetryContext.recordSuccess();
                                return value;
                            });
                    })
                    .exceptionally(e -> {
                        telemetryContext.recordFailure(e);
                        return null;
                    });
            });
    }

    @Nonnull
    @Override
    public CompletionStage<Map<K, V>> computeIfAbsentAsync(@Nonnull Collection<K> keys, @Nonnull Function<Set<K>, CompletionStage<Map<K, V>>> mappingFunction) {
        if (keys == null || keys.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        var telemetryContext = telemetry.create("COMPUTE_IF_ABSENT_MANY", name);
        final Map<K, byte[]> keysByKeyBytes = keys.stream()
            .collect(Collectors.toMap(k -> k, this::mapKey, (v1, v2) -> v2));

        final byte[][] keysByBytes = keysByKeyBytes.values().toArray(byte[][]::new);
        var responseCompletionStage = (expireAfterAccessMillis == null)
            ? redisClient.mget(keysByBytes)
            : redisClient.getex(keysByBytes, expireAfterAccessMillis);

        return responseCompletionStage
            .thenApply(valueByKeys -> {
                final Map<K, V> fromCache = new HashMap<>();
                for (var entry : keysByKeyBytes.entrySet()) {
                    valueByKeys.forEach((k, v) -> {
                        if (Arrays.equals(entry.getValue(), k)) {
                            var value = valueMapper.read(v);
                            fromCache.put(entry.getKey(), value);
                        }
                    });
                }

                return fromCache;
            })
            .exceptionally(e -> null)
            .thenCompose(fromCache -> {
                if (fromCache.size() == keys.size()) {
                    return CompletableFuture.completedFuture(fromCache);
                }

                var missingKeys = keys.stream()
                    .filter(k -> !fromCache.containsKey(k))
                    .collect(Collectors.toSet());

                return mappingFunction.apply(missingKeys)
                    .thenCompose(values -> {
                        if (values.isEmpty()) {
                            return CompletableFuture.completedFuture(fromCache);
                        }

                        var keyAndValuesAsBytes = new HashMap<byte[], byte[]>();
                        values.forEach((k, v) -> {
                            final byte[] keyAsBytes = mapKey(k);
                            final byte[] valueAsBytes = valueMapper.write(v);
                            keyAndValuesAsBytes.put(keyAsBytes, valueAsBytes);
                        });

                        var putCompletionStage = (expireAfterAccessMillis == null)
                            ? redisClient.mset(keyAndValuesAsBytes)
                            : redisClient.psetex(keyAndValuesAsBytes, expireAfterAccessMillis);

                        return putCompletionStage
                            .thenApply(v -> {
                                telemetryContext.recordSuccess();
                                fromCache.putAll(values);
                                return fromCache;
                            });
                    })
                    .exceptionally(e -> {
                        telemetryContext.recordFailure(e);
                        return null;
                    });
            });
    }

    @Nonnull
    @Override
    public CompletionStage<Boolean> invalidateAsync(@Nonnull K key) {
        if (key == null) {
            return CompletableFuture.completedFuture(false);
        }

        var telemetryContext = telemetry.create("INVALIDATE", name);
        final byte[] keyAsBytes = mapKey(key);
        return redisClient.del(keyAsBytes)
            .thenApply(r -> {
                telemetryContext.recordSuccess();
                return true;
            })
            .exceptionally(e -> {
                telemetryContext.recordFailure(e);
                return false;
            });
    }

    @Override
    public CompletionStage<Boolean> invalidateAsync(@Nonnull Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        var telemetryContext = telemetry.create("INVALIDATE_MANY", name);
        final byte[][] keyAsBytes = keys.stream()
            .distinct()
            .map(this::mapKey)
            .toArray(byte[][]::new);

        return redisClient.del(keyAsBytes)
            .thenApply(r -> {
                telemetryContext.recordSuccess();
                return true;
            })
            .exceptionally(e -> {
                telemetryContext.recordFailure(e);
                return false;
            });
    }

    @Nonnull
    @Override
    public CompletionStage<Boolean> invalidateAllAsync() {
        var telemetryContext = telemetry.create("INVALIDATE_ALL", name);
        return redisClient.flushAll()
            .thenApply(r -> {
                telemetryContext.recordSuccess();
                return r;
            })
            .exceptionally(e -> {
                telemetryContext.recordFailure(e);
                return false;
            });
    }

    private byte[] mapKey(K key) {
        final byte[] suffixAsBytes = keyMapper.apply(key);
        if (this.keyPrefix == null) {
            return suffixAsBytes;
        } else {
            var keyAsBytes = new byte[keyPrefix.length + suffixAsBytes.length];
            System.arraycopy(this.keyPrefix, 0, keyAsBytes, 0, this.keyPrefix.length);
            System.arraycopy(suffixAsBytes, 0, keyAsBytes, this.keyPrefix.length, suffixAsBytes.length);

            return keyAsBytes;
        }
    }
}
