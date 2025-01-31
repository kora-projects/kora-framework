package ru.tinkoff.kora.opentelemetry.module.grpc.server;

import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.incubating.NetIncubatingAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.grpc.server.telemetry.GrpcServerTracer;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

import static io.opentelemetry.context.Context.root;

public final class OpentelemetryGrpcServerTracer implements GrpcServerTracer {
    private final Tracer tracer;

    public OpentelemetryGrpcServerTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    private enum GrpcHeaderMapGetter implements TextMapGetter<Metadata> {
        INSTANCE;

        @Override
        public Iterable<String> keys(Metadata carrier) {
            return carrier.keys();
        }

        @Nullable
        @Override
        public String get(@Nullable Metadata carrier, String key) {
            return carrier.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
        }
    }

    @Override
    public GrpcServerSpan createSpan(ServerCall<?, ?> call, Metadata headers, String serviceName, String methodName) {
        var ipAddress = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR).toString();
        var context = Context.current();
        var parentCtx = W3CTraceContextPropagator.getInstance().extract(root(), headers, OpentelemetryGrpcServerTracer.GrpcHeaderMapGetter.INSTANCE);
        @SuppressWarnings("deprecation")
        var span = this.tracer
            .spanBuilder(serviceName + "/" + methodName)
            .setSpanKind(SpanKind.SERVER)
            .setParent(parentCtx)
            .setAttribute(RpcIncubatingAttributes.RPC_SYSTEM, "grpc")
            .setAttribute(RpcIncubatingAttributes.RPC_SERVICE, serviceName)
            .setAttribute(RpcIncubatingAttributes.RPC_METHOD, methodName)
            .setAttribute(NetIncubatingAttributes.NET_SOCK_PEER_ADDR, ipAddress)
            .setAttribute(NetworkAttributes.NETWORK_PEER_ADDRESS, ipAddress)
            .startSpan();

        OpentelemetryContext.set(context, OpentelemetryContext.get(context).add(span));


        return new OpentelemetryGrpcServerSpan(span);
    }
}
