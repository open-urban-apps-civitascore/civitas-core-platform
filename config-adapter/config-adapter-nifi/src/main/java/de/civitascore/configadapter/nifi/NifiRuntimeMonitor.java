/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi;

import com.fasterxml.jackson.databind.JsonNode;
import de.civitascore.configadapter.adapter.PipelineStatusPublisher;
import de.civitascore.configadapter.exception.AdapterException;
import de.civitascore.configadapter.nifi.rest.NifiRestClient;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Polls NiFi runtime state and emits only state transitions for registered pipelines. */
final class NifiRuntimeMonitor implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(NifiRuntimeMonitor.class);
  // Consecutive healthy polls required before a recovered pipeline is reported OK again, so a
  // flapping pipeline does not emit an OK/ERROR storm.
  private static final int HEALTHY_ROUNDS_FOR_RECOVERY = 3;
  private final NifiRestClient client;
  private final ScheduledExecutorService executor;
  private final Map<String, Pipeline> pipelines = new ConcurrentHashMap<>();
  private volatile PipelineStatusPublisher publisher;

  NifiRuntimeMonitor(NifiRestClient client, long intervalMs) {
    this.client = client;
    this.executor =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread thread = new Thread(r, "nifi-runtime-monitor");
              thread.setDaemon(true);
              return thread;
            });
    executor.scheduleWithFixedDelay(this::poll, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
  }

  void setPublisher(PipelineStatusPublisher publisher) {
    this.publisher = publisher;
  }

  void register(String pipelineId, String datasetId, String processGroupId) {
    pipelines.put(pipelineId, new Pipeline(pipelineId, datasetId, processGroupId));
  }

  void unregister(String pipelineId) {
    pipelines.remove(pipelineId);
  }

  private void poll() {
    JsonNode bulletins;
    try {
      bulletins = client.readBulletins();
    } catch (AdapterException | IllegalStateException e) {
      LOG.warn("Could not read NiFi bulletins: {}", Encode.forJava(e.getMessage()));
      return;
    }
    discoverPipelines();
    pipelines.values().forEach(pipeline -> inspectPipeline(pipeline, bulletins));
  }

  private void discoverPipelines() {
    try {
      for (NifiRestClient.ManagedProcessGroup group : client.listManagedProcessGroups()) {
        pipelines.compute(
            group.pipelineId(),
            (pipelineId, pipeline) -> {
              if (pipeline == null) {
                return new Pipeline(pipelineId, null, group.processGroupId());
              }
              pipeline.processGroupId(group.processGroupId());
              return pipeline;
            });
      }
    } catch (AdapterException | IllegalStateException e) {
      LOG.warn(
          "Could not discover NiFi pipeline process groups: {}", Encode.forJava(e.getMessage()));
    }
  }

  private void inspectPipeline(Pipeline pipeline, JsonNode bulletins) {
    try {
      NifiRestClient.RuntimeStatus status =
          client.readRuntimeStatus(pipeline.processGroupId(), bulletins);
      if (shouldPublish(pipeline, status)) {
        PipelineStatusPublisher currentPublisher = publisher;
        if (currentPublisher != null) {
          currentPublisher.publish(event(pipeline, status));
        }
      }
    } catch (AdapterException | IllegalStateException e) {
      if (e instanceof AdapterException adapterException
          && adapterException.getInternalMessage().contains("HTTP 404")) {
        pipelines.remove(pipeline.pipelineId());
      }
      LOG.warn(
          "Could not inspect NiFi runtime for pipeline {}: {}",
          Encode.forJava(pipeline.pipelineId()),
          Encode.forJava(e.getMessage()));
    }
  }

  /**
   * Decides whether this poll's status is a transition worth publishing, updating the pipeline's
   * debounce state. An ERROR is published as soon as the pipeline leaves the OK state (once, not on
   * every subsequent poll — a persistent failure whose bulletin text varies must not re-fire). A
   * return to OK is only published after {@link #HEALTHY_ROUNDS_FOR_RECOVERY} consecutive healthy
   * polls, so a flapping pipeline does not emit an OK/ERROR storm.
   */
  private static boolean shouldPublish(Pipeline pipeline, NifiRestClient.RuntimeStatus status) {
    if (!status.healthy()) {
      pipeline.healthyStreak(0);
      boolean wasError = pipeline.lastPublishedError();
      pipeline.lastPublishedError(true);
      return !wasError;
    }
    int streak = pipeline.healthyStreak() + 1;
    pipeline.healthyStreak(streak);
    if (pipeline.lastPublishedError() && streak >= HEALTHY_ROUNDS_FOR_RECOVERY) {
      pipeline.lastPublishedError(false);
      return true;
    }
    return false;
  }

  private Map<String, Object> event(Pipeline pipeline, NifiRestClient.RuntimeStatus status) {
    Map<String, Object> event = new HashMap<>();
    event.put("type", "PIPELINE_STATUS_CHANGED");
    event.put("eventId", UUID.randomUUID().toString());
    event.put("datasetId", pipeline.datasetId());
    event.put("pipelineId", pipeline.pipelineId());
    event.put("status", status.healthy() ? "OK" : "ERROR");
    event.put("source", "RUNTIME");
    event.put(
        "message", sanitize(status.message() == null ? "Pipeline is running" : status.message()));
    event.put("stacktrace", sanitize(status.stacktrace() == null ? "" : status.stacktrace()));
    event.put(
        "occurredAt",
        status.occurredAt() == null ? Instant.now().toString() : status.occurredAt().toString());
    event.put("correlationId", null);
    return event;
  }

  private static String sanitize(String value) {
    return PipelineMessageSanitizer.sanitize(value);
  }

  @Override
  public void close() {
    executor.shutdownNow();
  }

  @SuppressWarnings("PMD.DataClass") // Mutable debounce counters are the monitor's dedup state.
  private static final class Pipeline {
    private final String pipelineId;
    private final String datasetId;
    private volatile String processGroupId;
    // Whether the last published event for this pipeline was an ERROR (so a repeated ERROR poll is
    // not re-published), and how many consecutive healthy polls have been seen since (gates the
    // OK recovery event).
    private volatile boolean lastPublishedError;
    private volatile int healthyStreak;

    private Pipeline(String pipelineId, String datasetId, String processGroupId) {
      this.pipelineId = pipelineId;
      this.datasetId = datasetId;
      this.processGroupId = processGroupId;
    }

    String pipelineId() {
      return pipelineId;
    }

    String datasetId() {
      return datasetId;
    }

    String processGroupId() {
      return processGroupId;
    }

    void processGroupId(String value) {
      processGroupId = value;
    }

    boolean lastPublishedError() {
      return lastPublishedError;
    }

    void lastPublishedError(boolean value) {
      lastPublishedError = value;
    }

    int healthyStreak() {
      return healthyStreak;
    }

    void healthyStreak(int value) {
      healthyStreak = value;
    }
  }
}
