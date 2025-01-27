package ru.tinkoff.kora.cache.redis;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.cache.redis.testdata.DummyCache;

import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class AbstractSyncCacheTests extends CacheRunner {

    protected DummyCache cache = null;

    @Test
    void getWhenCacheEmpty() {
        // given
        var key = "1";

        // when
        assertNull(cache.get(key));
    }

    @Test
    void getWhenCacheFilled() {
        // given
        var key = "1";
        var value = "1";

        // when
        cache.put(key, value);

        // then
        final String fromCache = cache.get(key);
        assertEquals(value, fromCache);
    }

    @Test
    void getMultiWhenCacheEmpty() {
        // given
        List<String> keys = List.of("1", "2");

        // when
        Map<String, String> keyToValue = cache.get(keys);
        assertTrue(keyToValue.isEmpty());
    }

    @Test
    void getMultiWhenCacheFilledPartly() {
        // given
        List<String> keys = List.of("1");
        for (String key : keys) {
            assertNull(cache.get(key));
            cache.put(key, key);
        }

        // when
        Map<String, String> keyToValue = cache.get(keys);
        assertEquals(1, keyToValue.size());
        keyToValue.forEach((k, v) -> assertTrue(keys.stream().anyMatch(key -> key.equals(k) && key.equals(v))));
    }

    @Test
    void getMultiWhenCacheFilled() {
        // given
        List<String> keys = List.of("1", "2");
        for (String key : keys) {
            assertNull(cache.get(key));
            cache.put(key, key);
        }

        // when
        Map<String, String> keyToValue = cache.get(keys);
        assertEquals(2, keyToValue.size());
        keyToValue.forEach((k, v) -> assertTrue(keys.stream().anyMatch(key -> key.equals(k) && key.equals(v))));
    }

    @Test
    void computeIfAbsentWhenCacheEmpty() {
        // given

        // when
        assertNull(cache.get("1"));
        final String valueComputed = cache.computeIfAbsent("1", k -> "1");
        assertEquals("1", valueComputed);

        // then
        final String cached = cache.get("1");
        assertEquals(valueComputed, cached);
    }

    @Test
    void computeIfAbsentMultiWhenCacheEmpty() {
        // given
        List<String> keys = List.of("1", "2");
        for (String key : keys) {
            assertNull(cache.get(key));
        }

        // when
        final Map<String, String> valueComputed = cache.computeIfAbsent(keys, keysCompute -> {
            if (keysCompute.size() == 2) {
                return Map.of("1", "1", "2", "2");
            }

            throw new IllegalStateException("Should not happen");
        });
        assertEquals(2, valueComputed.size());
        assertEquals(Set.copyOf(keys), valueComputed.keySet());
        assertEquals(Set.copyOf(keys), Set.copyOf(valueComputed.values()));

        // then
        final Map<String, String> cached = cache.get(keys);
        assertEquals(valueComputed, cached);
    }

    @Test
    void computeIfAbsentMultiOneWhenCachePartly() {
        // given
        List<String> keys = List.of("1");
        for (String key : keys) {
            assertNull(cache.get(key));
            cache.put(key, key);
        }

        // when
        final Map<String, String> valueComputed = cache.computeIfAbsent(keys, keysCompute -> {
            throw new IllegalStateException("Should not happen");
        });
        assertEquals(1, valueComputed.size());
        assertEquals(Set.copyOf(keys), valueComputed.keySet());
        assertEquals(Set.copyOf(keys), Set.copyOf(valueComputed.values()));

        // then
        final Map<String, String> cached = cache.get(keys);
        assertEquals(valueComputed, cached);
    }

    @Test
    void computeIfAbsentMultiAllWhenCachePartly() {
        // given
        List<String> keys = List.of("1");
        for (String key : keys) {
            assertNull(cache.get(key));
            cache.put(key, key);
        }

        // when
        final Map<String, String> valueComputed = cache.computeIfAbsent(Set.of("1", "2"), keysCompute -> {
            if ("2".equals(keysCompute.iterator().next())) {
                return Map.of("2", "2");
            }

            throw new IllegalStateException("Should not happen");
        });
        assertEquals(2, valueComputed.size());
        assertEquals(Set.of("1", "2"), valueComputed.keySet());
        assertEquals(Set.of("1", "2"), Set.copyOf(valueComputed.values()));

        // then
        final Map<String, String> cached = cache.get(Set.of("1", "2"));
        assertEquals(valueComputed, cached);
    }

    @Test
    void computeIfAbsentMultiWhenCacheFilled() {
        // given
        List<String> keys = List.of("1", "2");
        for (String key : keys) {
            assertNull(cache.get(key));
            cache.put(key, key);
        }

        // when
        final Map<String, String> valueComputed = cache.computeIfAbsent(keys, keysCompute -> {
            throw new IllegalStateException("Should not happen");
        });
        assertEquals(2, valueComputed.size());
        assertEquals(Set.copyOf(keys), valueComputed.keySet());
        assertEquals(Set.copyOf(keys), Set.copyOf(valueComputed.values()));

        // then
        final Map<String, String> cached = cache.get(keys);
        assertEquals(valueComputed, cached);
    }

    @Test
    void getWrongKeyWhenCacheFilled() {
        // given
        var key = "1";
        var value = "1";

        // when
        cache.put(key, value);

        // then
        final String fromCache = cache.get("2");
        assertNull(fromCache);
    }

    @Test
    void getWhenCacheInvalidate() {
        // given
        var key = "1";
        var value = "1";
        cache.put(key, value);

        // when
        cache.invalidate(key);

        // then
        final String fromCache = cache.get(key);
        assertNull(fromCache);
    }

    @Test
    void getFromCacheWhenCacheInvalidateAll() {
        // given
        var key = "1";
        var value = "1";
        cache.put(key, value);

        // when
        cache.invalidateAll();

        // then
        final String fromCache = cache.get(key);
        assertNull(fromCache);
    }
}
