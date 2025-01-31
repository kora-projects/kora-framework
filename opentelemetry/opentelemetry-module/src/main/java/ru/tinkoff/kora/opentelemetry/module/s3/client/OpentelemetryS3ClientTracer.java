package ru.tinkoff.kora.opentelemetry.module.s3.client;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.incubating.AwsIncubatingAttributes;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;
import ru.tinkoff.kora.s3.client.S3Exception;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTracer;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

public final class OpentelemetryS3ClientTracer implements S3ClientTracer {

    private static final AttributeKey<String> CLIENT_NAME = stringKey("client.name");
    private static final AttributeKey<String> ERROR_CODE = stringKey("aws.error.code");

    private final Class<?> client;
    private final Tracer tracer;

    public OpentelemetryS3ClientTracer(Class<?> client, Tracer tracer) {
        this.client = client;
        this.tracer = tracer;
    }

    @Override
    public S3ClientSpan createSpan() {
        var ctx = ru.tinkoff.kora.common.Context.current();
        var tctx = OpentelemetryContext.get(ctx);

        var span = this.tracer.spanBuilder("S3 " + client)
            .setSpanKind(SpanKind.CLIENT)
            .setParent(tctx.getContext())
            .startSpan();

        return new S3ClientSpan() {
            @Override
            public void prepared(String method, String bucket, @Nullable String key, @Nullable Long contentLength) {
                span.updateName(" S3 " + client + " " + method);
                span.setAttribute(CLIENT_NAME, client.getSimpleName());
                span.setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, method);
                span.setAttribute(AwsIncubatingAttributes.AWS_S3_BUCKET, bucket);
                if (key != null) {
                    span.setAttribute(AwsIncubatingAttributes.AWS_S3_KEY, key);
                }
                if (contentLength != null) {
                    span.setAttribute(HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE, contentLength);
                }
            }

            @Override
            public void close(int statusCode, @Nullable S3Exception exception) {
                span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, statusCode);
                if (exception != null) {
                    span.setAttribute(ERROR_CODE.getKey(), exception.getErrorCode());
                    span.setStatus(StatusCode.ERROR);
                    span.recordException(exception);
                }
                span.end();
            }
        };
    }
}
