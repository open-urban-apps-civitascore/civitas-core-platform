package com.civitas.configadapter.messaging;

import io.cloudevents.CloudEvent;

@FunctionalInterface
public interface CloudEventHandler {

    void handleEvent(CloudEvent event);
}
