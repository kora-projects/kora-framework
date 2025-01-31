package ru.tinkoff.kora.micrometer.module.http.server.tag;

import io.micrometer.core.instrument.Tag;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * @see <a href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-metrics.md#metric-httpserverrequestduration">link</a>
 */
public final class Opentelemetry123MicrometerHttpServerTagsProvider implements MicrometerHttpServerTagsProvider {

    @Override
    public Iterable<Tag> getActiveRequestsTags(ActiveRequestsKey key) {
        return List.of(
            Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), key.target()),
            Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), key.method()),
            Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), key.host()),
            Tag.of(UrlAttributes.URL_SCHEME.getKey(), key.scheme())
        );
    }

    @Override
    public Iterable<Tag> getDurationTags(DurationKey key) {
        var list = new ArrayList<Tag>(6);
        if (key.errorType() != null) {
            list.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), key.errorType().getCanonicalName()));
        } else {
            list.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), ""));
        }
        list.add(Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), key.method()));
        list.add(Tag.of(HttpAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), Integer.toString(key.statusCode())));
        list.add(Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), key.route()));
        list.add(Tag.of(UrlAttributes.URL_SCHEME.getKey(), key.scheme()));
        list.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), key.host()));
        return list;
    }

}
