package ru.tinkoff.kora.micrometer.module.kafka.producer;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import jakarta.annotation.Nullable;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerMetrics;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @see <a href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/messaging/messaging-metrics.md">messaging-metrics</a>
 */
public class Opentelemetry123KafkaProducerMetrics implements KafkaProducerMetrics, AutoCloseable {
    private final KafkaClientMetrics micrometerMetrics;
    private final Properties properties;
    private final TelemetryConfig.MetricsConfig config;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<DurationKey, DistributionSummary> metrics = new ConcurrentHashMap<>();

    public Opentelemetry123KafkaProducerMetrics(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config, Producer<?, ?> producer, Properties properties) {
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
        var key = new DurationKey(record.topic(), Objects.requireNonNullElse(record.partition(), -1), e.getClass());
        var m = this.metrics.computeIfAbsent(key, this::metrics);
        m.record((double) durationNanos / 1_000_000_000);
    }

    @Override
    public void sendEnd(ProducerRecord<?, ?> record, long durationNanos, RecordMetadata metadata) {
        var key = new DurationKey(record.topic(), Objects.requireNonNullElse(record.partition(), -1), null);
        var m = this.metrics.computeIfAbsent(key, this::metrics);
        m.record((double) durationNanos / 1_000_000_000);
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

    private record DurationKey(String topic, int partition, @Nullable Class<? extends Throwable> errorType) {}

    private DistributionSummary metrics(DurationKey key) {
        var clientId = this.properties.get(ProducerConfig.CLIENT_ID_CONFIG);
        var partitionString = Integer.toString(key.partition());

        @SuppressWarnings("deprecation")
        var builder = DistributionSummary.builder("messaging.publish.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V123))
            .baseUnit("s")
            .tag(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), "kafka")
            .tag(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), partitionString)
            .tag(MessagingIncubatingAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION.getKey(), partitionString)
            .tag(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), key.topic())
            .tag(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), Objects.requireNonNullElse(clientId, "").toString());

        var errorType = key.errorType();
        if (errorType != null) {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), errorType.getCanonicalName());
        } else {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), "");
        }

        return builder.register(this.meterRegistry);
    }
}
