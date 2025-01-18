package ru.tinkoff.kora.micrometer.prometheus.kora;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.config.InvalidConfigurationException;
import io.micrometer.core.instrument.distribution.*;

import java.io.PrintStream;
import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * An abstract base class for histogram implementations who maintain samples in a ring
 * buffer to decay older samples and give greater weight to recent samples.
 * <p>
 * Credits to Jon Schneider
 * Credits to Trustin Heuiseung Lee
 *
 * @param <T> the type of the buckets in a ring buffer
 * @param <U> the type of accumulated histogram
 */
@SuppressWarnings("ConstantConditions")
abstract class AbstractTimeWindowHistogram<T, U> implements Histogram {

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<AbstractTimeWindowHistogram> rotatingUpdater = AtomicIntegerFieldUpdater
        .newUpdater(AbstractTimeWindowHistogram.class, "rotating");

    final DistributionStatisticConfig distributionStatisticConfig;

    private final Clock clock;

    private final boolean supportsAggregablePercentiles;

    private final T[] ringBuffer;

    private short currentBucket;

    private final long durationBetweenRotatesMillis;

    private volatile boolean accumulatedHistogramStale;

    private volatile long lastRotateTimestampMillis;

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private volatile int rotating; // 0 - not rotating, 1 - rotating

    @Nullable
    private U accumulatedHistogram;

    @SuppressWarnings("unchecked")
    AbstractTimeWindowHistogram(Clock clock, DistributionStatisticConfig distributionStatisticConfig,
                                Class<T> bucketType, boolean supportsAggregablePercentiles) {
        this.clock = clock;
        this.distributionStatisticConfig = validateDistributionConfig(distributionStatisticConfig);
        this.supportsAggregablePercentiles = supportsAggregablePercentiles;

        final int ageBuckets = distributionStatisticConfig.getBufferLength();

        ringBuffer = (T[]) Array.newInstance(bucketType, ageBuckets);

        durationBetweenRotatesMillis = distributionStatisticConfig.getExpiry().toMillis() / ageBuckets;
        if (durationBetweenRotatesMillis <= 0) {
            rejectHistogramConfig("expiry (" + distributionStatisticConfig.getExpiry().toMillis()
                                  + "ms) / bufferLength (" + ageBuckets + ") must be greater than 0.");
        }

        currentBucket = 0;
        lastRotateTimestampMillis = clock.wallTime();
    }

    private static DistributionStatisticConfig validateDistributionConfig(
        DistributionStatisticConfig distributionStatisticConfig) {
        if (distributionStatisticConfig.getPercentiles() != null
            && distributionStatisticConfig.getPercentilePrecision() == null) {
            rejectHistogramConfig("when publishing percentiles a precision must be specified.");
        }

        if (distributionStatisticConfig.getMinimumExpectedValueAsDouble() == null
            || distributionStatisticConfig.getMaximumExpectedValueAsDouble() == null) {
            rejectHistogramConfig("minimumExpectedValue and maximumExpectedValue must be non null");
        }
        return distributionStatisticConfig;
    }

    private static void rejectHistogramConfig(String msg) {
        throw new InvalidConfigurationException("Invalid distribution configuration: " + msg);
    }

    void initRingBuffer() {
        for (int i = 0; i < ringBuffer.length; i++) {
            ringBuffer[i] = newBucket();
        }
        accumulatedHistogram = newAccumulatedHistogram(ringBuffer);
    }

    abstract T newBucket();

    abstract void recordLong(T bucket, long value);

    abstract void recordDouble(T bucket, double value);

    abstract void resetBucket(T bucket);

    abstract U newAccumulatedHistogram(T[] ringBuffer);

    abstract void accumulate();

    abstract void resetAccumulatedHistogram();

    abstract double valueAtPercentile(double percentile);

    abstract Iterator<CountAtBucket> countsAtValues(Iterator<Double> values);

    void outputSummary(PrintStream out, double bucketScaling) {
    }

    @Override
    public final HistogramSnapshot takeSnapshot(long count, double total, double max) {
        rotate();

        final ValueAtPercentile[] values;
        final CountAtBucket[] counts;
        synchronized (this) {
            accumulateIfStale();
            values = takeValueSnapshot();
            counts = takeCountSnapshot();
        }

        return new HistogramSnapshot(count, total, max, values, counts, this::outputSummary);
    }

    private void accumulateIfStale() {
        if (accumulatedHistogramStale) {
            accumulate();
            accumulatedHistogramStale = false;
        }
    }

    private ValueAtPercentile[] takeValueSnapshot() {
        double[] monitoredPercentiles = distributionStatisticConfig.getPercentiles();
        if (monitoredPercentiles == null || monitoredPercentiles.length == 0) {
            return null;
        }

        final ValueAtPercentile[] values = new ValueAtPercentile[monitoredPercentiles.length];
        for (int i = 0; i < monitoredPercentiles.length; i++) {
            final double p = monitoredPercentiles[i];
            values[i] = new ValueAtPercentile(p, valueAtPercentile(p * 100));
        }
        return values;
    }

    private CountAtBucket[] takeCountSnapshot() {
        if (!distributionStatisticConfig.isPublishingHistogram()) {
            return null;
        }

        final Set<Double> monitoredValues = distributionStatisticConfig
            .getHistogramBuckets(supportsAggregablePercentiles);
        if (monitoredValues.isEmpty()) {
            return null;
        }

        final CountAtBucket[] counts = new CountAtBucket[monitoredValues.size()];
        final Iterator<CountAtBucket> iterator = countsAtValues(monitoredValues.iterator());
        for (int i = 0; i < counts.length; i++) {
            counts[i] = iterator.next();
        }
        return counts;
    }

    public void recordLong(long value) {
        rotate();
        try {
            for (T bucket : ringBuffer) {
                recordLong(bucket, value);
            }
        } catch (IndexOutOfBoundsException ignored) {
            // the value is so large (or small) that the dynamic range of the histogram
            // cannot be extended to include it
        } finally {
            accumulatedHistogramStale = true;
        }
    }

    public void recordDouble(double value) {
        rotate();
        try {
            for (T bucket : ringBuffer) {
                recordDouble(bucket, value);
            }
        } catch (IndexOutOfBoundsException ignored) {
            // the value is so large (or small) that the dynamic range of the histogram
            // cannot be extended to include it
        } finally {
            accumulatedHistogramStale = true;
        }
    }

    private void rotate() {
        long timeSinceLastRotateMillis = clock.wallTime() - lastRotateTimestampMillis;
        if (timeSinceLastRotateMillis < durationBetweenRotatesMillis) {
            // Need to wait more for next rotation.
            return;
        }

        if (!rotatingUpdater.compareAndSet(this, 0, 1)) {
            // Being rotated by other thread already.
            return;
        }

        try {
            int iterations = 0;
            synchronized (this) {
                do {
                    resetBucket(ringBuffer[currentBucket]);
                    if (++currentBucket >= ringBuffer.length) {
                        currentBucket = 0;
                    }
                    timeSinceLastRotateMillis -= durationBetweenRotatesMillis;
                    lastRotateTimestampMillis += durationBetweenRotatesMillis;
                }
                while (timeSinceLastRotateMillis >= durationBetweenRotatesMillis && ++iterations < ringBuffer.length);

                resetAccumulatedHistogram();
                accumulatedHistogramStale = true;
            }
        } finally {
            rotating = 0;
        }
    }

    protected U accumulatedHistogram() {
        return accumulatedHistogram;
    }

    protected T currentHistogram() {
        return ringBuffer[currentBucket];
    }

}
