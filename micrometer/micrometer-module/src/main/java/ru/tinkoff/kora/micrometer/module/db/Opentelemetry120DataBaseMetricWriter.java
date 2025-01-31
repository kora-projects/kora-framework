package ru.tinkoff.kora.micrometer.module.db;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DataBaseMetricWriter;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.concurrent.ConcurrentHashMap;

public final class Opentelemetry120DataBaseMetricWriter implements DataBaseMetricWriter {

    private final String poolName;
    private final ConcurrentHashMap<DbKey, DbMetrics> metrics = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final TelemetryConfig.MetricsConfig config;

    public Opentelemetry120DataBaseMetricWriter(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config, String poolName) {
        this.poolName = poolName;
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public void recordQuery(long queryBegin, QueryContext queryContext, @Nullable Throwable exception) {
        var duration = System.nanoTime() - queryBegin;
        var key = new DbKey(queryContext.queryId(), queryContext.operation(), exception == null ? null : exception.getClass());
        var metrics = this.metrics.computeIfAbsent(key, this::metrics);
        metrics.duration().record((double) duration / 1_000_000);
    }

    @Override
    public Object getMetricRegistry() {
        return this.meterRegistry;
    }

    private record DbMetrics(DistributionSummary duration) {}

    private record DbKey(String queryId, String operation, @Nullable Class<? extends Throwable> error) {}

    private DbMetrics metrics(DbKey key) {
        var builder = DistributionSummary.builder("database.client.request.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V120))
            .baseUnit("milliseconds")
            .tag("pool", this.poolName)
            .tag("query.id", key.queryId())
            .tag("query.operation", key.operation());

        if (key.error != null) {
            builder.tag("error", key.error.getCanonicalName());
        } else {
            builder.tag("error", "");
        }

        return new DbMetrics(builder.register(this.meterRegistry));
    }
}
