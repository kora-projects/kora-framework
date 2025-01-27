package ru.tinkoff.kora.cache.redis;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.cache.redis.testdata.DummyCache;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

abstract class AbstractAsyncCacheTests extends CacheRunner {

    protected DummyCache cache = null;

    @Test
    void getWhenCacheEmpty() {
        // given
        var key = "1";

        // when
        assertNull(cache.getAsync(key).toCompletableFuture().join());
    }

    @Test
    void getWhenCacheFilled() {
        // given
        var key = "1";
        var value = "1";

        // when
        cache.putAsync(key, value).toCompletableFuture().join();

        // then
        final String fromCache = cache.getAsync(key).toCompletableFuture().join();
        assertEquals(value, fromCache);
    }

    @Test
    void getMultiWhenCacheEmpty() {
        // given
        List<String> keys = List.of("1", "2");

        // when
        Map<String, String> keyToValue = cache.getAsync(keys).toCompletableFuture().join();
        assertTrue(keyToValue.isEmpty());
    }

    @Test
    void getMultiWhenCacheFilledPartly() {
        // given
        List<String> keys = List.of("1");
        for (String key : keys) {
            cache.putAsync(key, key).toCompletableFuture().join();
        }

        // when
        Map<String, String> keyToValue = cache.getAsync(keys).toCompletableFuture().join();
        assertEquals(1, keyToValue.size());
        keyToValue.forEach((k, v) -> assertTrue(keys.stream().anyMatch(key -> key.equals(k) && key.equals(v))));
    }

    @Test
    void getMultiWhenCacheFilled() {
        // given
        List<String> keys = List.of("1", "2");
        for (String key : keys) {
            cache.putAsync(key, key).toCompletableFuture().join();
        }

        // when
        Map<String, String> keyToValue = cache.getAsync(keys).toCompletableFuture().join();
        assertEquals(2, keyToValue.size());
        keyToValue.forEach((k, v) -> assertTrue(keys.stream().anyMatch(key -> key.equals(k) && key.equals(v))));
    }

    @Test
    void computeIfAbsentWhenCacheEmpty() {
        // given

        // when
        assertNull(cache.getAsync("1").toCompletableFuture().join());
        final String valueComputed = cache.computeIfAbsent("1", k -> "1");
        assertEquals("1", valueComputed);

        // then
        final String cached = cache.getAsync("1").toCompletableFuture().join();
        assertEquals(valueComputed, cached);
    }

    @Test
    void computeIfAbsentMultiWhenCacheEmpty() {
        // given
        List<String> keys = List.of("1", "2");
        for (String key : keys) {
            assertNull(cache.getAsync(key).toCompletableFuture().join());
        }

        // when
        final Map<String, String> valueComputed = cache.computeIfAbsentAsync(keys, keysCompute -> {
            if (keysCompute.size() == 2) {
                return CompletableFuture.completedFuture(Map.of("1", "1", "2", "2"));
            } else if ("1".equals(keysCompute.iterator().next())) {
                return CompletableFuture.completedFuture(Map.of("1", "1"));
            } else if ("2".equals(keysCompute.iterator().next())) {
                return CompletableFuture.completedFuture(Map.of("2", "2"));
            }

            throw new IllegalStateException("Should not happen");
        }).toCompletableFuture().join();
        assertEquals(2, valueComputed.size());
        assertEquals(Set.copyOf(keys), valueComputed.keySet());
        assertEquals(Set.copyOf(keys), Set.copyOf(valueComputed.values()));

        // then
        final Map<String, String> cached = cache.getAsync(keys).toCompletableFuture().join();
        assertEquals(valueComputed, cached);
    }

    @Test
    void computeIfAbsentMultiOneWhenCachePartly() {
        // given
        List<String> keys = List.of("1");
        for (String key : keys) {
            assertNull(cache.getAsync(key).toCompletableFuture().join());
            cache.putAsync(key, key).toCompletableFuture().join();
        }

        // when
        final Map<String, String> valueComputed = cache.computeIfAbsentAsync(keys, keysCompute -> {
            if (keysCompute.size() == 2) {
                return CompletableFuture.completedFuture(Map.of("1", "1", "2", "2"));
            } else if ("1".equals(keysCompute.iterator().next())) {
                return CompletableFuture.completedFuture(Map.of("1", "1"));
            } else if ("2".equals(keysCompute.iterator().next())) {
                return CompletableFuture.completedFuture(Map.of("2", "2"));
            }

            throw new IllegalStateException("Should not happen");
        }).toCompletableFuture().join();
        assertEquals(1, valueComputed.size());
        assertEquals(Set.copyOf(keys), valueComputed.keySet());
        assertEquals(Set.copyOf(keys), Set.copyOf(valueComputed.values()));

        // then
        final Map<String, String> cached = cache.getAsync(keys).toCompletableFuture().join();
        assertEquals(valueComputed, cached);
    }

    @Test
    void computeIfAbsentMultiAllWhenCachePartly() {
        // given
        List<String> keys = List.of("1");
        for (String key : keys) {
            assertNull(cache.getAsync(key).toCompletableFuture().join());
            cache.putAsync(key, key).toCompletableFuture().join();
        }

        // when
        final Map<String, String> valueComputed = cache.computeIfAbsentAsync(Set.of("1", "2"), keysCompute -> {
            if (keysCompute.size() == 2) {
                return CompletableFuture.completedFuture(Map.of("1", "1", "2", "2"));
            } else if ("1".equals(keysCompute.iterator().next())) {
                return CompletableFuture.completedFuture(Map.of("1", "1"));
            } else if ("2".equals(keysCompute.iterator().next())) {
                return CompletableFuture.completedFuture(Map.of("2", "2"));
            }

            throw new IllegalStateException("Should not happen");
        }).toCompletableFuture().join();
        assertEquals(2, valueComputed.size());
        assertEquals(Set.of("1", "2"), valueComputed.keySet());
        assertEquals(Set.of("1", "2"), Set.copyOf(valueComputed.values()));

        // then
        final Map<String, String> cached = cache.getAsync(Set.of("1", "2")).toCompletableFuture().join();
        assertEquals(valueComputed, cached);
    }

    @Test
    void computeIfAbsentMultiWhenCacheFilled() {
        // given
        List<String> keys = List.of("1", "2");
        for (String key : keys) {
            assertNull(cache.getAsync(key).toCompletableFuture().join());
            cache.putAsync(key, key).toCompletableFuture().join();
        }

        // when
        final Map<String, String> valueComputed = cache.computeIfAbsentAsync(keys, keysCompute -> {
            if (keysCompute.size() == 2) {
                return CompletableFuture.completedFuture(Map.of("1", "???", "2", "???"));
            } else if ("1".equals(keysCompute.iterator().next())) {
                return CompletableFuture.completedFuture(Map.of("1", "???"));
            } else if ("2".equals(keysCompute.iterator().next())) {
                return CompletableFuture.completedFuture(Map.of("2", "???"));
            }

            throw new IllegalStateException("Should not happen");
        }).toCompletableFuture().join();
        assertEquals(2, valueComputed.size());
        assertEquals(Set.copyOf(keys), valueComputed.keySet());
        assertEquals(Set.copyOf(keys), Set.copyOf(valueComputed.values()));

        // then
        final Map<String, String> cached = cache.getAsync(keys).toCompletableFuture().join();
        assertEquals(valueComputed, cached);
    }

    @Test
    void getWrongKeyWhenCacheFilled() {
        // given
        var key = "1";
        var value = "1";

        // when
        cache.putAsync(key, value).toCompletableFuture().join();

        // then
        final String fromCache = cache.getAsync("2").toCompletableFuture().join();
        assertNull(fromCache);
    }

    @Test
    void getWhenCacheInvalidate() {
        // given
        var key = "1";
        var value = "1";
        cache.putAsync(key, value).toCompletableFuture().join();

        // when
        cache.invalidate(key);

        // then
        final String fromCache = cache.getAsync(key).toCompletableFuture().join();
        assertNull(fromCache);
    }

    @Test
    void getFromCacheWhenCacheInvalidateAll() {
        // given
        var key = "1";
        var value = "1";
        cache.putAsync(key, value).toCompletableFuture().join();

        // when
        cache.invalidateAll();

        // then
        final String fromCache = cache.getAsync(key).toCompletableFuture().join();
        assertNull(fromCache);
    }
}
