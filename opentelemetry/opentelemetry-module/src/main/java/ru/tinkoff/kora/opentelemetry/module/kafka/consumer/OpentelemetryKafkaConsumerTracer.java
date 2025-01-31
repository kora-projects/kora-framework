package ru.tinkoff.kora.opentelemetry.module.kafka.consumer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTracer;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

public final class OpentelemetryKafkaConsumerTracer implements KafkaConsumerTracer {
    private final Tracer tracer;

    public OpentelemetryKafkaConsumerTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public KafkaConsumerRecordsSpan get(ConsumerRecords<?, ?> records) {
        var ctx = Context.current();
        var otctx = OpentelemetryContext.get(ctx);
        var partitions = records.partitions();
        var spans = new HashMap<TopicPartition, Span>(partitions.size());
        var rootSpan = this.tracer.spanBuilder("kafka.poll")
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, MessagingIncubatingAttributes.MessagingSystemValues.KAFKA)
            .setNoParent()
            .startSpan();
        var rootCtx = otctx.add(rootSpan);
        for (var topicPartition : partitions) {
            @SuppressWarnings("deprecation")
            var partitionSpan = this.tracer
                .spanBuilder(topicPartition.topic() + " receive")
                .setParent(rootCtx.getContext())
                .setSpanKind(SpanKind.CONSUMER)
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "kafka")
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION, MessagingIncubatingAttributes.MessagingOperationValues.RECEIVE)
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, topicPartition.topic())
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION, (long) topicPartition.partition())
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID, String.valueOf(topicPartition.partition()))
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT, (long) records.count())
                .startSpan();
            spans.put(topicPartition, partitionSpan);
        }
        OpentelemetryContext.set(ctx, rootCtx);

        return new OpentelemetryKafkaConsumerRecordsSpan(this.tracer, rootCtx, rootSpan, spans);
    }

    private static final class OpentelemetryKafkaConsumerRecordsSpan implements KafkaConsumerRecordsSpan {
        private final Tracer tracer;
        private final OpentelemetryContext rootCtx;
        private final Span rootSpan;
        private final Map<TopicPartition, Span> spans;

        public OpentelemetryKafkaConsumerRecordsSpan(Tracer tracer, OpentelemetryContext rootCtx, Span rootSpan, Map<TopicPartition, Span> spans) {
            this.tracer = tracer;
            this.rootCtx = rootCtx;
            this.rootSpan = rootSpan;
            this.spans = spans;
        }

        @Override
        public KafkaConsumerRecordSpan get(ConsumerRecord<?, ?> record) {
            var partitionSpan = this.spans.get(new TopicPartition(record.topic(), record.partition()));
            var root = io.opentelemetry.context.Context.root();
            var parent = W3CTraceContextPropagator.getInstance().extract(root, record, ConsumerRecordTextMapGetter.INSTANCE);

            @SuppressWarnings("deprecation")
            var recordSpanBuilder = this.tracer
                .spanBuilder(record.topic() + " process")
                .setSpanKind(SpanKind.CONSUMER)
                .setParent(parent)
                .addLink(partitionSpan.getSpanContext())
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "kafka")
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, record.topic())
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION, (long) record.partition())
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID, String.valueOf(record.partition()))
                .setAttribute(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET, record.offset());
            try {
                recordSpanBuilder.setAttribute(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY, Objects.toString(record.key()));
            } catch (Exception ignore) {}
            var recordSpan = recordSpanBuilder.startSpan();
            OpentelemetryContext.set(Context.current(), this.rootCtx.add(recordSpan));

            return new OpentelemetryKafkaConsumerRecordSpan(this.rootCtx, recordSpan);
        }

        @Override
        public void close(@Nullable Throwable ex) {
            for (var span : this.spans.values()) {
                if(ex != null) {
                    span.setStatus(StatusCode.ERROR);
                    span.recordException(ex);
                }
                span.end();
            }
            this.rootSpan.end();
        }

        private enum ConsumerRecordTextMapGetter implements TextMapGetter<ConsumerRecord<?, ?>> {
            INSTANCE;

            @Override
            public Iterable<String> keys(ConsumerRecord<?, ?> carrier) {
                var set = new HashSet<String>();
                for (var header : carrier.headers()) {
                    set.add(header.key());
                }
                return set;

            }

            @Nullable
            @Override
            public String get(ConsumerRecord<?, ?> carrier, String key) {
                var header = carrier.headers().lastHeader(key);
                return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
            }
        }
    }

    private static final class OpentelemetryKafkaConsumerRecordSpan implements KafkaConsumerRecordSpan {
        private final OpentelemetryContext rootCtx;
        private final Span recordSpan;

        public OpentelemetryKafkaConsumerRecordSpan(OpentelemetryContext rootCtx, Span recordSpan) {
            this.rootCtx = rootCtx;
            this.recordSpan = recordSpan;
        }

        @Override
        public void close(@Nullable Throwable ex) {
            if(ex != null) {
                this.recordSpan.setStatus(StatusCode.ERROR);
                this.recordSpan.recordException(ex);
            }
            this.recordSpan.end();
            OpentelemetryContext.set(Context.current(), this.rootCtx);
        }
    }
}
