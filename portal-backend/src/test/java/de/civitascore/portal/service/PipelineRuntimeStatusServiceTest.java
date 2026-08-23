package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.PipelineRuntimeSource;
import de.civitascore.portal.model.embedded.PipelineRuntimeState;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.PipelineRuntimeStatus;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.repository.PipelineRuntimeStatusRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PipelineRuntimeStatusService")
class PipelineRuntimeStatusServiceTest {

  @Mock private PipelineRepository pipelineRepository;
  @Mock private PipelineRuntimeStatusRepository statusRepository;

  private PipelineRuntimeStatusService service;

  @BeforeEach
  void setUp() {
    service = new PipelineRuntimeStatusService(pipelineRepository, statusRepository);
  }

  private Pipeline pipelineWithStatus(UUID id) {
    Pipeline pipeline = new Pipeline();
    when(pipelineRepository.findById(id)).thenReturn(Optional.of(pipeline));
    return pipeline;
  }

  private PipelineRuntimeStatus applyAndCapture(UUID id, String message, String stacktrace) {
    service.apply(
        id,
        PipelineRuntimeState.ERROR,
        PipelineRuntimeSource.RUNTIME,
        message,
        stacktrace,
        Instant.now(),
        null,
        UUID.randomUUID());
    ArgumentCaptor<PipelineRuntimeStatus> captor =
        ArgumentCaptor.forClass(PipelineRuntimeStatus.class);
    verify(statusRepository).save(captor.capture());
    return captor.getValue();
  }

  @Nested
  @DisplayName("sanitization")
  class Sanitization {

    @Test
    @DisplayName("redacts URIs of any scheme, including jdbc and tcp")
    void redactsUris() {
      UUID id = UUID.randomUUID();
      pipelineWithStatus(id);

      PipelineRuntimeStatus saved =
          applyAndCapture(
              id,
              "connect failed jdbc:postgresql://db-host:5432/core",
              "tcp://broker:1883 unreachable; see https://nifi.internal/logs");

      assertThat(saved.getMessage()).doesNotContain("db-host", "jdbc:postgresql");
      assertThat(saved.getSanitizedStacktrace())
          .doesNotContain("broker", "nifi.internal")
          .contains("[redacted-url]");
    }

    @Test
    @DisplayName("redacts scheme-less host:port that no URI pattern would catch")
    void redactsBareHostPort() {
      UUID id = UUID.randomUUID();
      pipelineWithStatus(id);

      PipelineRuntimeStatus saved =
          applyAndCapture(id, "Connection refused: nifi-node-1.svc:8443", null);

      assertThat(saved.getMessage()).doesNotContain("nifi-node-1.svc:8443");
    }

    @Test
    @DisplayName("redacts credential assignments")
    void redactsCredentials() {
      UUID id = UUID.randomUUID();
      pipelineWithStatus(id);

      PipelineRuntimeStatus saved =
          applyAndCapture(id, "auth failed password=hunter2 token=abc123", null);

      assertThat(saved.getMessage()).doesNotContain("hunter2", "abc123");
    }
  }

  @Nested
  @DisplayName("idempotency")
  class Idempotency {

    @Test
    @DisplayName("skips an event whose id equals the last applied event id")
    void skipsDuplicateEvent() {
      UUID id = UUID.randomUUID();
      UUID eventId = UUID.randomUUID();
      Pipeline pipeline = pipelineWithStatus(id);
      PipelineRuntimeStatus existing = new PipelineRuntimeStatus();
      existing.setLastEventId(eventId);
      pipeline.setRuntimeStatus(existing);

      service.apply(
          id,
          PipelineRuntimeState.ERROR,
          PipelineRuntimeSource.RUNTIME,
          "again",
          null,
          Instant.now(),
          null,
          eventId);

      verify(statusRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("markDeploymentSucceeded()")
  class MarkDeploymentSucceeded {

    @Test
    @DisplayName("supersedes the status of a failed deployment once a later one succeeds")
    void clearsPreviousDeploymentError() {
      UUID id = UUID.randomUUID();
      Pipeline pipeline = pipelineWithStatus(id);
      PipelineRuntimeStatus existing = new PipelineRuntimeStatus();
      existing.setState(PipelineRuntimeState.ERROR);
      existing.setSource(PipelineRuntimeSource.DEPLOYMENT);
      existing.setMessage("deploy failed: processor invalid");
      existing.setSanitizedStacktrace("stack");
      existing.setLastEventId(UUID.randomUUID());
      pipeline.setRuntimeStatus(existing);

      service.markDeploymentSucceeded(List.of(id.toString()));

      ArgumentCaptor<PipelineRuntimeStatus> captor =
          ArgumentCaptor.forClass(PipelineRuntimeStatus.class);
      verify(statusRepository).save(captor.capture());
      PipelineRuntimeStatus saved = captor.getValue();
      assertThat(saved.getState()).isEqualTo(PipelineRuntimeState.OK);
      assertThat(saved.getSource()).isEqualTo(PipelineRuntimeSource.DEPLOYMENT);
      assertThat(saved.getMessage()).isNull();
      assertThat(saved.getSanitizedStacktrace()).isNull();
      assertThat(saved.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("records a status for every reported pipeline")
    void appliesToAllReportedPipelines() {
      UUID first = UUID.randomUUID();
      UUID second = UUID.randomUUID();
      pipelineWithStatus(first);
      pipelineWithStatus(second);

      service.markDeploymentSucceeded(List.of(first.toString(), second.toString()));

      verify(statusRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("skips a malformed pipeline id without failing the remaining ones")
    void skipsMalformedId() {
      UUID valid = UUID.randomUUID();
      pipelineWithStatus(valid);

      service.markDeploymentSucceeded(List.of("not-a-uuid", valid.toString()));

      verify(statusRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("does nothing when the saga reported no pipelines")
    void ignoresNull() {
      service.markDeploymentSucceeded(null);

      verify(statusRepository, never()).save(any());
    }
  }

  @Test
  @DisplayName("silently ignores an event for an unknown pipeline")
  void ignoresUnknownPipeline() {
    UUID id = UUID.randomUUID();
    when(pipelineRepository.findById(id)).thenReturn(Optional.empty());

    service.apply(
        id,
        PipelineRuntimeState.ERROR,
        PipelineRuntimeSource.RUNTIME,
        "msg",
        null,
        Instant.now(),
        null,
        UUID.randomUUID());

    verify(statusRepository, never()).save(any());
  }
}
