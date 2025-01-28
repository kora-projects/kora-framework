package ru.tinkoff.kora.cache.redis.lettuce;

import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ConfigValueExtractor
public interface LettuceClientConfig {

    String uri();

    @Nullable
    Integer database();

    @Nullable
    String user();

    @Nullable
    String password();

    default ProtocolVersion protocol() {
        return ProtocolVersion.RESP3;
    }

    default Duration socketTimeout() {
        return Duration.ofSeconds(SocketOptions.DEFAULT_CONNECT_TIMEOUT);
    }

    default Duration commandTimeout() {
        return Duration.ofSeconds(RedisURI.DEFAULT_TIMEOUT);
    }
}
