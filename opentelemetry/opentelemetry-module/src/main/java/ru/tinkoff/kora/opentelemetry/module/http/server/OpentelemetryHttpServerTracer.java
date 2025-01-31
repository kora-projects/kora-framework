package ru.tinkoff.kora.opentelemetry.module.http.server;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.router.PublicApiRequest;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTracer;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static io.opentelemetry.context.Context.root;

public final class OpentelemetryHttpServerTracer implements HttpServerTracer {
    private final Tracer tracer;

    public OpentelemetryHttpServerTracer(Tracer tracer) {
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
    public HttpServerSpan createSpan(String template, PublicApiRequest routerRequest) {

        var context = Context.current();
        var parentCtx = W3CTraceContextPropagator.getInstance().extract(root(), routerRequest, PublicApiRequestTextMapGetter.INSTANCE);
        var span = this.tracer
            .spanBuilder(routerRequest.method() + " " + template)
            .setSpanKind(SpanKind.SERVER)
            .setParent(parentCtx)
            .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, routerRequest.method())
            .setAttribute(UrlAttributes.URL_SCHEME, routerRequest.scheme())
            .setAttribute(ServerAttributes.SERVER_ADDRESS, routerRequest.hostName())
            .setAttribute(UrlAttributes.URL_PATH, routerRequest.path())
            .setAttribute(HttpAttributes.HTTP_ROUTE, template)
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

    private static class PublicApiRequestTextMapGetter implements TextMapGetter<PublicApiRequest> {
        private static final PublicApiRequestTextMapGetter INSTANCE = new PublicApiRequestTextMapGetter();

        @Override
        public Iterable<String> keys(PublicApiRequest carrier) {
            return () -> new Iterator<>() {
                final Iterator<Map.Entry<String, List<String>>> i = carrier.headers().iterator();

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
        public String get(PublicApiRequest carrier, String key) {
            return carrier.headers().getFirst(key);
        }
    }

}
