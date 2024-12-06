package ru.tinkoff.kora.micrometer.prometheus.kora;

import io.prometheus.metrics.core.exemplars.ExemplarSampler;

/**
 * A factory that creates {@link ExemplarSampler} instances with the desired properties.
 * <p>
 * Credits to Jonatan Ivanov
 */
interface ExemplarSamplerFactory {

    /**
     * Creates an {@link ExemplarSampler} that stores the defined amount of exemplars.
     *
     * @param numberOfExemplars the amount of exemplars stored by the sampler.
     * @return a new {@link ExemplarSampler} instance.
     */
    ExemplarSampler createExemplarSampler(int numberOfExemplars);

    /**
     * Creates an {@link ExemplarSampler} that stores exemplars for the defined histogram
     * buckets. This means as many exemplars as buckets are defined.
     *
     * @param histogramUpperBounds histogram buckets to store exemplars for.
     * @return a new {@link ExemplarSampler} instance.
     */
    ExemplarSampler createExemplarSampler(double[] histogramUpperBounds);

}
