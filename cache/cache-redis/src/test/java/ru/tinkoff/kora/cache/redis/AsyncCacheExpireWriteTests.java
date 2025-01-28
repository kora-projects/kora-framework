package ru.tinkoff.kora.cache.redis;

import io.lettuce.core.FlushMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.test.redis.RedisParams;
import ru.tinkoff.kora.test.redis.RedisTestContainer;

import java.time.Duration;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RedisTestContainer
class AsyncCacheExpireWriteTests extends AbstractAsyncCacheTests {

    @BeforeEach
    void setup(RedisParams redisParams) throws Exception {
        redisParams.execute(cmd -> cmd.flushall(FlushMode.SYNC));
        if (cache == null) {
            cache = createCacheExpireWrite(redisParams, Duration.ofSeconds(1));
        }
    }
}
