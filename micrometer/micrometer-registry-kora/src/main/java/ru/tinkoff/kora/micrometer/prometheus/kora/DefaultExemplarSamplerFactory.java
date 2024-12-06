package ru.tinkoff.kora.micrometer.prometheus.kora;

import io.prometheus.metrics.config.ExemplarsProperties;
import io.prometheus.metrics.core.exemplars.ExemplarSampler;
import io.prometheus.metrics.core.exemplars.ExemplarSamplerConfig;
import io.prometheus.metrics.tracer.common.SpanContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default implementation of {@link ExemplarSamplerFactory}.
 * <p>
 * Credits to Jonatan Ivanov
 */
class DefaultExemplarSamplerFactory implements ExemplarSamplerFactory {

    private final ExemplarsProperties exemplarsProperties;

    private final ConcurrentMap<Integer, ExemplarSamplerConfig> exemplarSamplerConfigsByNumberOfExemplars = new ConcurrentHashMap<>();

    private final ConcurrentMap<double[], ExemplarSamplerConfig> exemplarSamplerConfigsByHistogramUpperBounds = new ConcurrentHashMap<>();

    private final SpanContext spanContext;

    DefaultExemplarSamplerFactory(SpanContext spanContext, ExemplarsProperties exemplarsProperties) {
        this.spanContext = spanContext;
        this.exemplarsProperties = exemplarsProperties;
    }

    @Override
    public ExemplarSampler createExemplarSampler(int numberOfExemplars) {
        ExemplarSamplerConfig config = exemplarSamplerConfigsByNumberOfExemplars.computeIfAbsent(numberOfExemplars,
            key -> new ExemplarSamplerConfig(exemplarsProperties, numberOfExemplars));
        return new ExemplarSampler(config, spanContext);
    }

    @Override
    public ExemplarSampler createExemplarSampler(double[] histogramUpperBounds) {
        ExemplarSamplerConfig config = exemplarSamplerConfigsByHistogramUpperBounds.computeIfAbsent(
            histogramUpperBounds, key -> new ExemplarSamplerConfig(exemplarsProperties, histogramUpperBounds));
        return new ExemplarSampler(config, spanContext);
    }

}
