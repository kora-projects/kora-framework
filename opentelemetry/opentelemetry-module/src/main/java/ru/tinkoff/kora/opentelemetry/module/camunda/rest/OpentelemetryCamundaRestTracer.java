package ru.tinkoff.kora.opentelemetry.module.camunda.rest;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestTracer;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static io.opentelemetry.context.Context.root;

public final class OpentelemetryCamundaRestTracer implements CamundaRestTracer {

    private final Tracer tracer;

    public OpentelemetryCamundaRestTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public <T> void inject(Context context, T headers, HeadersSetter<T> headersSetter) {
        W3CTraceContextPropagator.getInstance().inject(
            OpentelemetryContext.get(context).getContext(),
            headers,
            headersSetter::set
        );
    }

    @Override
    public CamundaRestSpan createSpan(String scheme,
                               String host,
                               String method,
                               String path,
                               String pathTemplate,
                               HttpHeaders headers,
                               Map<String, ? extends Collection<String>> queryParams,
                               HttpBodyInput body) {
        var context = Context.current();
        var parentCtx = W3CTraceContextPropagator.getInstance().extract(root(), headers, HttpHeadersTextMapGetter.INSTANCE);
        var span = this.tracer
            .spanBuilder(method + " " + pathTemplate)
            .setSpanKind(SpanKind.SERVER)
            .setParent(parentCtx)
            .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, method)
            .setAttribute(UrlAttributes.URL_SCHEME, scheme)
            .setAttribute(ServerAttributes.SERVER_ADDRESS, host)
            .setAttribute(UrlAttributes.URL_PATH, path)
            .setAttribute(HttpAttributes.HTTP_ROUTE, pathTemplate)
            .startSpan();

        OpentelemetryContext.set(context, OpentelemetryContext.get(context).add(span));

        return (statusCode, resultCode, exception) -> {
            if (statusCode >= 500 || resultCode == HttpResultCode.CONNECTION_ERROR || exception != null && !(exception instanceof HttpServerResponse)) {
                span.setAttribute("http.response.result_code", resultCode.string());
                span.setStatus(StatusCode.ERROR);
            }
            if (exception != null) {
                span.recordException(exception);
            }
            if (statusCode >= 0) {
                span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, statusCode);
            }
            span.end();
        };
    }

    private static class HttpHeadersTextMapGetter implements TextMapGetter<HttpHeaders> {

        private static final HttpHeadersTextMapGetter INSTANCE = new HttpHeadersTextMapGetter();

        @Override
        public Iterable<String> keys(HttpHeaders carrier) {
            return () -> new Iterator<>() {
                final Iterator<Map.Entry<String, List<String>>> i = carrier.iterator();

                @Override
                public boolean hasNext() {
                    return i.hasNext();
                }

                @Override
                public String next() {
                    return i.next().getKey();
                }
            };
        }

        @Override
        public String get(HttpHeaders carrier, String key) {
            return carrier.getFirst(key);
        }
    }
}
