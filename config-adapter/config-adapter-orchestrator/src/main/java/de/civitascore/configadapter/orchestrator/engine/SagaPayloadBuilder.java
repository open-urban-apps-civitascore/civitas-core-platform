/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.orchestrator.engine;

import de.civitascore.configadapter.model.dataset.NamedApiHelper;
import de.civitascore.configadapter.model.saga.SagaContext;
import de.civitascore.configadapter.model.saga.SagaStep;
import de.civitascore.configadapter.model.saga.SagaStepStatus;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds payloads and aggregates results for saga step commands. Extracted from {@link
 * SagaStateMachine} to keep the state machine focused on state transitions.
 */
final class SagaPayloadBuilder {

  private SagaPayloadBuilder() {}

  /**
   * Build the payload for a forward step command. Returns the trigger payload augmented with
   * previous step results, adapter-specific field mappings, and saga envelope fields.
   */
  static Map<String, Object> buildStepPayload(SagaContext context, SagaStepDefinition stepDef) {
    var payload = new HashMap<>(context.triggerPayload());
    for (SagaStep step : context.steps()) {
      if (step.status() == SagaStepStatus.SUCCESS && step.result() != null) {
        payload.putAll(step.result());
      }
    }

    applyAdapterMappings(payload, stepDef);

    payload.put("sagaId", context.sagaId());
    payload.put("datasetId", context.datasetId());
    payload.put("_operation", stepDef.operation());
    payload.put("_stepId", stepDef.stepId());
    return Map.copyOf(payload);
  }

  /** Maps inter-step field names that differ between adapter handlers. */
  private static void applyAdapterMappings(
      Map<String, Object> payload, SagaStepDefinition stepDef) {
    switch (stepDef.adapter()) {
      case "apisix" -> {
        // APISIX handler expects "upstreamUrl", FROST result provides "baseUrl"
        if (payload.containsKey("baseUrl") && !payload.containsKey("upstreamUrl")) {
          payload.put("upstreamUrl", payload.get("baseUrl"));
        }
      }
      case "redpanda" -> {
        // Redpanda handler expects "targetUrl", FROST result provides "baseUrl"
        if (payload.containsKey("baseUrl") && !payload.containsKey("targetUrl")) {
          payload.put("targetUrl", payload.get("baseUrl"));
        }
      }
      default -> {}
    }
  }

  /** Build the compensation payload from the step's stored compensation data. */
  static Map<String, Object> buildCompensationPayload(SagaContext context, SagaStep step) {
    var payload = new HashMap<String, Object>();
    if (step.compensationData() != null) {
      payload.putAll(step.compensationData());
    }
    if (step.result() != null) {
      payload.putAll(step.result());
    }
    payload.put("sagaId", context.sagaId());
    payload.put("datasetId", context.datasetId());
    payload.put("_operation", step.operation());
    payload.put("_stepId", step.stepId());
    return Map.copyOf(payload);
  }

  /** Aggregate results from all successful steps for the final CompleteSaga action. */
  static Map<String, Object> aggregateResults(SagaContext context) {
    var results = new HashMap<String, Object>();
    results.put("sagaId", context.sagaId());
    results.put("datasetId", context.datasetId());
    for (SagaStep step : context.steps()) {
      if (step.status() == SagaStepStatus.SUCCESS && step.result() != null) {
        results.putAll(step.result());
      }
    }

    Map<String, String> routeIds = resolveRouteIds(context);
    if (!routeIds.isEmpty()) {
      results.put("routeIds", routeIds);
    }

    return Map.copyOf(results);
  }

  /**
   * Resolve the slug-keyed route ID map for the saga. Stub implementation pending the real APISIX
   * named-API handler:
   *
   * <ul>
   *   <li>For each entry in the trigger's {@code namedApis[]}, reuse an existing route ID under the
   *       trigger's {@code routeIds[slug]} (UPDATE/DELETE flows).
   *   <li>Otherwise, derive a deterministic UUID via {@link NamedApiHelper#derive}.
   * </ul>
   */
  private static Map<String, String> resolveRouteIds(SagaContext context) {
    var trigger = context.triggerPayload();
    Object namedApisRaw = trigger.get("namedApis");
    if (!(namedApisRaw instanceof List<?> namedApisList) || namedApisList.isEmpty()) {
      return Map.of();
    }

    Map<String, String> existing = readStringMap(trigger.get("routeIds"));
    var resolved = new LinkedHashMap<String, String>();
    String datasetId = context.datasetId();
    for (Object entry : namedApisList) {
      if (!(entry instanceof Map<?, ?> map)) {
        continue;
      }
      Object slugValue = map.get("slug");
      if (!(slugValue instanceof String slug) || slug.isBlank()) {
        continue;
      }
      String routeId = existing.get(slug);
      if (routeId == null || routeId.isBlank()) {
        routeId = NamedApiHelper.derive(datasetId, slug);
      }
      resolved.put(slug, routeId);
    }
    return Map.copyOf(resolved);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> readStringMap(Object value) {
    if (value instanceof Map<?, ?> map) {
      return (Map<String, String>) map;
    }
    return Map.of();
  }

  /** Collect stale and cleaned resources from compensation results. */
  static void collectCompensationResults(
      SagaContext context,
      List<SagaAction.StaleResource> stale,
      List<SagaAction.CleanedResource> cleaned) {
    for (SagaStep step : context.steps()) {
      String resourceId = extractResourceId(step);
      if (step.status() == SagaStepStatus.COMPENSATION_FAILED) {
        stale.add(new SagaAction.StaleResource(step.adapter(), resourceId, step.error()));
      } else if (step.status() == SagaStepStatus.COMPENSATED) {
        cleaned.add(new SagaAction.CleanedResource(step.adapter(), resourceId));
      }
    }
  }

  /** Collect stale and deleted resources from delete execution results. */
  static void collectDeleteResults(
      SagaContext context,
      List<SagaAction.StaleResource> stale,
      List<SagaAction.CleanedResource> cleaned) {
    for (SagaStep step : context.steps()) {
      if (step.status() == SagaStepStatus.SKIPPED) {
        continue;
      }
      String resourceId = extractResourceId(step);
      if (step.status() == SagaStepStatus.FAILED) {
        stale.add(new SagaAction.StaleResource(step.adapter(), resourceId, step.error()));
      } else if (step.status() == SagaStepStatus.SUCCESS) {
        cleaned.add(new SagaAction.CleanedResource(step.adapter(), resourceId));
      }
    }
  }

  /** Extract a representative resource ID from a step's result data. */
  static String extractResourceId(SagaStep step) {
    if (step.result() == null || step.result().isEmpty()) {
      return step.stepId();
    }
    for (String key : List.of("projectId", "routeId", "serviceId", "pipelineIds")) {
      Object value = step.result().get(key);
      if (value != null) {
        return value.toString();
      }
    }
    return step.stepId();
  }
}
