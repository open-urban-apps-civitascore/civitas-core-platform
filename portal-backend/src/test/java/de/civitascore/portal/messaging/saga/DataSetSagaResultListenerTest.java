package de.civitascore.portal.messaging.saga;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.civitascore.portal.service.DataSetService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataSetSagaResultListener Tests")
class DataSetSagaResultListenerTest {

  @Mock private DataSetService dataSetService;

  private final ObjectMapper objectMapper = new JsonMapper();
  private DataSetSagaResultListener listener;

  @BeforeEach
  void setUp() {
    listener = new DataSetSagaResultListener(dataSetService, objectMapper);
  }

  private String completedPayload(UUID datasetId) throws Exception {
    return objectMapper.writeValueAsString(
        Map.of(
            "type",
            "SAGA_COMPLETED",
            "result",
            Map.of(
                "datasetId", datasetId.toString(),
                "projectId", "proj-1",
                "baseUrl", "http://frost",
                "routeId", "route-1",
                "serviceId", "svc-1",
                "publicUrl", "http://public",
                "pipelineIds", List.of("pipe-1"))));
  }

  private String failedPayload(String datasetId, String step, String error, boolean compensated)
      throws Exception {
    return objectMapper.writeValueAsString(
        Map.of(
            "type", "SAGA_FAILED",
            "datasetId", datasetId,
            "failedStep", step,
            "error", error,
            "compensated", compensated));
  }

  @Nested
  @DisplayName("handleSagaResult() routing")
  class HandleSagaResultTests {

    @Test
    @DisplayName("ignores message with null type")
    void ignoresNullType() throws Exception {
      String payload = objectMapper.writeValueAsString(Map.of("datasetId", UUID.randomUUID()));

      listener.handleSagaResult(payload);

      verifyNoServiceCalls();
    }

    @Test
    @DisplayName("ignores message with unknown type")
    void ignoresUnknownType() throws Exception {
      String payload = objectMapper.writeValueAsString(Map.of("type", "UNKNOWN_TYPE"));

      listener.handleSagaResult(payload);

      verifyNoServiceCalls();
    }

    @Test
    @DisplayName("throws IllegalStateException for malformed JSON so DLQ handler picks it up")
    void throwsOnMalformedJson() {
      assertThatThrownBy(() -> listener.handleSagaResult("not-json"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Saga result deserialization failed");
    }
  }

  @Nested
  @DisplayName("SAGA_COMPLETED handling")
  class SagaCompletedTests {

    @Test
    @DisplayName("dispatches to service with parsed payload")
    void dispatchesCompletedToService() throws Exception {
      UUID datasetId = UUID.randomUUID();

      listener.handleSagaResult(completedPayload(datasetId));

      verify(dataSetService)
          .handleSagaCompleted(
              eq(datasetId),
              eq(
                  new SagaResultPayload(
                      datasetId.toString(),
                      "proj-1",
                      "http://frost",
                      "route-1",
                      "svc-1",
                      "http://public",
                      List.of("pipe-1"),
                      null,
                      null,
                      null)));
    }

    @Test
    @DisplayName("skips when result object is missing")
    void skipsMissingResult() throws Exception {
      String payload =
          objectMapper.writeValueAsString(
              Map.of("type", "SAGA_COMPLETED", "datasetId", UUID.randomUUID().toString()));

      listener.handleSagaResult(payload);

      verify(dataSetService, never()).handleSagaCompleted(any(), any());
    }

    @Test
    @DisplayName("skips when result has null datasetId")
    void skipsNullDatasetId() throws Exception {
      String payload =
          objectMapper.writeValueAsString(
              Map.of("type", "SAGA_COMPLETED", "result", Map.of("projectId", "proj-1")));

      listener.handleSagaResult(payload);

      verify(dataSetService, never()).handleSagaCompleted(any(), any());
    }

    @Test
    @DisplayName("skips when result has invalid UUID")
    void skipsInvalidUuid() throws Exception {
      String payload =
          objectMapper.writeValueAsString(
              Map.of("type", "SAGA_COMPLETED", "result", Map.of("datasetId", "not-a-uuid")));

      listener.handleSagaResult(payload);

      verify(dataSetService, never()).handleSagaCompleted(any(), any());
    }
  }

  @Nested
  @DisplayName("SAGA_FAILED handling")
  class SagaFailedTests {

    @Test
    @DisplayName("dispatches to service with failure details")
    void dispatchesFailedToService() throws Exception {
      UUID datasetId = UUID.randomUUID();

      listener.handleSagaResult(
          failedPayload(datasetId.toString(), "FROST", "connection refused", true));

      verify(dataSetService)
          .handleSagaFailed(eq(datasetId), eq("FROST"), eq("connection refused"), eq(true));
    }

    @Test
    @DisplayName("treats null compensated as false")
    void nullCompensatedIsFalse() throws Exception {
      UUID datasetId = UUID.randomUUID();
      // Build manually to have null-like compensated (omit the key)
      String payload =
          objectMapper.writeValueAsString(
              Map.of(
                  "type", "SAGA_FAILED",
                  "datasetId", datasetId.toString(),
                  "failedStep", "APISIX",
                  "error", "timeout"));

      listener.handleSagaResult(payload);

      verify(dataSetService)
          .handleSagaFailed(eq(datasetId), eq("APISIX"), eq("timeout"), eq(false));
    }

    @Test
    @DisplayName("skips when datasetId is missing")
    void skipsMissingDatasetId() throws Exception {
      String payload =
          objectMapper.writeValueAsString(
              Map.of("type", "SAGA_FAILED", "failedStep", "FROST", "error", "error"));

      listener.handleSagaResult(payload);

      verifyNoServiceCalls();
    }

    @Test
    @DisplayName("skips when datasetId is invalid UUID")
    void skipsInvalidUuid() throws Exception {
      listener.handleSagaResult(failedPayload("bad-uuid", "FROST", "error", false));

      verifyNoServiceCalls();
    }
  }

  private void verifyNoServiceCalls() {
    verify(dataSetService, never()).handleSagaCompleted(any(), any());
    verify(dataSetService, never()).handleSagaFailed(any(), any(), any(), eq(false));
    verify(dataSetService, never()).handleSagaFailed(any(), any(), any(), eq(true));
  }
}
