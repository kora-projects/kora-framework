package ru.tinkoff.kora.micrometer.module.http.client;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientMetrics;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.concurrent.ConcurrentHashMap;

public final class Opentelemetry123HttpClientMetrics implements HttpClientMetrics {
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<DurationKey, DistributionSummary> duration = new ConcurrentHashMap<>();
    private final TelemetryConfig.MetricsConfig config;

    public Opentelemetry123HttpClientMetrics(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public void record(@Nullable Integer statusCode, HttpResultCode resultCode, String scheme, String host, String method, String pathTemplate, HttpHeaders headers, long processingTimeNanos, Throwable throwable) {
        int code = statusCode == null ? -1 : statusCode;
        this.duration.computeIfAbsent(new DurationKey(code, method, host, scheme, pathTemplate), this::duration)
            .record((double) processingTimeNanos / 1_000_000_000);
    }

    private DistributionSummary duration(DurationKey key) {
        var builder = DistributionSummary.builder("http.client.request.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V123))
            .baseUnit("s")
            .tag(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), key.method)
            .tag(HttpAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), Integer.toString(key.statusCode()))
            .tag(ServerAttributes.SERVER_ADDRESS.getKey(), key.host)
            .tag(UrlAttributes.URL_SCHEME.getKey(), key.scheme)
            .tag(HttpAttributes.HTTP_ROUTE.getKey(), key.target)
            .tag("http.status_code", Integer.toString(key.statusCode()));

        return builder.register(meterRegistry);
    }

    private record DurationKey(int statusCode, String method, String host, String scheme, String target) {}
}
