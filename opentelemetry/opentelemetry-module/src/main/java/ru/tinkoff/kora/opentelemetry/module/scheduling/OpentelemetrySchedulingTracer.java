package ru.tinkoff.kora.opentelemetry.module.scheduling;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingTracer;

public class OpentelemetrySchedulingTracer implements SchedulingTracer {
    private final Tracer tracer;
    private final String className;
    private final String methodName;

    public OpentelemetrySchedulingTracer(Tracer tracer, String className, String methodName) {
        this.tracer = tracer;
        this.className = className;
        this.methodName = methodName;
    }


    @Override
    public SchedulingSpan createSpan(Context ctx) {
        var context = Context.current();
        var span = this.tracer
            .spanBuilder(this.className + " " + this.methodName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(CodeIncubatingAttributes.CODE_FUNCTION, this.methodName)
            .setAttribute(CodeIncubatingAttributes.CODE_FILEPATH, this.className)
            .startSpan();
        OpentelemetryContext.set(context, OpentelemetryContext.get(context).add(span));

        return (exception) -> {
            if (exception != null) {
                span.recordException(exception);
                span.setStatus(StatusCode.ERROR);
            }
            span.end();
        };
    }
}
