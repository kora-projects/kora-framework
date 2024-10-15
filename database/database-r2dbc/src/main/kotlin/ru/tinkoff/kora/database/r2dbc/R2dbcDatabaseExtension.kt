package ru.tinkoff.kora.database.r2dbc

import io.r2dbc.spi.Connection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import reactor.core.publisher.Mono
import ru.tinkoff.kora.common.Context
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

suspend inline fun <T> R2dbcConnectionFactory.withConnectionSuspend(context: CoroutineContext? = null, noinline callback: suspend (Connection) -> T): T {
    val ctx = context ?: coroutineContext
    val future = withConnection {
        Mono.fromFuture(CoroutineScope(ctx).future {
            callback.invoke(it)
        })
    }
    return future.toFuture().await()
}

suspend inline fun <T> R2dbcConnectionFactory.inTxSuspend(context: CoroutineContext? = null, noinline callback: suspend (Connection) -> T): T {
    val ctx = context ?: coroutineContext
    val future = inTx {
        Mono.fromFuture(CoroutineScope(ctx).future(ctx) {
            callback.invoke(it)
        })
    }
    return future.toFuture().await()
}
