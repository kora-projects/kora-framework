package ru.tinkoff.kora.opentelemetry.module.grpc.server;

import io.grpc.Status;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.incubating.MessageIncubatingAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.grpc.server.telemetry.GrpcServerTracer;

import java.util.concurrent.atomic.AtomicLong;

public final class OpentelemetryGrpcServerSpan implements GrpcServerTracer.GrpcServerSpan {
    private final Span span;
    private final AtomicLong sentCounter = new AtomicLong(0);
    private final AtomicLong receivedCounter = new AtomicLong(0);

    public OpentelemetryGrpcServerSpan(Span span) {
        this.span = span;
    }

    @Override
    public void close(@Nullable Status status, @Nullable Throwable exception) {
        if (exception != null) {
            this.span.recordException(exception);
            this.span.setStatus(StatusCode.ERROR);
        }
        this.span.end();
    }

    @Override
    public void addSend(Object message) {
        this.span.addEvent(
            "message",
            Attributes.of(
                MessageIncubatingAttributes.MESSAGE_TYPE, "SENT",
                MessageIncubatingAttributes.MESSAGE_ID, sentCounter.incrementAndGet()
            )
        );
    }

    @Override
    public void addReceive(Object message) {
        this.span.addEvent(
            "message",
            Attributes.of(
                MessageIncubatingAttributes.MESSAGE_TYPE, "RECEIVED",
                MessageIncubatingAttributes.MESSAGE_ID, receivedCounter.incrementAndGet()
            )
        );
    }
}
