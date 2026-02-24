package de.civitascore.portal.messaging.kafka;

import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.portal.messaging.CloudEventPublisher;
import io.cloudevents.CloudEvent;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * Kafka implementation of CloudEventPublisher.
 *
 * <p>This publisher:
 *
 * <ul>
 *   <li>Publishes events asynchronously to Kafka topics
 *   <li>Tracks pending requests waiting for Config Adapter results
 *   <li>Returns CompletableFutures that complete when results are received
 *   <li>Handles timeouts for requests that don't receive results
 * </ul>
 */
@Slf4j
public class KafkaCloudEventPublisher implements CloudEventPublisher {

  public static final String NAME = "kafka";

  private final KafkaProducer<String, CloudEvent> kafkaProducer;
  private final String resultTopic;
  private final long resultTimeoutMs;

  // Map of messageId -> CompletableFuture waiting for result
  private final Map<String, CompletableFuture<ConfigResultEvent>> pendingRequests =
      new ConcurrentHashMap<>();

  public KafkaCloudEventPublisher(
      KafkaProducer<String, CloudEvent> kafkaProducer, String resultTopic, long resultTimeoutMs) {
    this.kafkaProducer = kafkaProducer;
    this.resultTopic = resultTopic;
    this.resultTimeoutMs = resultTimeoutMs;
    log.info(
        "Kafka CloudEvent publisher initialized (resultTopic={}, timeout={}ms)",
        resultTopic,
        resultTimeoutMs);
  }

  public KafkaCloudEventPublisher(
      KafkaProducer<String, CloudEvent> kafkaProducer, String resultTopic) {
    this(kafkaProducer, resultTopic, 30000); // Default 30 second timeout
  }

  @Override
  public CompletableFuture<ConfigResultEvent> publishAsync(
      String topic, String messageId, CloudEvent cloudEvent) {

    CompletableFuture<ConfigResultEvent> resultFuture = new CompletableFuture<>();

    // Register this request to wait for result
    pendingRequests.put(messageId, resultFuture);

    setupTimout(topic, messageId);
    publish(topic, messageId, cloudEvent, resultFuture);

    return resultFuture;
  }

  /**
   * Called by the result consumer when a ConfigResultEvent is received.
   *
   * <p>This completes the CompletableFuture for the corresponding request.
   *
   * @param resultEvent the result event from the Config Adapter
   */
  public void handleResult(ConfigResultEvent resultEvent) {
    String originalMessageId = resultEvent.originalMessageId();
    if (Objects.nonNull(originalMessageId)) {
      CompletableFuture<ConfigResultEvent> future = pendingRequests.remove(originalMessageId);

      if (future != null) {
        if (resultEvent.status() == ConfigResultEvent.Status.SUCCESS) {
          log.info(
              "Config Adapter SUCCESS: messageId={}, operation={}, resourceId={}",
              originalMessageId,
              resultEvent.operation(),
              resultEvent.resourceId());
          future.complete(resultEvent);
        }
      } else {
        log.error(
            "Config Adapter FAILURE: messageId={}, operation={}, error={}, message={}",
            originalMessageId,
            resultEvent.operation(),
            resultEvent.errorCode(),
            resultEvent.message());
        future.completeExceptionally(
            new CloudPublishException(
                "Config Adapter processing failed: "
                    + resultEvent.message()
                    + " (errorCode="
                    + resultEvent.errorCode()
                    + ")"));
      }
    } else {
      log.warn("Received result for unknown messageId={} (may have timed out)", originalMessageId);
    }
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean isReady() {
    // Kafka producer is always ready once created
    return true;
  }

  @Override
  public String getResultTopic() {
    return resultTopic;
  }

  /**
   * Get the number of pending requests waiting for results.
   *
   * @return the count of pending requests
   */
  public int getPendingRequestCount() {
    return pendingRequests.size();
  }

  private void setupTimout(String topic, String messageId) {
    CompletableFuture.delayedExecutor(resultTimeoutMs, TimeUnit.MILLISECONDS)
        .execute(
            () -> {
              CompletableFuture<ConfigResultEvent> pending = pendingRequests.remove(messageId);
              if (pending != null && !pending.isDone()) {
                pending.completeExceptionally(
                    new CloudPublishException(
                        "Timeout waiting for Config Adapter result: messageId="
                            + messageId
                            + ", timeout="
                            + resultTimeoutMs
                            + "ms"));
                log.warn(
                    "Timeout waiting for result: messageId={}, topic={}, timeout={}ms",
                    messageId,
                    topic,
                    resultTimeoutMs);
              }
            });
  }

  private void publish(
      String topic,
      String messageId,
      CloudEvent cloudEvent,
      CompletableFuture<ConfigResultEvent> resultFuture) {
    ProducerRecord<String, CloudEvent> producerRecord =
        new ProducerRecord<>(topic, messageId, cloudEvent);
    kafkaProducer.send(
        producerRecord,
        (metadata, exception) -> {
          if (exception != null) {
            log.error(
                "Failed to send CloudEvent to Kafka: messageId={}, topic={}",
                messageId,
                topic,
                exception);
            pendingRequests.remove(messageId);
            resultFuture.completeExceptionally(
                new CloudPublishException(
                    "Failed to publish CloudEvent to Kafka: messageId=" + messageId, exception));
          } else {
            log.debug(
                "CloudEvent sent to Kafka: messageId={}, topic={}, partition={}, offset={} - waiting for result on topic={}",
                messageId,
                metadata.topic(),
                metadata.partition(),
                metadata.offset(),
                resultTopic);
          }
        });
  }
}
