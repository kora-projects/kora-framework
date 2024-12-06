package ru.tinkoff.kora.micrometer.prometheus.kora;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Counter;
import io.prometheus.metrics.core.exemplars.ExemplarSampler;
import io.prometheus.metrics.model.snapshots.Exemplar;

import java.util.concurrent.atomic.DoubleAdder;

/**
 * {@link Counter} for Prometheus.
 * <p>
 * Credits to Jon Schneider
 * Credits to Jonatan Ivanov
 */
public class PrometheusCounter extends AbstractMeter implements Counter {

    private final DoubleAdder count = new DoubleAdder();

    @Nullable
    private final ExemplarSampler exemplarSampler;

    PrometheusCounter(Id id) {
        this(id, null);
    }

    PrometheusCounter(Id id, @Nullable ExemplarSamplerFactory exemplarSamplerFactory) {
        super(id);
        this.exemplarSampler = exemplarSamplerFactory != null ? exemplarSamplerFactory.createExemplarSampler(1) : null;
    }

    @Override
    public void increment(double amount) {
        if (amount > 0) {
            count.add(amount);
            if (exemplarSampler != null) {
                exemplarSampler.observe(amount);
            }
        }
    }

    @Override
    public double count() {
        return count.doubleValue();
    }

    @Nullable
    Exemplar exemplar() {
        return exemplarSampler != null ? exemplarSampler.collect().getLatest() : null;
    }

}
