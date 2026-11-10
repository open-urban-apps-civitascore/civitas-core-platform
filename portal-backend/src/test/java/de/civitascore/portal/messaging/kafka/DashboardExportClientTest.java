package de.civitascore.portal.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.model.dashboardexport.DashboardExportRequest;
import de.civitascore.configadapter.model.dashboardexport.DashboardExportResult;
import de.civitascore.portal.configuration.DashboardExportProperties;
import de.civitascore.portal.util.ExternalSystemRejectionException;
import de.civitascore.portal.util.ExternalSystemTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerSeekAware.ConsumerSeekCallback;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardExportClient Tests")
class DashboardExportClientTest {

  private static final String REQUEST_TOPIC = "test.dashboard-export.request";
  private static final String RESULT_TOPIC = "test.dashboard-export.result";
  private static final Map<String, Object> DOCUMENT =
      Map.of("tool", "superset", "files", Map.of("metadata.yaml", Map.of("version", "1.0.0")));

  private final ObjectMapper objectMapper = new JsonMapper();

  @Mock private KafkaTemplate<String, String> kafkaTemplate;

  private DashboardExportClient client;

  @BeforeEach
  void setUp() {
    client = newClient();
    client.onPartitionsAssigned(
        Map.of(new TopicPartition(RESULT_TOPIC, 0), 0L), mock(ConsumerSeekCallback.class));
  }

  private DashboardExportClient newClient() {
    return new DashboardExportClient(
        kafkaTemplate,
        objectMapper,
        new DashboardExportProperties(REQUEST_TOPIC, RESULT_TOPIC, 30, 2),
        "portal");
  }

  private static DashboardExportRequest request(Duration wait) {
    return new DashboardExportRequest(UUID.randomUUID().toString(), Instant.now().plus(wait), "7");
  }

  private void acknowledgeSends() {
    when(kafkaTemplate.send(eq(REQUEST_TOPIC), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));
  }

  private ConsumerRecord<String, String> record(String key, Object result) {
    return new ConsumerRecord<>(RESULT_TOPIC, 0, 0L, key, objectMapper.writeValueAsString(result));
  }

  private Future<DashboardExportResult> exportInBackground(DashboardExportRequest request) {
    return CompletableFuture.supplyAsync(() -> client.export(request));
  }

  @Test
  @DisplayName("export before the result partitions are assigned is rejected without a send")
  void export_beforePartitionsAreAssigned_isRejected() {
    DashboardExportClient unassigned = newClient();

    assertThatThrownBy(() -> unassigned.export(request(Duration.ofSeconds(10))))
        .isInstanceOf(ExternalSystemRejectionException.class);
    verifyNoInteractions(kafkaTemplate);
  }

  @Test
  @DisplayName("export sends the request keyed by its id and returns the result that arrives")
  void export_whenTheResultArrives_returnsIt() throws Exception {
    acknowledgeSends();
    DashboardExportRequest request = request(Duration.ofSeconds(10));

    Future<DashboardExportResult> answer = exportInBackground(request);
    await().atMost(Duration.ofSeconds(5)).until(() -> client.pendingCount() == 1);
    client.handleResult(
        record(request.requestId(), DashboardExportResult.success(request.requestId(), DOCUMENT)));

    DashboardExportResult result = answer.get(5, TimeUnit.SECONDS);
    assertThat(result.document()).isEqualTo(DOCUMENT);
    assertThat(client.pendingCount()).isZero();
    verify(kafkaTemplate).send(eq(REQUEST_TOPIC), eq(request.requestId()), anyString());
  }

  @Test
  @DisplayName("export without a result before the request expires times out")
  void export_withoutResultBeforeExpiry_timesOut() {
    acknowledgeSends();

    assertThatThrownBy(() -> client.export(request(Duration.ofMillis(200))))
        .isInstanceOf(ExternalSystemTimeoutException.class);
    assertThat(client.pendingCount()).isZero();
  }

  @Test
  @DisplayName("an unreadable result for a waiting request fails it at once")
  void handleResult_withUnreadableResultOfAWaitingRequest_failsItAtOnce() {
    acknowledgeSends();
    DashboardExportRequest request = request(Duration.ofSeconds(10));
    Future<DashboardExportResult> answer = exportInBackground(request);
    await().atMost(Duration.ofSeconds(5)).until(() -> client.pendingCount() == 1);

    client.handleResult(
        new ConsumerRecord<>(
            RESULT_TOPIC,
            0,
            0L,
            request.requestId(),
            "{\"requestId\":\""
                + request.requestId()
                + "\",\"outcome\":\"FAILURE\",\"reason\":\"FROM_A_NEWER_ADAPTER\"}"));

    assertThatThrownBy(() -> answer.get(2, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(ExternalSystemRejectionException.class);
  }

  @Test
  @DisplayName("every client reads results under a consumer group of its own")
  void groupId_isUniquePerInstance() {
    DashboardExportClient other = newClient();

    assertThat(client.getGroupId()).startsWith("portal-dashboard-export-");
    assertThat(client.getGroupId()).isNotEqualTo(other.getGroupId());
    assertThat(client.getResultTopic()).isEqualTo(RESULT_TOPIC);
  }
}
