package ru.tinkoff.kora.micrometer.prometheus.kora;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.config.MeterRegistryConfig;
import io.micrometer.core.instrument.config.validate.Validated;

import java.time.Duration;
import java.util.Properties;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getBoolean;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getDuration;

/**
 * Configuration for {@link PrometheusMeterRegistry}.
 * <p>
 * Credits to Jon Schneider
 * Credits to Jonatan Ivanov
 */
public interface PrometheusConfig extends MeterRegistryConfig {

    /**
     * Accept configuration defaults
     */
    PrometheusConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "prometheus";
    }

    /**
     * @return {@code true} if meter descriptions should be sent to Prometheus. Turn this
     * off to minimize the amount of data sent on each scrape.
     */
    default boolean descriptions() {
        return getBoolean(this, "descriptions").orElse(true);
    }

    /**
     * @return The step size to use in computing windowed statistics like max. The default
     * is 1 minute. To get the most out of these statistics, align the step interval to be
     * close to your scrape interval.
     */
    default Duration step() {
        return getDuration(this, "step").orElse(Duration.ofMinutes(1));
    }

    /**
     * @return an instance of {@link Properties} that contains Prometheus Java Client
     * config entries, for example
     * {@code io.prometheus.exporter.exemplarsOnAllMetricTypes=true}.
     * @see <a href="https://prometheus.github.io/client_java/config/config/">Prometheus
     * docs</a>
     */
    @Nullable
    default Properties prometheusProperties() {
        Properties properties = new Properties();
        properties.setProperty("io.prometheus.exporter.exemplarsOnAllMetricTypes", "true");
        return properties;
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, checkRequired("step", PrometheusConfig::step));
    }

}
