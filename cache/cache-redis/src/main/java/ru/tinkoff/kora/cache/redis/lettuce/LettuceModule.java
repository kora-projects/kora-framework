package ru.tinkoff.kora.cache.redis.lettuce;

import io.lettuce.core.cluster.RedisClusterClient;
import ru.tinkoff.kora.cache.redis.RedisCacheClient;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import java.time.Duration;

public interface LettuceModule {

    default LettuceClientConfig lettuceConfig(Config config, ConfigValueExtractor<LettuceClientConfig> extractor) {
        var value = config.get("lettuce");
        return extractor.extract(value);
    }

    default LettuceClientFactory lettuceClientFactory() {
        return new LettuceClientFactory();
    }

    @DefaultComponent
    default RedisCacheClient lettuceRedisClient(LettuceClientFactory factory, LettuceClientConfig config) {
        var redisClient = factory.build(config);
        if (redisClient instanceof io.lettuce.core.RedisClient rc) {
            return new LettuceRedisCacheClient(rc, config);
        } else if (redisClient instanceof RedisClusterClient rcc) {
            return new LettuceClusterRedisCacheClient(rcc);
        } else {
            throw new UnsupportedOperationException("Unknown Redis Client: " + redisClient.getClass());
        }
    }
}
