package ru.tinkoff.kora.opentelemetry.module.s3.client;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.incubating.AwsIncubatingAttributes;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;
import ru.tinkoff.kora.s3.client.telemetry.S3KoraClientTracer;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

public final class OpentelemetryS3KoraClientTracer implements S3KoraClientTracer {

    private static final AttributeKey<String> CLIENT_NAME = stringKey("client.name");
    private static final AttributeKey<String> ERROR_CODE = stringKey("aws.error.code");
    private static final AttributeKey<String> OPERATION_NAME = stringKey("aws.operation.name");

    private final Class<?> client;
    private final Tracer tracer;

    public OpentelemetryS3KoraClientTracer(Class<?> client, Tracer tracer) {
        this.client = client;
        this.tracer = tracer;
    }

    @Override
    public S3KoraClientSpan createSpan(String operation,
                                       String bucket,
                                       @Nullable String key,
                                       @Nullable Long contentLength) {
        var ctx = ru.tinkoff.kora.common.Context.current();
        var tctx = OpentelemetryContext.get(ctx);

        var span = this.tracer.spanBuilder("S3 " + client + " " + operation)
            .setSpanKind(SpanKind.CLIENT)
            .setParent(tctx.getContext())
            .startSpan();

        span.setAttribute(CLIENT_NAME, client.getSimpleName());
        span.setAttribute(OPERATION_NAME, operation);
        span.setAttribute(AwsIncubatingAttributes.AWS_S3_BUCKET, bucket);
        if (key != null) {
            span.setAttribute(AwsIncubatingAttributes.AWS_S3_KEY, key);
        }
        if (contentLength != null) {
            span.setAttribute(HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE, contentLength);
        }

        return exception -> {
            if (exception != null) {
                span.setAttribute(ERROR_CODE.getKey(), exception.getErrorCode());
                span.setStatus(StatusCode.ERROR);
                span.recordException(exception);
            }
            span.end();
        };
    }
}
