package ru.tinkoff.kora.opentelemetry.module.kafka.consumer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import jakarta.annotation.Nullable;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerTracer;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

import java.nio.charset.StandardCharsets;

public class OpentelemetryKafkaProducerTracer implements KafkaProducerTracer {
    private final Tracer tracer;

    public OpentelemetryKafkaProducerTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public KafkaProducerRecordSpan get(ProducerRecord<?, ?> record) {
        var ctx = Context.current();
        var otctx = OpentelemetryContext.get(ctx);
        var span = this.tracer.spanBuilder(record.topic() + " send")
            .setSpanKind(SpanKind.PRODUCER)
            .setParent(otctx.getContext())
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "kafka")
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION, MessagingIncubatingAttributes.MessagingOperationValues.PUBLISH)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, record.topic())
            .startSpan();
        W3CTraceContextPropagator.getInstance().inject(otctx.getContext().with(span), record, ProducerRecordTextMapSetter.INSTANCE);

        return new OpentelemetryKafkaProducerRecordSpan(span);
    }

    @Override
    public KafkaProducerTxSpan tx() {
        var ctx = Context.current();
        var otctx = OpentelemetryContext.get(ctx);
        var span = this.tracer.spanBuilder("producer transaction")
            .setSpanKind(SpanKind.INTERNAL)
            .setParent(otctx.getContext())
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "kafka")
            .startSpan();
        OpentelemetryContext.set(ctx, otctx.add(span));
        return new OpentelemetryKafkaProducerTxSpan(ctx, otctx, span);
    }

    private static final class OpentelemetryKafkaProducerRecordSpan implements KafkaProducerRecordSpan {
        private final Span span;

        private OpentelemetryKafkaProducerRecordSpan(Span span) {
            this.span = span;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void close(RecordMetadata metadata) {
            span.setAttribute(MessagingIncubatingAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION, metadata.partition());
            span.setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID, String.valueOf(metadata.partition()));
            span.setAttribute(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET, metadata.offset());
            span.end();
        }

        @Override
        public void close(Throwable e) {
            span.setAttribute(ErrorAttributes.ERROR_TYPE, e.getClass().getName());
            span.setStatus(StatusCode.ERROR);
            span.recordException(e);
            span.end();
        }
    }

    private static final class OpentelemetryKafkaProducerTxSpan implements KafkaProducerTxSpan {
        private final Context context;
        private final OpentelemetryContext ctx;
        private final Span span;

        private OpentelemetryKafkaProducerTxSpan(Context context, OpentelemetryContext ctx, Span span) {
            this.context = context;
            this.ctx = ctx;
            this.span = span;
        }

        @Override
        public void commit() {
            span.end();
            OpentelemetryContext.set(this.context, this.ctx);
        }

        @Override
        public void rollback(Throwable e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            span.end();
            OpentelemetryContext.set(this.context, this.ctx);
        }
    }

    private enum ProducerRecordTextMapSetter implements TextMapSetter<ProducerRecord<?, ?>> {
        INSTANCE;

        @Override
        public void set(@Nullable ProducerRecord<?, ?> carrier, String key, String value) {
            carrier.headers().add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }

}
