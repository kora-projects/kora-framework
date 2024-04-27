package ru.tinkoff.kora.bpmn.camunda7.rest.undertow;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.bpmn.camunda7.rest.CamundaRestHttpServer;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;

record FakeCamundaRestHttpServer(int port) implements CamundaRestHttpServer {

    @Override
    public void init() {
        // do nothing
    }

    @Override
    public void release() {
        // do nothing
    }

    @Nullable
    @Override
    public ReadinessProbeFailure probe() {
        return null;
    }
}