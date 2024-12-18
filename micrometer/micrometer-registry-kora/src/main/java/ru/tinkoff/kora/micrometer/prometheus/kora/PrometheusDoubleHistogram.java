package ru.tinkoff.kora.micrometer.prometheus.kora;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.Histogram;
import io.micrometer.core.instrument.util.TimeUtils;
import io.prometheus.client.exemplars.Exemplar;
import io.prometheus.client.exemplars.HistogramExemplarSampler;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Internal {@link Histogram} implementation for Prometheus that handles {@link Exemplar
 * exemplars}.
 * <p>
 * Credits to Jonatan Ivanov
 */
class PrometheusDoubleHistogram extends TimeWindowFixedBoundaryDoubleHistogram {

    @Nullable
    private final double[] buckets;

    @Nullable
    private final AtomicReferenceArray<Exemplar> exemplars;

    @Nullable
    private final AtomicReference<Exemplar> lastExemplar;

    @Nullable
    private final HistogramExemplarSampler exemplarSampler;

    PrometheusDoubleHistogram(Clock clock, DistributionStatisticConfig config, @Nullable HistogramExemplarSampler exemplarSampler) {
        super(clock, DistributionStatisticConfig.builder()
            // effectively never rolls over
            .expiry(Duration.ofDays(1825))
            .bufferLength(1)
            .build()
            .merge(config), true);

        this.exemplarSampler = exemplarSampler;
        if (isExemplarsEnabled()) {
            double[] originalBuckets = getBuckets();
            if (originalBuckets[originalBuckets.length - 1] != Double.POSITIVE_INFINITY) {
                this.buckets = Arrays.copyOf(originalBuckets, originalBuckets.length + 1);
                this.buckets[buckets.length - 1] = Double.POSITIVE_INFINITY;
            }
            else {
                this.buckets = originalBuckets;
            }
            this.exemplars = new AtomicReferenceArray<>(this.buckets.length);
            this.lastExemplar = new AtomicReference<>();
        }
        else {
            this.buckets = null;
            this.exemplars = null;
            this.lastExemplar = null;
        }
    }

    boolean isExemplarsEnabled() {
        return exemplarSampler != null;
    }

    @Override
    public void recordDouble(double value) {
        super.recordDouble(value);
        if (isExemplarsEnabled()) {
            updateExemplar(value, null, null);
        }
    }

    @Override
    public void recordLong(long value) {
        super.recordLong(value);
        if (isExemplarsEnabled()) {
            updateExemplar(value, NANOSECONDS, SECONDS);
        }
    }

    private void updateExemplar(double value, @Nullable TimeUnit sourceUnit, @Nullable TimeUnit destinationUnit) {
        int index = leastLessThanOrEqualTo(value);
        index = (index == -1) ? exemplars.length() - 1 : index;
        updateExemplar(value, sourceUnit, destinationUnit, index);
    }

    private void updateExemplar(double value, @Nullable TimeUnit sourceUnit, @Nullable TimeUnit destinationUnit,
            int index) {
        double bucketFrom = (index == 0) ? Double.NEGATIVE_INFINITY : buckets[index - 1];
        double bucketTo = buckets[index];
        Exemplar previusBucketExemplar;
        Exemplar previousLastExemplar;
        Exemplar nextExemplar;

        double exemplarValue = (sourceUnit != null && destinationUnit != null)
                ? TimeUtils.convert(value, sourceUnit, destinationUnit) : value;
        do {
            previusBucketExemplar = exemplars.get(index);
            previousLastExemplar = lastExemplar.get();
            nextExemplar = exemplarSampler.sample(exemplarValue, bucketFrom, bucketTo, previusBucketExemplar);
        }
        while (nextExemplar != null && nextExemplar != previusBucketExemplar
                && !(exemplars.compareAndSet(index, previusBucketExemplar, nextExemplar)
                        && lastExemplar.compareAndSet(previousLastExemplar, nextExemplar)));
    }

    @Nullable
    Exemplar[] exemplars() {
        if (isExemplarsEnabled()) {
            Exemplar[] exemplarsArray = new Exemplar[this.exemplars.length()];
            for (int i = 0; i < this.exemplars.length(); i++) {
                exemplarsArray[i] = this.exemplars.get(i);
            }

            return exemplarsArray;
        }
        else {
            return null;
        }
    }

    @Nullable
    Exemplar lastExemplar() {
        return this.lastExemplar.get();
    }

    /**
     * The least bucket that is less than or equal to a sample.
     */
    private int leastLessThanOrEqualTo(double key) {
        int low = 0;
        int high = buckets.length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (buckets[mid] < key)
                low = mid + 1;
            else if (buckets[mid] > key)
                high = mid - 1;
            else
                return mid; // exact match
        }

        return low < buckets.length ? low : -1;
    }

}
