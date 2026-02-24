/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.orchestrator.engine;

import de.civitascore.configadapter.model.dataset.Dataset;
import de.civitascore.configadapter.model.saga.SagaContext;
import de.civitascore.configadapter.model.saga.SagaStep;
import de.civitascore.configadapter.model.saga.SagaStepStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds adapter-specific command payloads from the trigger event and previous step results.
 * Kafka-free and JUnit-testable — works purely on Maps.
 *
 * <p>See SAGA-DATASET-USE-CASES.md Sections 4 and 5 for the data flow specification.
 */
public final class DatasetCommandBuilder {

  private DatasetCommandBuilder() {}

  /**
   * Build the forward execution payload for a step. Extracts adapter-specific fields from the
   * trigger payload and enriches with results from previous steps.
   */
  public static Map<String, Object> buildExecutePayload(
      SagaContext context, SagaStepDefinition stepDef) {
    return switch (stepDef.adapter()) {
      case "frost" -> buildFrostPayload(context, stepDef);
      case "apisix" -> buildApisixPayload(context, stepDef);
      case "redpanda" -> buildRedpandaPayload(context, stepDef);
      default -> buildGenericPayload(context, stepDef);
    };
  }

  /**
   * Build the compensation payload for a step. Uses the step's stored result and compensation data.
   */
  public static Map<String, Object> buildCompensatePayload(
      SagaContext context, SagaStep step, SagaStepDefinition stepDef) {
    return switch (stepDef.adapter()) {
      case "frost" -> buildFrostCompensationPayload(context, step, stepDef);
      case "apisix" -> buildApisixCompensationPayload(context, step, stepDef);
      case "redpanda" -> buildRedpandaCompensationPayload(context, step, stepDef);
      default -> buildGenericCompensationPayload(step, stepDef);
    };
  }

  /** Aggregate results from all completed steps into the final saga result. */
  public static Map<String, Object> aggregateSagaResult(SagaContext context) {
    var result = new HashMap<String, Object>();
    result.put("sagaId", context.sagaId());
    result.put("datasetId", context.datasetId());

    for (SagaStep step : context.steps()) {
      if (step.status() == SagaStepStatus.SUCCESS && step.result() != null) {
        result.putAll(step.result());
      }
    }

    // Build properties array for backend persistence
    result.put("properties", buildProperties(context));
    return Map.copyOf(result);
  }

  // ─── FROST Adapter ──────────────────────────────────────────────────────────

  private static Map<String, Object> buildFrostPayload(
      SagaContext context, SagaStepDefinition stepDef) {
    var trigger = context.triggerPayload();
    var dataset = context.triggerPayloadAs(Dataset.class);
    var payload = new HashMap<String, Object>();
    payload.put("operation", stepDef.operation());
    payload.put("datasetId", context.datasetId());

    return switch (stepDef.operation()) {
      case "CREATE_PROJECT" -> {
        payload.put("datasetName", dataset.name());
        payload.put("description", trigger.getOrDefault("description", ""));
        yield Map.copyOf(payload);
      }
      case "UPDATE_PROJECT" -> {
        payload.put("projectId", getProperty(trigger, "projectId"));
        payload.put("datasetName", dataset.name());
        payload.put("description", trigger.getOrDefault("description", ""));
        yield Map.copyOf(payload);
      }
      case "DELETE_PROJECT" -> {
        payload.put("projectId", getProperty(trigger, "projectId"));
        yield Map.copyOf(payload);
      }
      default -> Map.copyOf(payload);
    };
  }

  private static Map<String, Object> buildFrostCompensationPayload(
      SagaContext context, SagaStep step, SagaStepDefinition stepDef) {
    var payload = new HashMap<String, Object>();
    payload.put("operation", stepDef.compensationOperation());

    if (step.result() != null && step.result().containsKey("projectId")) {
      payload.put("projectId", step.result().get("projectId"));
    }
    if (step.compensationData() != null) {
      payload.putAll(step.compensationData());
    }
    return Map.copyOf(payload);
  }

  // ─── APISIX Adapter ─────────────────────────────────────────────────────────

  private static Map<String, Object> buildApisixPayload(
      SagaContext context, SagaStepDefinition stepDef) {
    var trigger = context.triggerPayload();
    var dataset = context.triggerPayloadAs(Dataset.class);
    var payload = new HashMap<String, Object>();
    payload.put("operation", stepDef.operation());
    payload.put("datasetId", context.datasetId());

    // Get baseUrl from FROST step result
    String baseUrl = getResultFromStep(context, "frost", "baseUrl");
    if (baseUrl != null) {
      payload.put("upstreamUrl", baseUrl);
    }

    payload.put("openDataAccess", dataset.openDataAccess());

    return switch (stepDef.operation()) {
      case "CREATE_ROUTE" -> Map.copyOf(payload);
      case "UPDATE_ROUTE" -> {
        payload.put("routeId", getProperty(trigger, "routeId"));
        payload.put("serviceId", getProperty(trigger, "serviceId"));
        yield Map.copyOf(payload);
      }
      case "DELETE_ROUTE" -> {
        payload.put("routeId", getProperty(trigger, "routeId"));
        payload.put("serviceId", getProperty(trigger, "serviceId"));
        yield Map.copyOf(payload);
      }
      default -> Map.copyOf(payload);
    };
  }

  private static Map<String, Object> buildApisixCompensationPayload(
      SagaContext context, SagaStep step, SagaStepDefinition stepDef) {
    var payload = new HashMap<String, Object>();
    payload.put("operation", stepDef.compensationOperation());

    if (step.result() != null) {
      if (step.result().containsKey("routeId")) {
        payload.put("routeId", step.result().get("routeId"));
      }
      if (step.result().containsKey("serviceId")) {
        payload.put("serviceId", step.result().get("serviceId"));
      }
    }
    if (step.compensationData() != null) {
      payload.putAll(step.compensationData());
    }
    return Map.copyOf(payload);
  }

  // ─── Redpanda Adapter ───────────────────────────────────────────────────────

  private static Map<String, Object> buildRedpandaPayload(
      SagaContext context, SagaStepDefinition stepDef) {
    var trigger = context.triggerPayload();
    var payload = new HashMap<String, Object>();
    payload.put("operation", stepDef.operation());
    payload.put("datasetId", context.datasetId());

    // Get baseUrl from FROST step result
    String baseUrl = getResultFromStep(context, "frost", "baseUrl");
    if (baseUrl != null) {
      payload.put("targetUrl", baseUrl);
    }

    return switch (stepDef.operation()) {
      case "DEPLOY_PIPELINES", "UPDATE_PIPELINES" -> {
        payload.put("datasources", trigger.getOrDefault("datasources", List.of()));
        payload.put("dataPipelines", trigger.getOrDefault("dataPipelines", List.of()));
        yield Map.copyOf(payload);
      }
      case "DELETE_PIPELINES" -> {
        payload.put("pipelineIds", getProperty(trigger, "pipelineIds"));
        yield Map.copyOf(payload);
      }
      default -> Map.copyOf(payload);
    };
  }

  private static Map<String, Object> buildRedpandaCompensationPayload(
      SagaContext context, SagaStep step, SagaStepDefinition stepDef) {
    var payload = new HashMap<String, Object>();
    payload.put("operation", stepDef.compensationOperation());
    payload.put("datasetId", context.datasetId());

    if (step.result() != null && step.result().containsKey("pipelineIds")) {
      payload.put("pipelineIds", step.result().get("pipelineIds"));
    }
    if (step.compensationData() != null) {
      payload.putAll(step.compensationData());
    }

    // For RESTORE_PIPELINES, also provide targetUrl and datasources
    String baseUrl = getResultFromStep(context, "frost", "baseUrl");
    if (baseUrl != null) {
      payload.put("targetUrl", baseUrl);
    }
    payload.put("datasources", context.triggerPayload().getOrDefault("datasources", List.of()));

    return Map.copyOf(payload);
  }

  // ─── Helpers ────────────────────────────────────────────────────────────────

  private static Map<String, Object> buildGenericPayload(
      SagaContext context, SagaStepDefinition stepDef) {
    var payload = new HashMap<>(context.triggerPayload());
    payload.put("operation", stepDef.operation());
    payload.put("datasetId", context.datasetId());
    // Add previous step results
    for (SagaStep step : context.steps()) {
      if (step.status() == SagaStepStatus.SUCCESS && step.result() != null) {
        payload.putAll(step.result());
      }
    }
    return Map.copyOf(payload);
  }

  private static Map<String, Object> buildGenericCompensationPayload(
      SagaStep step, SagaStepDefinition stepDef) {
    var payload = new HashMap<String, Object>();
    payload.put("operation", stepDef.compensationOperation());
    if (step.result() != null) {
      payload.putAll(step.result());
    }
    if (step.compensationData() != null) {
      payload.putAll(step.compensationData());
    }
    return Map.copyOf(payload);
  }

  /**
   * Extract a property value from the trigger payload's properties array. Properties are stored as
   * {@code List<Map<String, Object>>} where each entry has a single key-value pair.
   */
  private static Object getProperty(Map<String, Object> trigger, String key) {
    Object props = trigger.get("properties");
    if (props instanceof List<?> list) {
      for (Object entry : list) {
        if (entry instanceof Map<?, ?> map && map.containsKey(key)) {
          return map.get(key);
        }
      }
    }
    // Fallback: try direct access (for create triggers that don't have properties)
    return trigger.get(key);
  }

  /** Get a result value from a specific adapter's completed step. */
  private static String getResultFromStep(SagaContext context, String adapter, String key) {
    return context.steps().stream()
        .filter(s -> s.adapter().equals(adapter) && s.status() == SagaStepStatus.SUCCESS)
        .findFirst()
        .map(s -> s.result() != null ? s.result().get(key) : null)
        .map(Object::toString)
        .orElse(null);
  }

  /** Build the properties array from completed step results. */
  private static List<Map<String, Object>> buildProperties(SagaContext context) {
    var props = new ArrayList<Map<String, Object>>();
    for (SagaStep step : context.steps()) {
      if (step.status() != SagaStepStatus.SUCCESS || step.result() == null) {
        continue;
      }
      var result = step.result();
      if (result.containsKey("projectId")) {
        props.add(Map.of("projectId", result.get("projectId")));
      }
      if (result.containsKey("routeId")) {
        props.add(Map.of("routeId", result.get("routeId")));
      }
      if (result.containsKey("serviceId")) {
        props.add(Map.of("serviceId", result.get("serviceId")));
      }
      if (result.containsKey("pipelineIds")) {
        props.add(Map.of("pipelineIds", result.get("pipelineIds")));
      }
    }
    return List.copyOf(props);
  }
}
