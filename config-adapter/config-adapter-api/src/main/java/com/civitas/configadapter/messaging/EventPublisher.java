package com.civitas.configadapter.messaging;

import com.civitas.configadapter.model.ConfigResultEvent;

/**
 * Interface for publishing events from adapters. Adapters can use this to send result events, error
 * notifications, or status updates.
 */
public interface EventPublisher {

  /**
   * Publish a configuration result event to a specific topic. The implementation is responsible for
   * converting the ConfigResultEvent to the appropriate wire format (e.g., CloudEvent).
   *
   * @param topic the topic to publish to
   * @param resultEvent the configuration result event to publish
   */
  void publish(String topic, ConfigResultEvent resultEvent);
}
