package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.PipelineRuntimeSource;
import de.civitascore.portal.model.embedded.PipelineRuntimeState;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.PipelineRuntimeStatus;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.repository.PipelineRuntimeStatusRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineRuntimeStatusService {
  // Redaction mirrors the config-adapter PipelineMessageSanitizer (producer side). The values
  // arriving here are already sanitized there; this pass is defense-in-depth for the persisted
  // value users see, and additionally strips scheme-less host:port which neither side caught
  // before. Kept in sync by hand because the two modules share no code.
  private static final Pattern URI = Pattern.compile("(?i)\\b[a-z][a-z0-9+.-]*://[^\\s\"'<>]+");
  private static final Pattern CREDENTIAL =
      Pattern.compile(
          "(?i)\\b(password|token|credential|secret)\\b\\s*[:=]\\s*"
              + "(?:\"[^\"]*\"|'[^']*'|[^\\s,;}\\]]+)");
  private static final Pattern BARE_HOST =
      Pattern.compile(
          "(?<![\\w.])(?:[a-z0-9-]+\\.)+[a-z0-9-]+:\\d{2,5}\\b", Pattern.CASE_INSENSITIVE);

  private final PipelineRepository pipelineRepository;
  private final PipelineRuntimeStatusRepository statusRepository;

  @Transactional
  public void apply(
      UUID pipelineId,
      PipelineRuntimeState state,
      PipelineRuntimeSource source,
      String message,
      String stacktrace,
      Instant occurredAt,
      UUID correlationId,
      UUID eventId) {
    Pipeline pipeline = pipelineRepository.findById(pipelineId).orElse(null);
    if (pipeline == null) {
      // An event for an unknown/already-deleted pipeline is expected under out-of-order or stale
      // delivery; drop it silently rather than fail the consumer.
      log.debug("Ignoring pipeline status for unknown pipeline {}", pipelineId);
      return;
    }
    PipelineRuntimeStatus status = pipeline.getRuntimeStatus();
    if (status == null) {
      status = new PipelineRuntimeStatus();
      status.setPipeline(pipeline);
      // Leave the shared primary key unset so @MapsId derives it from the pipeline during persist.
      // Setting it here makes Spring Data choose merge(), which fails with Hibernate's one-to-one
      // shared-key mapping because the transient status still has no resolved identifier.
    }
    if (eventId != null && eventId.equals(status.getLastEventId())) {
      return;
    }
    status.setState(state);
    status.setSource(source);
    status.setMessage(sanitize(message));
    status.setSanitizedStacktrace(sanitize(stacktrace));
    status.setOccurredAt(occurredAt == null ? Instant.now() : occurredAt);
    status.setCorrelationId(correlationId);
    status.setLastEventId(eventId);
    statusRepository.save(status);
  }

  /**
   * Records a successful deployment for the pipelines a saga reports as deployed. This is the
   * success counterpart to the {@code ERROR}/{@code DEPLOYMENT} status written when a saga fails —
   * without it the status of a failed publication would outlive the fix that resolved it.
   *
   * @param pipelineIds the pipeline ids reported by the completed saga; unknown or malformed ids
   *     are skipped
   */
  @Transactional
  public void markDeploymentSucceeded(List<String> pipelineIds) {
    if (pipelineIds == null) {
      return;
    }
    for (String pipelineId : pipelineIds) {
      UUID id;
      try {
        id = UUID.fromString(pipelineId);
      } catch (IllegalArgumentException e) {
        log.warn("Ignoring malformed pipeline id in saga result: {}", Encode.forJava(pipelineId));
        continue;
      }
      apply(
          id,
          PipelineRuntimeState.OK,
          PipelineRuntimeSource.DEPLOYMENT,
          null,
          null,
          null,
          null,
          null);
    }
  }

  private static String sanitize(String value) {
    if (value == null) {
      return null;
    }
    String withoutUris = URI.matcher(value).replaceAll("[redacted-url]");
    String withoutHosts = BARE_HOST.matcher(withoutUris).replaceAll("[redacted-host]");
    return CREDENTIAL.matcher(withoutHosts).replaceAll("$1=[redacted]");
  }
}
