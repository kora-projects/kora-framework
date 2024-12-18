package ru.tinkoff.kora.micrometer.prometheus.kora;

import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.prometheus.HistogramFlavor;
import io.prometheus.client.exemplars.CounterExemplarSampler;
import io.prometheus.client.exemplars.Exemplar;
import io.prometheus.client.exemplars.ExemplarSampler;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * {@link DistributionSummary} for Prometheus.
 *
 * Credits to Jon Schneider
 * Credits to Jonatan Ivanov
 */
public class PrometheusDistributionSummary extends AbstractDistributionSummary {

    private static final CountAtBucket[] EMPTY_HISTOGRAM = new CountAtBucket[0];

    private final LongAdder count = new LongAdder();

    private final DoubleAdder amount = new DoubleAdder();

    private final TimeWindowMax max;

    private final HistogramFlavor histogramFlavor;

    @Nullable
    private final Histogram histogram;

    @Nullable
    private final ExemplarSampler exemplarSampler;

    @Nullable
    private final AtomicReference<Exemplar> lastExemplar;

    private boolean histogramExemplarsEnabled = false;

    PrometheusDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
                                  double scale, HistogramFlavor histogramFlavor, @Nullable ExemplarSampler exemplarSampler) {
        super(id, clock,
                DistributionStatisticConfig.builder()
                    .percentilesHistogram(false)
                    .serviceLevelObjectives()
                    .build()
                    .merge(distributionStatisticConfig),
                scale, false);

        this.histogramFlavor = histogramFlavor;
        this.max = new TimeWindowMax(clock, distributionStatisticConfig);

        if (distributionStatisticConfig.isPublishingHistogram()) {
            switch (histogramFlavor) {
                case Prometheus:
                    PrometheusDoubleHistogram prometheusHistogram = new PrometheusDoubleHistogram(clock, distributionStatisticConfig, exemplarSampler);
                    this.histogram = prometheusHistogram;
                    this.histogramExemplarsEnabled = prometheusHistogram.isExemplarsEnabled();
                    break;
                case VictoriaMetrics:
                    this.histogram = new FixedBoundaryVictoriaMetricsHistogram();
                    break;
                default:
                    this.histogram = null;
                    break;
            }
        }
        else {
            this.histogram = null;
        }

        if (!this.histogramExemplarsEnabled && exemplarSampler != null) {
            this.exemplarSampler = exemplarSampler;
            this.lastExemplar = new AtomicReference<>();
        }
        else {
            this.exemplarSampler = null;
            this.lastExemplar = null;
        }
    }

    @Override
    protected void recordNonNegative(double amount) {
        count.increment();
        this.amount.add(amount);
        max.record(amount);

        if (histogram != null) {
            histogram.recordDouble(amount);
        }
        if (!histogramExemplarsEnabled && exemplarSampler != null) {
            updateLastExemplar(amount, exemplarSampler);
        }
    }

    // Similar to exemplar.updateAndGet(...) but it does nothing if the next value is null
    private void updateLastExemplar(double amount, @NonNull CounterExemplarSampler exemplarSampler) {
        Exemplar prev;
        Exemplar next;
        do {
            prev = lastExemplar.get();
            next = exemplarSampler.sample(amount, prev);
        }
        while (next != null && next != prev && !lastExemplar.compareAndSet(prev, next));
    }

    @Nullable
    Exemplar[] histogramExemplars() {
        if (histogramExemplarsEnabled) {
            return ((PrometheusDoubleHistogram) histogram).exemplars();
        }
        else {
            return null;
        }
    }

    @Nullable
    Exemplar lastExemplar() {
        if (histogramExemplarsEnabled) {
            return ((PrometheusDoubleHistogram) histogram).lastExemplar();
        }
        else {
            return lastExemplar != null ? lastExemplar.get() : null;
        }
    }

    @Override
    public long count() {
        return count.longValue();
    }

    @Override
    public double totalAmount() {
        return amount.doubleValue();
    }

    @Override
    public double max() {
        return max.poll();
    }

    public HistogramFlavor histogramFlavor() {
        return histogramFlavor;
    }

    /**
     * For Prometheus we cannot use the histogram counts from HistogramSnapshot, as it is
     * based on a rolling histogram. Prometheus requires a histogram that accumulates
     * values over the lifetime of the app.
     * @return Cumulative histogram buckets.
     */
    public CountAtBucket[] histogramCounts() {
        return histogram == null ? EMPTY_HISTOGRAM : histogram.takeSnapshot(0, 0, 0).histogramCounts();
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        HistogramSnapshot snapshot = super.takeSnapshot();

        if (histogram == null) {
            return snapshot;
        }

        return new HistogramSnapshot(snapshot.count(), snapshot.total(), snapshot.max(), snapshot.percentileValues(),
                histogramCounts(), snapshot::outputSummary);
    }

}
