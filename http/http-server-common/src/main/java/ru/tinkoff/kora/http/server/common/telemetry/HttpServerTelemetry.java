package ru.tinkoff.kora.http.server.common.telemetry;

import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.server.common.router.PublicApiRequest;

import javax.annotation.Nullable;

public interface HttpServerTelemetry {

    interface HttpServerTelemetryContext {
        void close(int statusCode, HttpResultCode resultCode, @Nullable HttpHeaders headers, @Nullable Throwable exception);
    }

    HttpServerTelemetryContext get(PublicApiRequest request, @Nullable String routeTemplate);
}
