package ru.tinkoff.kora.micrometer.module.kafka.producer;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import jakarta.annotation.Nullable;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerMetrics;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @see <a href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/messaging/messaging-metrics.md">messaging-metrics</a>
 */
public class Opentelemetry120KafkaProducerMetrics implements KafkaProducerMetrics, AutoCloseable {
    private final KafkaClientMetrics micrometerMetrics;
    private final Properties properties;
    private final TelemetryConfig.MetricsConfig config;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<TopicPartition, DistributionSummary> metrics = new ConcurrentHashMap<>();

    public Opentelemetry120KafkaProducerMetrics(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config, Producer<?, ?> producer, Properties properties) {
        this.micrometerMetrics = new KafkaClientMetrics(producer);
        this.micrometerMetrics.bindTo(meterRegistry);
        this.properties = properties;
        this.config = config;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public KafkaProducerTxMetrics tx() {
        return new KafkaProducerTxMetrics() {
            @Override
            public void commit() {

            }

            @Override
            public void rollback(@Nullable Throwable e) {

            }
        };
    }

    @Override
    public void sendEnd(ProducerRecord<?, ?> record, long durationNanos, Throwable e) {
        var m = this.metrics.computeIfAbsent(new TopicPartition(record.topic(), Objects.requireNonNullElse(record.partition(), -1)), this::metrics);
        m.record((double) durationNanos / 1_000_000);
    }

    @Override
    public void sendEnd(ProducerRecord<?, ?> record, long durationNanos, RecordMetadata metadata) {
        var m = this.metrics.computeIfAbsent(new TopicPartition(metadata.topic(), metadata.partition()), this::metrics);
        m.record((double) durationNanos / 1_000_000);
    }

    @Override
    public void close() {
        this.micrometerMetrics.close();
        for (var i = this.metrics.entrySet().iterator(); i.hasNext(); ) {
            var entry = i.next();
            i.remove();
            try {
                entry.getValue().close();
            } catch (Throwable ignore) {
            }
        }
    }

    private DistributionSummary metrics(TopicPartition topicPartition) {
        var clientId = this.properties.get(ProducerConfig.CLIENT_ID_CONFIG);
        var partitionString = Integer.toString(topicPartition.partition());

        @SuppressWarnings("deprecation")
        var builder = DistributionSummary.builder("messaging.publish.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V120))
            .baseUnit("milliseconds")
            .tag(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemValues.KAFKA)
            .tag(MessagingIncubatingAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION.getKey(), partitionString)
            .tag(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), partitionString)
            .tag(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), topicPartition.topic())
            .tag(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), Objects.requireNonNullElse(clientId, "").toString());

        return builder.register(this.meterRegistry);
    }
}
