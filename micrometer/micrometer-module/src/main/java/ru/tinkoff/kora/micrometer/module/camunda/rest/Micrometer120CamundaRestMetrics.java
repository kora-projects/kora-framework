package ru.tinkoff.kora.micrometer.module.camunda.rest;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestMetrics;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class Micrometer120CamundaRestMetrics implements CamundaRestMetrics {

    private record ActiveRequestsKey(String method, String target, String host, String scheme) {}

    private record DurationKey(int statusCode, String method, String route, String host, String scheme, @Nullable Class<? extends Throwable> errorType) {}

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<ActiveRequestsKey, AtomicInteger> requestCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DurationKey, DistributionSummary> duration = new ConcurrentHashMap<>();
    private final TelemetryConfig.MetricsConfig config;

    public Micrometer120CamundaRestMetrics(MeterRegistry meterRegistry, @Nullable TelemetryConfig.MetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public void requestStarted(String method, String pathTemplate, String host, String scheme) {
        var counter = requestCounters.computeIfAbsent(new ActiveRequestsKey(method, pathTemplate, host, scheme), activeRequestsKey -> {
            var c = new AtomicInteger(0);
            this.registerActiveRequestsGauge(activeRequestsKey, c);
            return c;
        });

        counter.incrementAndGet();
    }

    @Override
    public void requestFinished(int statusCode, HttpResultCode resultCode, String scheme, String host, String method, String pathTemplate, HttpHeaders headers, long processingTimeNanos, Throwable exception) {
        var counter = requestCounters.computeIfAbsent(new ActiveRequestsKey(method, pathTemplate, host, scheme), activeRequestsKey -> {
            var c = new AtomicInteger(0);
            this.registerActiveRequestsGauge(activeRequestsKey, c);
            return c;
        });

        counter.decrementAndGet();
        var errorType = exception != null ? exception.getClass() : null;
        var key = new DurationKey(statusCode, method, pathTemplate, host, scheme, errorType);
        this.duration.computeIfAbsent(key, this::requestDuration).record(((double) processingTimeNanos) / 1_000_000);
    }

    @SuppressWarnings("deprecation")
    private void registerActiveRequestsGauge(ActiveRequestsKey key, AtomicInteger counter) {
        Gauge.builder("camunda.rest.server.active_requests", counter, AtomicInteger::get)
            .tags(HttpAttributes.HTTP_ROUTE.getKey(), key.target())
            .tags(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), key.method())
            .tags(ServerAttributes.SERVER_ADDRESS.getKey(), key.host())
            .tags(UrlAttributes.URL_SCHEME.getKey(), key.scheme())
            .tags(HttpIncubatingAttributes.HTTP_TARGET.getKey(), key.target())
            .tags(HttpIncubatingAttributes.HTTP_METHOD.getKey(), key.method())
            .register(this.meterRegistry);
    }

    @SuppressWarnings("deprecation")
    private DistributionSummary requestDuration(DurationKey key) {
        return DistributionSummary.builder("camunda.rest.server.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V120))
            .baseUnit("milliseconds")
            .tags(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), key.method())
            .tags(HttpAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), Integer.toString(key.statusCode()))
            .tags(HttpAttributes.HTTP_ROUTE.getKey(), key.route())
            .tags(ServerAttributes.SERVER_ADDRESS.getKey(), key.host())
            .tags(UrlAttributes.URL_SCHEME.getKey(), key.scheme())
            .tags(HttpIncubatingAttributes.HTTP_TARGET.getKey(), key.route())
            .tags(HttpIncubatingAttributes.HTTP_METHOD.getKey(), key.method())
            .tags(HttpIncubatingAttributes.HTTP_STATUS_CODE.getKey(), Integer.toString(key.statusCode()))
            .register(this.meterRegistry);
    }
}
