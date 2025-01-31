package ru.tinkoff.kora.opentelemetry.module.soap.client;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;
import ru.tinkoff.kora.soap.client.common.SoapResult;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetry;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTracer;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

public class OpentelemetrySoapClientTracer implements SoapClientTracer {

    private static final AttributeKey<String> FAULT_CODE = stringKey("fault.code");
    private static final AttributeKey<String> FAULT_ACTOR = stringKey("fault.actor");

    private final Tracer tracer;

    private final String serviceName;
    private final String soapMethod;
    private final String url;

    public OpentelemetrySoapClientTracer(Tracer tracer, String serviceName, String soapMethod, String url) {
        this.tracer = tracer;
        this.serviceName = serviceName;
        this.soapMethod = soapMethod;
        this.url = url;
    }

    @Override
    public SoapClientSpan createSpan(Context ctx, SoapEnvelope requestEnvelope) {
        var otctx = OpentelemetryContext.get(ctx);

        var builder = this.tracer.spanBuilder("SOAP " + serviceName + " " + soapMethod)
            .setSpanKind(SpanKind.CLIENT)
            .setParent(otctx.getContext());

        builder.setAttribute(RpcIncubatingAttributes.RPC_SERVICE, serviceName);
        builder.setAttribute(RpcIncubatingAttributes.RPC_METHOD, soapMethod);
        builder.setAttribute(RpcIncubatingAttributes.RPC_SYSTEM, url);

        var span = builder.startSpan();

        var newCtx = otctx.add(span);
        OpentelemetryContext.set(ctx, newCtx);

        return new SoapClientSpan() {

            @Override
            public void success(SoapResult.Success result) {
                span.end();
                OpentelemetryContext.set(ctx, otctx);
            }

            @Override
            public void failure(SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure failure) {
                if (failure instanceof SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure.InvalidHttpCode ie) {
                    span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, ie.code());
                } else if (failure instanceof SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure.InternalServerError se) {
                    span.setAttribute(FAULT_CODE.getKey(), se.result().fault().getFaultcode().toString());
                    span.setAttribute(FAULT_ACTOR.getKey(), se.result().fault().getFaultactor());
                } else if (failure instanceof SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure.ProcessException pe) {
                    span.setAttribute(ErrorAttributes.ERROR_TYPE.getKey(), pe.throwable().getClass().getCanonicalName());
                    span.recordException(pe.throwable());
                }
                span.setStatus(StatusCode.ERROR);
                span.end();
                OpentelemetryContext.set(ctx, otctx);
            }
        };
    }
}
