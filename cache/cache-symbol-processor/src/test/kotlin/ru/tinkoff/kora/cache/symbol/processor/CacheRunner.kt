package ru.tinkoff.kora.cache.symbol.processor

import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheConfig
import ru.tinkoff.kora.cache.redis.RedisCacheConfig
import ru.tinkoff.kora.cache.redis.RedisCacheClient
import java.nio.ByteBuffer
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class CacheRunner {

    companion object {

        fun getCaffeineConfig(): CaffeineCacheConfig {
            return object : CaffeineCacheConfig {
                override fun expireAfterWrite(): Duration? {
                    return null;
                }

                override fun expireAfterAccess(): Duration? {
                    return null;
                }

                override fun initialSize(): Int? {
                    return null;
                }
            }
        }

        fun getRedisConfig(): RedisCacheConfig {
            return object : RedisCacheConfig {

                override fun keyPrefix(): String = "pref"

                override fun expireAfterWrite(): Duration? = null

                override fun expireAfterAccess(): Duration? = null
            }
        }

        fun lettuceClient(cache: MutableMap<ByteBuffer?, ByteBuffer?>): RedisCacheClient {
            return object : RedisCacheClient {
                override fun get(key: ByteArray): CompletionStage<ByteArray?> {
                    val r = cache[ByteBuffer.wrap(key)]
                    return CompletableFuture.completedFuture(r?.array())
                }

                override fun mget(keys: Array<ByteArray>): CompletionStage<Map<ByteArray, ByteArray>> {
                    val result: MutableMap<ByteArray, ByteArray> = HashMap()
                    for (key in keys) {
                        Optional.ofNullable(cache[ByteBuffer.wrap(key)]).ifPresent { r: ByteBuffer ->
                            result[key] = r.array()
                        }
                    }
                    return CompletableFuture.completedFuture(result)
                }

                override fun getex(key: ByteArray, expireAfterMillis: Long): CompletionStage<ByteArray?> {
                    return get(key)
                }

                override fun getex(keys: Array<ByteArray>, expireAfterMillis: Long): CompletionStage<Map<ByteArray, ByteArray>> {
                    return mget(keys)
                }

                override fun set(key: ByteArray, value: ByteArray) : CompletionStage<Boolean> {
                    cache[ByteBuffer.wrap(key)] = ByteBuffer.wrap(value)
                    return CompletableFuture.completedFuture(true)
                }

                override fun mset(keyAndValue: MutableMap<ByteArray, ByteArray>) : CompletionStage<Boolean> {
                    keyAndValue.forEach { (k, v) -> set(k, v) }
                    return CompletableFuture.completedFuture(true)
                }

                override fun psetex(keyAndValue: MutableMap<ByteArray, ByteArray>, expireAfterMillis: Long): CompletionStage<Boolean> {
                    mset(keyAndValue)
                    return CompletableFuture.completedFuture(true)
                }

                override fun psetex(key: ByteArray, value: ByteArray, expireAfterMillis: Long): CompletionStage<Boolean> {
                    return set(key, value)
                }

                override fun del(key: ByteArray): CompletionStage<Long> {
                    val res = if (cache.remove(ByteBuffer.wrap(key)) == null) 0L else 1L
                    return CompletableFuture.completedFuture(res)
                }

                override fun del(keys: Array<ByteArray>): CompletionStage<Long> {
                    var counter = 0
                    for (key in keys) {
                        counter += runBlocking { del(key).await().toInt() }
                    }
                    return CompletableFuture.completedFuture(counter.toLong())
                }

                override fun flushAll() : CompletionStage<Boolean> {
                    cache.clear()
                    return CompletableFuture.completedFuture(true)
                }
            }
        }
    }
}
