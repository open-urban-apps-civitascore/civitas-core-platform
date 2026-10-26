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

import de.civitascore.configadapter.model.connectiontest.ConnectionTestRequest;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Outcome;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Reason;
import de.civitascore.portal.configuration.ConnectionTestProperties;
import de.civitascore.portal.util.ExternalSystemRejectionException;
import de.civitascore.portal.util.ExternalSystemTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerSeekAware.ConsumerSeekCallback;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConnectionTestClient Tests")
class ConnectionTestClientTest {

  private static final String REQUEST_TOPIC = "test.connection-test.request";
  private static final String RESULT_TOPIC = "test.connection-test.result";

  private final ObjectMapper objectMapper = new JsonMapper();

  @Mock private KafkaTemplate<String, String> kafkaTemplate;

  private ConnectionTestClient client;

  @BeforeEach
  void setUp() {
    client = newClient();
    assignPartitions(client);
  }

  private static void assignPartitions(ConnectionTestClient instance) {
    instance.onPartitionsAssigned(
        Map.of(new TopicPartition(RESULT_TOPIC, 0), 0L), mock(ConsumerSeekCallback.class));
  }

  private ConnectionTestClient newClient() {
    return new ConnectionTestClient(
        kafkaTemplate,
        objectMapper,
        new ConnectionTestProperties(REQUEST_TOPIC, RESULT_TOPIC, 20, 4),
        "portal");
  }

  private static ConnectionTestRequest request(Duration wait) {
    return new ConnectionTestRequest(
        UUID.randomUUID().toString(),
        Instant.now().plus(wait),
        Map.of("connectionType", "sql", "password", "ENC(abc)"),
        null,
        false);
  }

  private static ConnectionTestResult success(String requestId) {
    return new ConnectionTestResult(
        requestId, Outcome.SUCCESS, null, "1 row", "id\n1\n", false, 1, null, 42);
  }

  private void acknowledgeSends() {
    when(kafkaTemplate.send(eq(REQUEST_TOPIC), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));
  }

  private ConsumerRecord<String, String> record(Object result) {
    return new ConsumerRecord<>(
        RESULT_TOPIC, 0, 0L, "key", objectMapper.writeValueAsString(result));
  }

  private Future<ConnectionTestResult> testInBackground(ConnectionTestRequest request) {
    return CompletableFuture.supplyAsync(() -> client.test(request));
  }

  @Test
  @DisplayName("test before the result partitions are assigned is rejected without a send")
  void test_beforePartitionsAreAssigned_isRejected() {
    ConnectionTestClient unassigned = newClient();

    assertThatThrownBy(() -> unassigned.test(request(Duration.ofSeconds(10))))
        .isInstanceOf(ExternalSystemRejectionException.class);
    verifyNoInteractions(kafkaTemplate);
  }

  @Test
  @DisplayName("an empty assignment leaves the client not ready")
  void test_afterEmptyAssignment_isStillRejected() {
    ConnectionTestClient unassigned = newClient();
    unassigned.onPartitionsAssigned(Map.of(), mock(ConsumerSeekCallback.class));

    assertThat(unassigned.isReady()).isFalse();
    assertThatThrownBy(() -> unassigned.test(request(Duration.ofSeconds(10))))
        .isInstanceOf(ExternalSystemRejectionException.class);
  }

  @Test
  @DisplayName("test returns the result that arrives for the request")
  void test_whenTheResultArrives_returnsIt() throws Exception {
    acknowledgeSends();
    ConnectionTestRequest request = request(Duration.ofSeconds(10));

    Future<ConnectionTestResult> answer = testInBackground(request);
    await().atMost(Duration.ofSeconds(5)).until(() -> client.pendingCount() == 1);
    client.handleResult(record(success(request.requestId())));

    assertThat(answer.get(5, TimeUnit.SECONDS)).isEqualTo(success(request.requestId()));
    assertThat(client.pendingCount()).isZero();
  }

  @Test
  @DisplayName("test sends the request keyed by its id, with expiresAt as an ISO-8601 string")
  void test_sendsTheRequestKeyedByItsIdWithExpiresAtAsString() throws Exception {
    ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
    when(kafkaTemplate.send(eq(REQUEST_TOPIC), anyString(), json.capture()))
        .thenReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));
    ConnectionTestRequest request =
        new ConnectionTestRequest(
            UUID.randomUUID().toString(),
            Instant.now().plusSeconds(10).truncatedTo(ChronoUnit.MILLIS),
            Map.of("connectionType", "http", "baseUrl", "https://api.example.org"),
            "stations",
            false);

    Future<ConnectionTestResult> answer = testInBackground(request);
    await().atMost(Duration.ofSeconds(5)).until(() -> client.pendingCount() == 1);
    client.handleResult(record(success(request.requestId())));
    answer.get(5, TimeUnit.SECONDS);

    verify(kafkaTemplate).send(eq(REQUEST_TOPIC), eq(request.requestId()), anyString());
    JsonNode sent = objectMapper.readTree(json.getValue());
    assertThat(sent.path("expiresAt").isString()).isTrue();
    assertThat(Instant.parse(sent.path("expiresAt").asString())).isEqualTo(request.expiresAt());
    assertThat(sent.path("requestId").asString()).isEqualTo(request.requestId());
    assertThat(sent.path("apiCall").asString()).isEqualTo("stations");
    assertThat(sent.at("/connector/baseUrl").asString()).isEqualTo("https://api.example.org");
  }

  @Test
  @DisplayName("test without a result before the request expires times out")
  void test_withoutResultBeforeExpiry_timesOut() {
    acknowledgeSends();

    assertThatThrownBy(() -> client.test(request(Duration.ofMillis(200))))
        .isInstanceOf(ExternalSystemTimeoutException.class);
    assertThat(client.pendingCount()).isZero();
  }

  @Test
  @DisplayName("test whose send fails is rejected")
  void test_whenTheSendFails_isRejected() {
    when(kafkaTemplate.send(eq(REQUEST_TOPIC), anyString(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

    assertThatThrownBy(() -> client.test(request(Duration.ofSeconds(10))))
        .isInstanceOf(ExternalSystemRejectionException.class);
    assertThat(client.pendingCount()).isZero();
  }

  @Test
  @DisplayName("test whose send blocks on the broker gives up when the request expires")
  void test_whenTheSendBlocks_timesOutWithTheRequest() {
    when(kafkaTemplate.send(eq(REQUEST_TOPIC), anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              // Stands in for a producer waiting for metadata of an unreachable broker.
              Thread.sleep(5000);
              return CompletableFuture.completedFuture(null);
            });
    long started = System.nanoTime();

    assertThatThrownBy(() -> client.test(request(Duration.ofMillis(300))))
        .isInstanceOf(ExternalSystemTimeoutException.class);
    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    assertThat(client.pendingCount()).isZero();
  }

  @Test
  @DisplayName("test whose send throws is rejected")
  void test_whenTheSendThrows_isRejected() {
    when(kafkaTemplate.send(eq(REQUEST_TOPIC), anyString(), anyString()))
        .thenThrow(new KafkaException("record too large"));

    assertThatThrownBy(() -> client.test(request(Duration.ofSeconds(10))))
        .isInstanceOf(ExternalSystemRejectionException.class);
    assertThat(client.pendingCount()).isZero();
  }

  @Test
  @DisplayName("handleResult ignores a result for another request, which keeps waiting")
  void handleResult_forAnotherRequest_leavesTheWaitingRequestOpen() throws Exception {
    acknowledgeSends();
    ConnectionTestRequest request = request(Duration.ofSeconds(10));
    Future<ConnectionTestResult> answer = testInBackground(request);
    await().atMost(Duration.ofSeconds(5)).until(() -> client.pendingCount() == 1);

    client.handleResult(record(success(UUID.randomUUID().toString())));

    assertThatThrownBy(() -> answer.get(300, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);
    assertThat(client.pendingCount()).isEqualTo(1);

    client.handleResult(record(success(request.requestId())));

    assertThat(answer.get(5, TimeUnit.SECONDS)).isEqualTo(success(request.requestId()));
  }

  @Test
  @DisplayName("handleResult drops an unreadable record and a record without a value")
  void handleResult_withUnreadableRecord_dropsIt() throws Exception {
    acknowledgeSends();
    ConnectionTestRequest request = request(Duration.ofSeconds(10));
    Future<ConnectionTestResult> answer = testInBackground(request);
    await().atMost(Duration.ofSeconds(5)).until(() -> client.pendingCount() == 1);

    client.handleResult(new ConsumerRecord<>(RESULT_TOPIC, 0, 0L, "key", "not json"));
    client.handleResult(new ConsumerRecord<>(RESULT_TOPIC, 0, 1L, "key", null));
    client.handleResult(record(Map.of("outcome", "SUCCESS")));
    client.handleResult(record(success(request.requestId())));

    assertThat(answer.get(5, TimeUnit.SECONDS).outcome()).isEqualTo(Outcome.SUCCESS);
  }

  @Test
  @DisplayName("an unreadable result for a waiting request fails it at once")
  void handleResult_withUnreadableResultOfAWaitingRequest_failsItAtOnce() throws Exception {
    acknowledgeSends();
    ConnectionTestRequest request = request(Duration.ofSeconds(10));
    Future<ConnectionTestResult> answer = testInBackground(request);
    await().atMost(Duration.ofSeconds(5)).until(() -> client.pendingCount() == 1);

    client.handleResult(
        new ConsumerRecord<>(
            RESULT_TOPIC,
            0,
            0L,
            request.requestId(),
            "{\"requestId\":\""
                + request.requestId()
                + "\",\"outcome\":\"FAILURE\",\"reason\":\"FROM_A_NEWER_SERVICE\"}"));

    assertThatThrownBy(() -> answer.get(2, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(ExternalSystemRejectionException.class);
  }

  @Test
  @DisplayName("handleResult completes a failure result as it arrives")
  void handleResult_withFailure_completesTheRequestWithIt() throws Exception {
    acknowledgeSends();
    ConnectionTestRequest request = request(Duration.ofSeconds(10));
    ConnectionTestResult failure =
        ConnectionTestResult.failure(request.requestId(), Reason.SOURCE_ERROR, "refused", 7);

    Future<ConnectionTestResult> answer = testInBackground(request);
    await().atMost(Duration.ofSeconds(5)).until(() -> client.pendingCount() == 1);
    client.handleResult(record(failure));

    assertThat(answer.get(5, TimeUnit.SECONDS)).isEqualTo(failure);
  }

  @Test
  @DisplayName("every client reads results under a consumer group of its own")
  void groupId_isUniquePerInstance() {
    ConnectionTestClient other = newClient();

    assertThat(client.getGroupId()).startsWith("portal-connection-test-");
    assertThat(other.getGroupId()).startsWith("portal-connection-test-");
    assertThat(client.getGroupId()).isNotEqualTo(other.getGroupId());
    assertThat(client.getResultTopic()).isEqualTo(RESULT_TOPIC);
  }
}
