package ru.tinkoff.kora.micrometer.module.s3.client;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.incubating.AwsIncubatingAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.s3.client.S3Exception;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientMetrics;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

public class Opentelemetry123S3ClientMetrics implements S3ClientMetrics {

    private static final AttributeKey<String> ERROR_CODE = stringKey("aws.error.code");
    private static final AttributeKey<String> CLIENT_NAME = stringKey("aws.client.name");

    private final ConcurrentHashMap<DurationKey, DistributionSummary> duration = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final TelemetryConfig.MetricsConfig config;
    private final Class<?> client;

    public Opentelemetry123S3ClientMetrics(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config, Class<?> client) {
        this.meterRegistry = meterRegistry;
        this.config = config;
        this.client = client;
    }

    @Override
    public void record(String method,
                       String bucket,
                       @Nullable String key,
                       int statusCode,
                       long processingTimeNanos,
                       @Nullable S3Exception exception) {
        String errorCode = (exception != null) ? exception.getErrorCode() : null;
        this.duration.computeIfAbsent(new DurationKey(method, bucket, statusCode, errorCode), this::duration)
            .record((double) processingTimeNanos / 1_000_000_000);
    }

    private DistributionSummary duration(DurationKey key) {
        var builder = DistributionSummary.builder("s3.client.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V123))
            .baseUnit("s")
            .tag(CLIENT_NAME.getKey(), client.getSimpleName())
            .tag(AwsIncubatingAttributes.AWS_S3_BUCKET.getKey(), key.bucket())
            .tag(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), key.method())
            .tag(HttpAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), Integer.toString(key.statusCode()))
            .tag(ERROR_CODE.getKey(), Objects.requireNonNullElse(key.errorCode(), ""));

        return builder.register(meterRegistry);
    }

    private record DurationKey(String method, String bucket, int statusCode, @Nullable String errorCode) {}
}
