package de.civitascore.portal.messaging;

import com.civitas.configadapter.model.ConfigResultEvent;
import io.cloudevents.CloudEvent;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for publishing CloudEvents to a messaging system. Implementations can support different
 * messaging backends (Kafka, RabbitMQ, etc.).
 *
 * <p>All publish operations are asynchronous and return a CompletableFuture that completes when the
 * Config Adapter processes the event and sends back a result.
 */
public interface CloudEventPublisher {

  /**
   * Publish a CloudEvent asynchronously to a specific topic/queue.
   *
   * <p>The returned CompletableFuture completes when:
   *
   * <ul>
   *   <li>The Config Adapter processes the event and sends a SUCCESS result to the result topic
   *   <li>The Config Adapter processes the event and sends a FAILURE result to the result topic
   *   <li>Publishing to the topic fails
   *   <li>A timeout occurs waiting for the result
   * </ul>
   *
   * @param topic the topic or queue name to publish to
   * @param messageId the unique message identifier (used as message key in some implementations)
   * @param cloudEvent the CloudEvent to publish
   * @return a CompletableFuture that completes with the ConfigResultEvent from the Config Adapter,
   *     or exceptionally if publishing fails or times out
   */
  CompletableFuture<ConfigResultEvent> publishAsync(
      String topic, String messageId, CloudEvent cloudEvent);

  /**
   * Get the name of this publisher implementation.
   *
   * @return the publisher name (e.g., "kafka", "rabbitmq")
   */
  String getName();

  /**
   * Check if this publisher is ready to publish events.
   *
   * @return true if ready, false otherwise
   */
  boolean isReady();

  /**
   * Get the result topic name where Config Adapter result events will be published.
   *
   * @return the result topic name
   */
  String getResultTopic();

  /** Exception thrown when event publishing fails. */
  class PublishException extends Exception {
    private static final long serialVersionUID = 1L;

    public PublishException(String message, Throwable cause) {
      super(message, cause);
    }

    public PublishException(String message) {
      super(message);
    }
  }
}
