package ru.tinkoff.kora.micrometer.prometheus.kora;

/**
 * {@link PrometheusNamingConvention} with {@code _duration} suffix for timers.
 * <p>
 * Credits to Clint Checketts
 */
public class PrometheusDurationNamingConvention extends PrometheusNamingConvention {

    public PrometheusDurationNamingConvention() {
        super("_duration");
    }
}
