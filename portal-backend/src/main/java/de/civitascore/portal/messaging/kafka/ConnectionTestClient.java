package de.civitascore.portal.messaging.kafka;

import de.civitascore.configadapter.model.connectiontest.ConnectionTestRequest;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult;
import de.civitascore.portal.configuration.ConnectionTestProperties;
import de.civitascore.portal.util.ExternalSystemRejectionException;
import de.civitascore.portal.util.ExternalSystemTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.owasp.encoder.Encode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.support.KafkaUtils;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Sends connection test requests to config-adapter and waits for their results. Each backend
 * instance has its own consumer group, because only the sending instance waits for a result.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
public class ConnectionTestClient implements ConsumerSeekAware {

  private final KafkaTemplate<String, String> eventKafkaTemplate;
  private final ObjectMapper objectMapper;
  private final String requestTopic;

  /** The result topic, exposed for the listener's SpEL expression. */
  @Getter private final String resultTopic;

  /** The consumer group of this instance, exposed for the listener's SpEL expression. */
  @Getter private final String groupId;

  /** Whether the result partitions are assigned, so that no result can pass unseen. */
  @Getter private volatile boolean ready;

  private static final Executor SENDER = runnable -> Thread.ofVirtual().start(runnable);

  private final Map<String, CompletableFuture<ConnectionTestResult>> pending =
      new ConcurrentHashMap<>();

  public ConnectionTestClient(
      KafkaTemplate<String, String> eventKafkaTemplate,
      ObjectMapper objectMapper,
      ConnectionTestProperties properties,
      @Value("${spring.kafka.consumer.group-id:civitas-portal-backend}") String groupIdPrefix) {
    this.eventKafkaTemplate = eventKafkaTemplate;
    this.objectMapper = objectMapper;
    this.requestTopic = properties.requestTopic();
    this.resultTopic = properties.resultTopic();
    this.groupId = groupIdPrefix + "-connection-test-" + UUID.randomUUID();
    log.info(
        "Connection test client reads {} as consumer group {}",
        Encode.forJava(resultTopic),
        Encode.forJava(groupId));
  }

  /**
   * Sends {@code request} and waits for its result until {@link ConnectionTestRequest#expiresAt()}.
   *
   * @throws ExternalSystemRejectionException if the request cannot be sent, or the result
   *     partitions are not assigned yet
   * @throws ExternalSystemTimeoutException if no result arrives before the request expires
   */
  public ConnectionTestResult test(ConnectionTestRequest request) {
    if (!ready) {
      throw new ExternalSystemRejectionException("The connection test is not ready yet");
    }
    String requestId = request.requestId();
    CompletableFuture<ConnectionTestResult> result = new CompletableFuture<>();
    if (pending.putIfAbsent(requestId, result) != null) {
      throw new IllegalStateException("A connection test with this request id is already waiting");
    }
    try {
      send(request);
      return result.get(remainingMillis(request), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      throw new ExternalSystemTimeoutException(
          "No result for connection test " + requestId + " before it expired", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExternalSystemTimeoutException(
          "Interrupted while waiting for connection test " + requestId, e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("A connection test result never completes exceptionally", e);
    } finally {
      pending.remove(requestId);
    }
  }

  /** Completes the request that waits for {@code record}; ignores every other result. */
  @KafkaListener(
      topics = "#{__listener.resultTopic}",
      groupId = "#{__listener.groupId}",
      containerFactory = "connectionTestKafkaListenerContainerFactory")
  public void handleResult(ConsumerRecord<String, String> record) {
    if (record.value() == null) {
      log.warn("Dropped connection test result {} without a value", KafkaUtils.format(record));
      return;
    }
    ConnectionTestResult result;
    try {
      result = objectMapper.readValue(record.value(), ConnectionTestResult.class);
    } catch (JacksonException e) {
      // The exception message can quote the record, whose preview may hold source data.
      log.error(
          "Dropped unreadable connection test result {}: {}",
          KafkaUtils.format(record),
          e.getClass().getSimpleName());
      return;
    }
    CompletableFuture<ConnectionTestResult> waiting =
        result.requestId() == null ? null : pending.get(result.requestId());
    if (waiting == null) {
      log.debug(
          "Ignored connection test result {}, which no request of this instance waits for",
          KafkaUtils.format(record));
      return;
    }
    waiting.complete(result);
  }

  @Override
  public void onPartitionsAssigned(
      Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
    if (!assignments.isEmpty()) {
      ready = true;
    }
  }

  @Override
  public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
    // A rebalance of this instance's own group happens only at shutdown.
  }

  int pendingCount() {
    return pending.size();
  }

  private void send(ConnectionTestRequest request) {
    String requestId = request.requestId();
    String json = objectMapper.writeValueAsString(request);
    try {
      // send() can block on metadata for max.block.ms (60 s by default). Running it on another
      // thread bounds the send by the request's wait.
      CompletableFuture.supplyAsync(
              () -> eventKafkaTemplate.send(requestTopic, requestId, json), SENDER)
          .thenCompose(Function.identity())
          .get(remainingMillis(request), TimeUnit.MILLISECONDS);
    } catch (ExecutionException e) {
      throw new ExternalSystemRejectionException(
          "Connection test " + requestId + " could not be sent: " + rootCauseOf(e));
    } catch (TimeoutException e) {
      throw new ExternalSystemTimeoutException(
          "Connection test " + requestId + " was not acknowledged before it expired", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExternalSystemTimeoutException(
          "Interrupted while sending connection test " + requestId, e);
    }
  }

  private static long remainingMillis(ConnectionTestRequest request) {
    return Math.max(0, Duration.between(Instant.now(), request.expiresAt()).toMillis());
  }

  private static String rootCauseOf(Throwable e) {
    Throwable root = e;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    return root.getClass().getSimpleName();
  }
}
