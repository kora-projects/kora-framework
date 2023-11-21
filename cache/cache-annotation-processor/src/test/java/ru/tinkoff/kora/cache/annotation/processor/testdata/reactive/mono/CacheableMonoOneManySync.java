package ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache11;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache12;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache13;

import java.math.BigDecimal;

public class CacheableMonoOneManySync {

    public String value = "1";

    @Cacheable(DummyCache11.class)
    @Cacheable(DummyCache13.class)
    public Mono<String> getValue(String arg1) {
        return Mono.just(value);
    }

    @CachePut(value = DummyCache11.class, parameters = {"arg1"})
    @CachePut(value = DummyCache13.class, parameters = {"arg1"})
    public Mono<String> putValue(BigDecimal arg2, String arg3, String arg1) {
        return Mono.just(value);
    }

    @CacheInvalidate(DummyCache11.class)
    @CacheInvalidate(DummyCache13.class)
    public Mono<Void> evictValue(String arg1) {
        return Mono.empty();
    }

    @CacheInvalidate(value = DummyCache11.class, invalidateAll = true)
    @CacheInvalidate(value = DummyCache13.class, invalidateAll = true)
    public Mono<Void> evictAll() {
        return Mono.empty();
    }
}
