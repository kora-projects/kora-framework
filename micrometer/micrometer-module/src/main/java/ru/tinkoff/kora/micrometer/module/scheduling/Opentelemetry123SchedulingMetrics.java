package ru.tinkoff.kora.micrometer.module.scheduling;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingMetrics;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Opentelemetry123SchedulingMetrics implements SchedulingMetrics {
    private final Map<Class<? extends Throwable>, DistributionSummary> errorDuration = new ConcurrentHashMap<>();
    private final DistributionSummary successDuration;
    private final MeterRegistry meterRegistry;
    private final TelemetryConfig.MetricsConfig config;
    private final String className;
    private final String methodName;

    public Opentelemetry123SchedulingMetrics(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config, String className, String methodName) {
        this.meterRegistry = meterRegistry;
        this.config = config;
        this.className = className;
        this.methodName = methodName;
        this.successDuration = duration(null);
    }

    private DistributionSummary duration(@Nullable Class<? extends Throwable> error) {
        var builder = DistributionSummary.builder("scheduling.job.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V123))
            .baseUnit("s")
            .tag(CodeIncubatingAttributes.CODE_FUNCTION.getKey(), this.methodName)
            .tag("code.class", this.className);

        if (error != null) {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), error.getCanonicalName());
        } else {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), "");
        }

        return builder.register(this.meterRegistry);
    }

    @Override
    public void record(long processingTimeNanos, @Nullable Throwable e) {
        var procesingTime = processingTimeNanos / 1_000_000_000d;
        if (e == null) {
            this.successDuration.record(procesingTime);
        } else {
            this.errorDuration.computeIfAbsent(e.getClass(), this::duration).record(procesingTime);
        }
    }
}
