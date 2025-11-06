package com.civitas.configadapter.messaging;

import io.cloudevents.CloudEvent;

/**
 * Interface for publishing events from adapters.
 * Adapters can use this to send result events, error notifications, or status updates.
 */
public interface EventPublisher {

    /**
     * Publish a CloudEvent to a specific topic.
     *
     * @param topic the topic to publish to
     * @param event the CloudEvent to publish
     */
    void publish(String topic, CloudEvent event);
}
