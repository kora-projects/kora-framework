package ru.tinkoff.kora.micrometer.prometheus.kora;

import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Counter;
import io.prometheus.client.exemplars.CounterExemplarSampler;
import io.prometheus.client.exemplars.Exemplar;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * {@link Counter} for Prometheus.
 * <p>
 * Credits to Jon Schneider
 * Credits to Jonatan Ivanov
 */
class KoraCounter extends AbstractMeter implements Counter {

    private final DoubleAdder count = new DoubleAdder();

    private final AtomicReference<Exemplar> exemplar = new AtomicReference<>();

    @Nullable
    private final CounterExemplarSampler exemplarSampler;

    KoraCounter(Id id) {
        this(id, null);
    }

    KoraCounter(Id id, @Nullable CounterExemplarSampler exemplarSampler) {
        super(id);
        this.exemplarSampler = exemplarSampler;
    }

    @Override
    public void increment(double amount) {
        if (amount > 0) {
            count.add(amount);
            if (exemplarSampler != null) {
                updateExemplar(amount, exemplarSampler);
            }
        }
    }

    @Override
    public double count() {
        return count.doubleValue();
    }

    @Nullable
    Exemplar exemplar() {
        return exemplar.get();
    }

    // Similar to exemplar.updateAndGet(...) but it does nothing if the next value is null
    private void updateExemplar(double amount, @NonNull CounterExemplarSampler exemplarSampler) {
        Exemplar prev;
        Exemplar next;
        do {
            prev = exemplar.get();
            next = exemplarSampler.sample(amount, prev);
        }
        while (next != null && next != prev && !exemplar.compareAndSet(prev, next));
    }

}
