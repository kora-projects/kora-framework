package ru.tinkoff.kora.micrometer.module.db;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import io.opentelemetry.semconv.incubating.PoolIncubatingAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DataBaseMetricWriter;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.concurrent.ConcurrentHashMap;

public final class Opentelemetry123DataBaseMetricWriter implements DataBaseMetricWriter {

    private final String poolName;
    private final ConcurrentHashMap<DbKey, DbMetrics> metrics = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final TelemetryConfig.MetricsConfig config;

    public Opentelemetry123DataBaseMetricWriter(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config, String poolName) {
        this.poolName = poolName;
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public void recordQuery(long queryBegin, QueryContext queryContext, @Nullable Throwable exception) {
        var duration = System.nanoTime() - queryBegin;
        var key = new DbKey(queryContext.queryId(), queryContext.operation(), exception == null ? null : exception.getClass());
        var metrics = this.metrics.computeIfAbsent(key, this::metrics);
        metrics.duration().record((double) duration / 1_000_000_000);
    }

    @Override
    public Object getMetricRegistry() {
        return this.meterRegistry;
    }

    private record DbMetrics(DistributionSummary duration) {}

    private record DbKey(String queryId, String operation, @Nullable Class<? extends Throwable> error) {}

    private DbMetrics metrics(DbKey key) {
        var builder = DistributionSummary.builder("db.client.request.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V123))
            .baseUnit("s")
            .tag(PoolIncubatingAttributes.POOL_NAME.getKey(), this.poolName)
            .tag(DbIncubatingAttributes.DB_STATEMENT.getKey(), key.queryId())
            .tag(DbIncubatingAttributes.DB_OPERATION.getKey(), key.operation());

        if (key.error != null) {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), key.error.getCanonicalName());
        } else {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), "");
        }

        return new DbMetrics(builder.register(this.meterRegistry));
    }
}
