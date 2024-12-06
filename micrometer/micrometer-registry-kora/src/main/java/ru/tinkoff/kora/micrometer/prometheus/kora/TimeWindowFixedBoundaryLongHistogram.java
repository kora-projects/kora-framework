package ru.tinkoff.kora.micrometer.prometheus.kora;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.NavigableSet;
import java.util.Objects;

/**
 * A histogram implementation that does not support precomputed percentiles but supports
 * aggregable percentile histograms and SLO boundaries. There is no need for a high
 * dynamic range histogram and its more expensive memory footprint if all we are
 * interested in is fixed histogram counts.
 * <p>
 * Credits to Jon Schneider
 */
public class TimeWindowFixedBoundaryLongHistogram extends AbstractTimeWindowHistogram<FixedBoundaryLongHistogram, Void> {

    private final double[] buckets;

    private final boolean isCumulativeBucketCounts;

    public TimeWindowFixedBoundaryLongHistogram(Clock clock,
                                                DistributionStatisticConfig config,
                                                boolean supportsAggregablePercentiles) {
        this(clock, config, supportsAggregablePercentiles, true);
    }

    /**
     * Create a {@code TimeWindowFixedBoundaryHistogram} instance.
     *
     * @param clock                         clock
     * @param config                        distribution statistic configuration
     * @param supportsAggregablePercentiles whether it supports aggregable percentiles
     * @param isCumulativeBucketCounts      whether it uses cumulative bucket counts
     */
    public TimeWindowFixedBoundaryLongHistogram(Clock clock,
                                                DistributionStatisticConfig config,
                                                boolean supportsAggregablePercentiles,
                                                boolean isCumulativeBucketCounts) {
        super(clock, config, FixedBoundaryLongHistogram.class, supportsAggregablePercentiles);

        this.isCumulativeBucketCounts = isCumulativeBucketCounts;

        NavigableSet<Double> histogramBuckets = distributionStatisticConfig.getHistogramBuckets(supportsAggregablePercentiles);
        this.buckets = histogramBuckets.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
        initRingBuffer();
    }

    @Override
    FixedBoundaryLongHistogram newBucket() {
        return new FixedBoundaryLongHistogram(this.buckets, isCumulativeBucketCounts);
    }

    @Override
    void recordLong(FixedBoundaryLongHistogram bucket, long value) {
        bucket.record(value);
    }

    @Override
    final void recordDouble(FixedBoundaryLongHistogram bucket, double value) {
        recordLong(bucket, (long) Math.ceil(value));
    }

    @Override
    void resetBucket(FixedBoundaryLongHistogram bucket) {
        bucket.reset();
    }

    @Override
    Void newAccumulatedHistogram(FixedBoundaryLongHistogram[] ringBuffer) {
        return null;
    }

    @Override
    void accumulate() {
        // do nothing -- we aren't using swaps for source and accumulated
    }

    @Override
    void resetAccumulatedHistogram() {
    }

    @Override
    double valueAtPercentile(double percentile) {
        return 0;
    }

    @Override
    Iterator<CountAtBucket> countsAtValues(Iterator<Double> values) {
        return currentHistogram().countsAtValues(values);
    }

    @Override
    void outputSummary(PrintStream printStream, double bucketScaling) {
        printStream.format("%14s %10s\n\n", "Bucket", "TotalCount");

        String bucketFormatString = "%14.1f %10d\n";

        var currentHistogram = currentHistogram();
        for (int i = 0; i < buckets.length; i++) {
            printStream.format(Locale.ROOT, bucketFormatString, buckets[i] / bucketScaling,
                currentHistogram.values.get(i));
        }

        printStream.write('\n');
    }

    /**
     * Return buckets.
     *
     * @return buckets
     */
    protected double[] getBuckets() {
        return this.buckets;
    }
}
