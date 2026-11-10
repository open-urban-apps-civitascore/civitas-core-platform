package de.civitascore.portal.messaging.kafka;

import de.civitascore.configadapter.model.dashboardexport.DashboardExportRequest;
import de.civitascore.configadapter.model.dashboardexport.DashboardExportResult;
import de.civitascore.portal.configuration.DashboardExportProperties;
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
 * Sends dashboard export requests to config-adapter and waits for their results. Each backend
 * instance has its own consumer group, because only the sending instance waits for a result. The
 * pattern is the one of {@link ConnectionTestClient}, and so is the listener container factory: it
 * reads from the latest offset and drops a record it cannot process.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
public class DashboardExportClient implements ConsumerSeekAware {

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

  private final Map<String, CompletableFuture<DashboardExportResult>> pending =
      new ConcurrentHashMap<>();

  public DashboardExportClient(
      KafkaTemplate<String, String> eventKafkaTemplate,
      ObjectMapper objectMapper,
      DashboardExportProperties properties,
      @Value("${spring.kafka.consumer.group-id:civitas-portal-backend}") String groupIdPrefix) {
    this.eventKafkaTemplate = eventKafkaTemplate;
    this.objectMapper = objectMapper;
    this.requestTopic = properties.requestTopic();
    this.resultTopic = properties.resultTopic();
    this.groupId = groupIdPrefix + "-dashboard-export-" + UUID.randomUUID();
    log.info(
        "Dashboard export client reads {} as consumer group {}",
        Encode.forJava(resultTopic),
        Encode.forJava(groupId));
  }

  /**
   * Sends {@code request} and waits for its result until {@link
   * DashboardExportRequest#expiresAt()}.
   *
   * @throws ExternalSystemRejectionException if the request cannot be sent, the result partitions
   *     are not assigned yet, or the answer cannot be read
   * @throws ExternalSystemTimeoutException if no result arrives before the request expires
   */
  public DashboardExportResult export(DashboardExportRequest request) {
    if (!ready) {
      throw new ExternalSystemRejectionException("The dashboard export is not ready yet");
    }
    String requestId = request.requestId();
    CompletableFuture<DashboardExportResult> result = new CompletableFuture<>();
    pending.put(requestId, result);
    try {
      send(request);
      return result.get(remainingMillis(request), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      throw new ExternalSystemTimeoutException(
          "No result for dashboard export " + requestId + " before it expired", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExternalSystemTimeoutException(
          "Interrupted while waiting for dashboard export " + requestId, e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ExternalSystemRejectionException rejection) {
        throw rejection;
      }
      throw new IllegalStateException("Dashboard export " + requestId + " failed unexpectedly", e);
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
      log.warn("Dropped dashboard export result {} without a value", KafkaUtils.format(record));
      return;
    }
    DashboardExportResult result;
    try {
      result = objectMapper.readValue(record.value(), DashboardExportResult.class);
    } catch (JacksonException e) {
      // The exception message can quote the record, whose document is the user's dashboard.
      log.error(
          "Dropped unreadable dashboard export result {}: {}",
          KafkaUtils.format(record),
          e.getClass().getSimpleName());
      // The key is the request id, so a waiting request fails at once instead of timing out.
      CompletableFuture<DashboardExportResult> waiting =
          record.key() == null ? null : pending.get(record.key());
      if (waiting != null) {
        waiting.completeExceptionally(
            new ExternalSystemRejectionException("The dashboard export answer cannot be read"));
      }
      return;
    }
    CompletableFuture<DashboardExportResult> waiting =
        result.requestId() == null ? null : pending.get(result.requestId());
    if (waiting == null) {
      log.debug(
          "Ignored dashboard export result {}, which no request of this instance waits for",
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

  private void send(DashboardExportRequest request) {
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
          "Dashboard export " + requestId + " could not be sent: " + rootCauseOf(e));
    } catch (TimeoutException e) {
      throw new ExternalSystemTimeoutException(
          "Dashboard export " + requestId + " was not acknowledged before it expired", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExternalSystemTimeoutException(
          "Interrupted while sending dashboard export " + requestId, e);
    }
  }

  private static long remainingMillis(DashboardExportRequest request) {
    return Math.max(0, Duration.between(Instant.now(), request.expiresAt()).toMillis());
  }

  private static String rootCauseOf(Throwable e) {
    Throwable root = e;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    return root.getClass().getSimpleName();
  }
}
