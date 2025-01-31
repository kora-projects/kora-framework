package ru.tinkoff.kora.micrometer.module.http.server.tag;

import io.micrometer.core.instrument.Tag;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;

import java.util.List;

/**
 * @see <a href="https://github.com/open-telemetry/opentelemetry-specification/blob/v1.20.0/specification/metrics/semantic_conventions/http-metrics.md">link</a>
 */
public class DefaultMicrometerHttpServerTagsProvider implements MicrometerHttpServerTagsProvider {

    @Override
    @SuppressWarnings("deprecation")
    public Iterable<Tag> getActiveRequestsTags(ActiveRequestsKey key) {
        return List.of(
            Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), key.target()),
            Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), key.method()),
            Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), key.host()),
            Tag.of(UrlAttributes.URL_SCHEME.getKey(), key.scheme()),
            Tag.of(HttpIncubatingAttributes.HTTP_TARGET.getKey(), key.target()),
            Tag.of(HttpIncubatingAttributes.HTTP_METHOD.getKey(), key.method())
        );
    }

    @Override
    @SuppressWarnings("deprecation")
    public Iterable<Tag> getDurationTags(DurationKey key) {
        return List.of(
            Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), key.method()),
            Tag.of(HttpAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), Integer.toString(key.statusCode())),
            Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), key.route()),
            Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), key.host()),
            Tag.of(UrlAttributes.URL_SCHEME.getKey(), key.scheme()),
            Tag.of(HttpIncubatingAttributes.HTTP_TARGET.getKey(), key.route()),
            Tag.of(HttpIncubatingAttributes.HTTP_METHOD.getKey(), key.method()),
            Tag.of(HttpIncubatingAttributes.HTTP_STATUS_CODE.getKey(), Integer.toString(key.statusCode()))
        );
    }

}
