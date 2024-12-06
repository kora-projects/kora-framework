package ru.tinkoff.kora.micrometer.prometheus.kora;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts known meter names from Micrometer's preferred name to Prometheus' preferred
 * name.
 * <p>
 * Credits to Tommy Ludwig
 */
public class PrometheusRenameFilter implements MeterFilter {

    private static final Map<String, String> MICROMETER_TO_PROMETHEUS_NAMES = new HashMap<>();

    static {
        MICROMETER_TO_PROMETHEUS_NAMES.put("process.files.open", "process.open.fds");
        MICROMETER_TO_PROMETHEUS_NAMES.put("process.files.max", "process.max.fds");
    }

    @Override
    public Meter.Id map(Meter.Id id) {
        if (id.getName().equals("process.start.time")) {
            return new Meter.Id(id.getName(), Tags.of(id.getTagsAsIterable()), id.getBaseUnit(),
                "Start time of the process since unix epoch in seconds.", id.getType());
        }
        String convertedName = MICROMETER_TO_PROMETHEUS_NAMES.get(id.getName());
        return convertedName == null ? id : id.withName(convertedName);
    }
}
