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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.model.dataset.NamedApiHelper;
import de.civitascore.configadapter.model.saga.SagaContext;
import de.civitascore.configadapter.model.saga.SagaStatus;
import de.civitascore.configadapter.model.saga.SagaStep;
import de.civitascore.configadapter.model.saga.SagaStepStatus;
import de.civitascore.configadapter.model.saga.SagaType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SagaPayloadBuilderTest {

  private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

  private static SagaStep step(
      String stepId, String adapter, SagaStepStatus status, Map<String, Object> result) {
    return new SagaStep(stepId, adapter, "OP", status, result, Map.of(), null, NOW, NOW);
  }

  private static SagaStep stepWithCompensationData(
      String stepId,
      String adapter,
      SagaStepStatus status,
      Map<String, Object> result,
      Map<String, Object> compensationData) {
    return new SagaStep(stepId, adapter, "OP", status, result, compensationData, null, NOW, NOW);
  }

  private static SagaStep stepWithError(
      String stepId, String adapter, SagaStepStatus status, String error) {
    return new SagaStep(stepId, adapter, "OP", status, Map.of(), Map.of(), error, NOW, NOW);
  }

  private static SagaContext saga(Map<String, Object> triggerPayload, SagaStep... steps) {
    return new SagaContext(
        "saga-1",
        SagaType.DATASET_CREATE,
        "ds-1",
        null,
        SagaStatus.EXECUTING,
        List.of(steps),
        null,
        triggerPayload,
        NOW,
        NOW);
  }

  @Nested
  @DisplayName("buildStepPayload")
  class BuildStepPayload {

    @Test
    @DisplayName("includes trigger payload fields")
    void shouldIncludeTriggerPayload() {
      SagaContext context =
          saga(
              Map.of("datasetName", "Test"),
              step("create-project", "frost", SagaStepStatus.PENDING, Map.of()));

      SagaStepDefinition stepDef =
          SagaStepDefinition.mandatory(
              "create-project", "frost", "CREATE_PROJECT", "DELETE_PROJECT", "exec", "comp");

      Map<String, Object> payload = SagaPayloadBuilder.buildStepPayload(context, stepDef);

      assertEquals("Test", payload.get("datasetName"));
    }

    @Test
    @DisplayName("includes results from previous successful steps")
    void shouldIncludePreviousStepResults() {
      SagaContext context =
          saga(
              Map.of("datasetName", "Test"),
              step("create-project", "frost", SagaStepStatus.SUCCESS, Map.of("projectId", "42")),
              step("create-route", "apisix", SagaStepStatus.PENDING, Map.of()));

      SagaStepDefinition stepDef =
          SagaStepDefinition.mandatory(
              "create-route", "apisix", "CREATE_ROUTE", "DELETE_ROUTE", "exec", "comp");

      Map<String, Object> payload = SagaPayloadBuilder.buildStepPayload(context, stepDef);

      assertEquals("42", payload.get("projectId"));
      assertEquals("Test", payload.get("datasetName"));
    }

    @Test
    @DisplayName("includes saga envelope fields")
    void shouldIncludeEnvelopeFields() {
      SagaContext context =
          saga(Map.of(), step("create-project", "frost", SagaStepStatus.PENDING, Map.of()));

      SagaStepDefinition stepDef =
          SagaStepDefinition.mandatory(
              "create-project", "frost", "CREATE_PROJECT", "DELETE_PROJECT", "exec", "comp");

      Map<String, Object> payload = SagaPayloadBuilder.buildStepPayload(context, stepDef);

      assertEquals("saga-1", payload.get("sagaId"));
      assertEquals("ds-1", payload.get("datasetId"));
      assertEquals("CREATE_PROJECT", payload.get("_operation"));
      assertEquals("create-project", payload.get("_stepId"));
    }

    @Test
    @DisplayName("skips results from non-SUCCESS steps")
    void shouldSkipNonSuccessStepResults() {
      SagaContext context =
          saga(
              Map.of(),
              step("create-project", "frost", SagaStepStatus.FAILED, Map.of("projectId", "42")),
              step("create-route", "apisix", SagaStepStatus.PENDING, Map.of()));

      SagaStepDefinition stepDef =
          SagaStepDefinition.mandatory(
              "create-route", "apisix", "CREATE_ROUTE", "DELETE_ROUTE", "exec", "comp");

      Map<String, Object> payload = SagaPayloadBuilder.buildStepPayload(context, stepDef);

      assertTrue(payload.containsKey("sagaId"));
      assertTrue(!payload.containsKey("projectId"));
    }

    @Test
    @DisplayName("APISIX step: maps baseUrl from FROST result to upstreamUrl")
    void shouldMapBaseUrlToUpstreamUrlForApisixStep() {
      SagaContext context =
          saga(
              Map.of("datasetName", "Test"),
              step(
                  "create-project",
                  "frost",
                  SagaStepStatus.SUCCESS,
                  Map.of("projectId", "42", "baseUrl", "http://frost/Projects(42)")),
              step("create-route", "apisix", SagaStepStatus.PENDING, Map.of()));

      SagaStepDefinition stepDef =
          SagaStepDefinition.mandatory(
              "create-route", "apisix", "CREATE_ROUTE", "DELETE_ROUTE", "exec", "comp");

      Map<String, Object> payload = SagaPayloadBuilder.buildStepPayload(context, stepDef);

      assertEquals("http://frost/Projects(42)", payload.get("upstreamUrl"));
    }

    @Test
    @DisplayName("APISIX step: does not overwrite upstreamUrl when already present")
    void shouldNotOverwriteUpstreamUrlWhenAlreadyPresent() {
      SagaContext context =
          saga(
              Map.of("upstreamUrl", "http://explicit"),
              step(
                  "create-project",
                  "frost",
                  SagaStepStatus.SUCCESS,
                  Map.of("baseUrl", "http://frost/Projects(42)")),
              step("create-route", "apisix", SagaStepStatus.PENDING, Map.of()));

      SagaStepDefinition stepDef =
          SagaStepDefinition.mandatory(
              "create-route", "apisix", "CREATE_ROUTE", "DELETE_ROUTE", "exec", "comp");

      Map<String, Object> payload = SagaPayloadBuilder.buildStepPayload(context, stepDef);

      assertEquals("http://explicit", payload.get("upstreamUrl"));
    }

    @Test
    @DisplayName("Redpanda step: maps baseUrl from FROST result to targetUrl")
    void shouldMapBaseUrlToTargetUrlForRedpandaStep() {
      SagaContext context =
          saga(
              Map.of("datasetName", "Test"),
              step(
                  "create-project",
                  "frost",
                  SagaStepStatus.SUCCESS,
                  Map.of("projectId", "42", "baseUrl", "http://frost/Projects(42)")),
              step("deploy-pipelines", "redpanda", SagaStepStatus.PENDING, Map.of()));

      SagaStepDefinition stepDef =
          SagaStepDefinition.mandatory(
              "deploy-pipelines",
              "redpanda",
              "DEPLOY_PIPELINES",
              "DELETE_PIPELINES",
              "exec",
              "comp");

      Map<String, Object> payload = SagaPayloadBuilder.buildStepPayload(context, stepDef);

      assertEquals("http://frost/Projects(42)", payload.get("targetUrl"));
    }

    @Test
    @DisplayName("Redpanda step: does not overwrite targetUrl when already present")
    void shouldNotOverwriteTargetUrlWhenAlreadyPresent() {
      SagaContext context =
          saga(
              Map.of("targetUrl", "http://explicit"),
              step(
                  "create-project",
                  "frost",
                  SagaStepStatus.SUCCESS,
                  Map.of("baseUrl", "http://frost/Projects(42)")),
              step("deploy-pipelines", "redpanda", SagaStepStatus.PENDING, Map.of()));

      SagaStepDefinition stepDef =
          SagaStepDefinition.mandatory(
              "deploy-pipelines",
              "redpanda",
              "DEPLOY_PIPELINES",
              "DELETE_PIPELINES",
              "exec",
              "comp");

      Map<String, Object> payload = SagaPayloadBuilder.buildStepPayload(context, stepDef);

      assertEquals("http://explicit", payload.get("targetUrl"));
    }

    @Test
    @DisplayName("FROST step: baseUrl not remapped to upstreamUrl or targetUrl")
    void shouldNotRemapBaseUrlForOtherAdapters() {
      SagaContext context =
          saga(
              Map.of("baseUrl", "http://upstream"),
              step("create-project", "frost", SagaStepStatus.PENDING, Map.of()));

      SagaStepDefinition stepDef =
          SagaStepDefinition.mandatory(
              "create-project", "frost", "CREATE_PROJECT", "DELETE_PROJECT", "exec", "comp");

      Map<String, Object> payload = SagaPayloadBuilder.buildStepPayload(context, stepDef);

      assertFalse(payload.containsKey("upstreamUrl"));
      assertFalse(payload.containsKey("targetUrl"));
    }
  }

  @Nested
  @DisplayName("buildCompensationPayload")
  class BuildCompensationPayload {

    @Test
    @DisplayName("includes compensation data and result data")
    void shouldIncludeCompensationAndResultData() {
      SagaStep successStep =
          stepWithCompensationData(
              "create-project",
              "frost",
              SagaStepStatus.SUCCESS,
              Map.of("projectId", "42", "baseUrl", "http://frost/Projects(42)"),
              Map.of("projectId", "42"));
      SagaContext context = saga(Map.of(), successStep);

      Map<String, Object> payload =
          SagaPayloadBuilder.buildCompensationPayload(context, successStep);

      assertEquals("42", payload.get("projectId"));
      assertEquals("http://frost/Projects(42)", payload.get("baseUrl"));
    }

    @Test
    @DisplayName("includes saga envelope fields")
    void shouldIncludeEnvelopeFields() {
      SagaStep successStep = step("create-project", "frost", SagaStepStatus.SUCCESS, Map.of());
      SagaContext context = saga(Map.of(), successStep);

      Map<String, Object> payload =
          SagaPayloadBuilder.buildCompensationPayload(context, successStep);

      assertEquals("saga-1", payload.get("sagaId"));
      assertEquals("ds-1", payload.get("datasetId"));
      assertEquals("OP", payload.get("_operation"));
      assertEquals("create-project", payload.get("_stepId"));
    }

    @Test
    @DisplayName("handles null compensation data and null result")
    void shouldHandleNullCompensationAndResult() {
      SagaStep nullDataStep =
          new SagaStep(
              "create-project", "frost", "OP", SagaStepStatus.SUCCESS, null, null, null, NOW, NOW);
      SagaContext context = saga(Map.of(), nullDataStep);

      Map<String, Object> payload =
          SagaPayloadBuilder.buildCompensationPayload(context, nullDataStep);

      assertEquals("saga-1", payload.get("sagaId"));
      assertEquals(4, payload.size());
    }
  }

  @Nested
  @DisplayName("aggregateResults")
  class AggregateResults {

    @Test
    @DisplayName("aggregates results from all successful steps")
    void shouldAggregateSuccessfulStepResults() {
      SagaContext context =
          saga(
              Map.of(),
              step("create-project", "frost", SagaStepStatus.SUCCESS, Map.of("projectId", "42")),
              step("create-route", "apisix", SagaStepStatus.SUCCESS, Map.of("routeId", "ds-1")));

      Map<String, Object> results = SagaPayloadBuilder.aggregateResults(context);

      assertEquals("saga-1", results.get("sagaId"));
      assertEquals("ds-1", results.get("datasetId"));
      assertEquals("42", results.get("projectId"));
      assertEquals("ds-1", results.get("routeId"));
    }

    @Test
    @DisplayName("skips failed and pending steps")
    void shouldSkipNonSuccessSteps() {
      SagaContext context =
          saga(
              Map.of(),
              step("create-project", "frost", SagaStepStatus.SUCCESS, Map.of("projectId", "42")),
              step(
                  "create-route",
                  "apisix",
                  SagaStepStatus.FAILED,
                  Map.of("routeId", "should-not-appear")));

      Map<String, Object> results = SagaPayloadBuilder.aggregateResults(context);

      assertEquals("42", results.get("projectId"));
      assertTrue(!results.containsKey("routeId"));
    }

    @Test
    @DisplayName("returns only envelope fields when no steps succeeded")
    void shouldReturnEnvelopeOnlyWhenNoSuccess() {
      SagaContext context =
          saga(Map.of(), step("create-project", "frost", SagaStepStatus.PENDING, Map.of()));

      Map<String, Object> results = SagaPayloadBuilder.aggregateResults(context);

      assertEquals(2, results.size());
      assertEquals("saga-1", results.get("sagaId"));
      assertEquals("ds-1", results.get("datasetId"));
    }

    @Test
    @DisplayName("CREATE: derives a deterministic UUID per slug when no incoming routeIds")
    void aggregateResults_createWithNamedApis_shouldSynthesizeRouteIds() {
      SagaContext context =
          saga(
              Map.of(
                  "namedApis",
                  List.of(
                      Map.of("slug", "traffic", "standard", "STA"),
                      Map.of("slug", "weather", "standard", "STA"))));

      Map<String, Object> results = SagaPayloadBuilder.aggregateResults(context);

      @SuppressWarnings("unchecked")
      Map<String, String> routeIds = (Map<String, String>) results.get("routeIds");
      assertEquals(2, routeIds.size());
      assertEquals(NamedApiHelper.derive("ds-1", "traffic"), routeIds.get("traffic"));
      assertEquals(NamedApiHelper.derive("ds-1", "weather"), routeIds.get("weather"));
    }

    @Test
    @DisplayName("UPDATE: preserves existing routeIds for known slugs and synthesizes for new ones")
    void aggregateResults_updateMixedSlugs_shouldPreserveExistingAndSynthesizeNew() {
      var trigger = new HashMap<String, Object>();
      trigger.put(
          "namedApis",
          List.of(
              Map.of("slug", "traffic", "standard", "STA"),
              Map.of("slug", "weather", "standard", "STA")));
      trigger.put("routeIds", Map.of("traffic", "existing-route-id"));

      SagaContext context = saga(trigger);

      Map<String, Object> results = SagaPayloadBuilder.aggregateResults(context);

      @SuppressWarnings("unchecked")
      Map<String, String> routeIds = (Map<String, String>) results.get("routeIds");
      assertEquals("existing-route-id", routeIds.get("traffic"));
      assertEquals(NamedApiHelper.derive("ds-1", "weather"), routeIds.get("weather"));
    }

    @Test
    @DisplayName("Empty or absent namedApis omits routeIds from the result")
    void aggregateResults_noNamedApis_shouldOmitRouteIds() {
      SagaContext withoutKey = saga(Map.of());
      assertTrue(!SagaPayloadBuilder.aggregateResults(withoutKey).containsKey("routeIds"));

      SagaContext withEmptyList = saga(Map.of("namedApis", List.of()));
      assertTrue(!SagaPayloadBuilder.aggregateResults(withEmptyList).containsKey("routeIds"));
    }
  }

  @Nested
  @DisplayName("collectCompensationResults")
  class CollectCompensationResults {

    @Test
    @DisplayName("classifies COMPENSATION_FAILED as stale and COMPENSATED as cleaned")
    void shouldClassifyCompensationResults() {
      SagaContext context =
          saga(
              Map.of(),
              step(
                  "create-project", "frost", SagaStepStatus.COMPENSATED, Map.of("projectId", "42")),
              stepWithError(
                  "create-route", "apisix", SagaStepStatus.COMPENSATION_FAILED, "timeout"));

      List<SagaAction.StaleResource> stale = new ArrayList<>();
      List<SagaAction.CleanedResource> cleaned = new ArrayList<>();
      SagaPayloadBuilder.collectCompensationResults(context, stale, cleaned);

      assertEquals(1, stale.size());
      assertEquals("apisix", stale.get(0).adapter());
      assertEquals("timeout", stale.get(0).error());

      assertEquals(1, cleaned.size());
      assertEquals("frost", cleaned.get(0).adapter());
      assertEquals("42", cleaned.get(0).resourceId());
    }

    @Test
    @DisplayName("ignores steps with other statuses")
    void shouldIgnoreOtherStatuses() {
      SagaContext context =
          saga(
              Map.of(),
              step("create-project", "frost", SagaStepStatus.SUCCESS, Map.of()),
              step("create-route", "apisix", SagaStepStatus.FAILED, Map.of()));

      List<SagaAction.StaleResource> stale = new ArrayList<>();
      List<SagaAction.CleanedResource> cleaned = new ArrayList<>();
      SagaPayloadBuilder.collectCompensationResults(context, stale, cleaned);

      assertTrue(stale.isEmpty());
      assertTrue(cleaned.isEmpty());
    }
  }

  @Nested
  @DisplayName("collectDeleteResults")
  class CollectDeleteResults {

    @Test
    @DisplayName("classifies FAILED as stale and SUCCESS as cleaned")
    void shouldClassifyDeleteResults() {
      SagaContext context =
          saga(
              Map.of(),
              step("delete-project", "frost", SagaStepStatus.SUCCESS, Map.of("projectId", "42")),
              stepWithError("delete-route", "apisix", SagaStepStatus.FAILED, "not found"));

      List<SagaAction.StaleResource> stale = new ArrayList<>();
      List<SagaAction.CleanedResource> cleaned = new ArrayList<>();
      SagaPayloadBuilder.collectDeleteResults(context, stale, cleaned);

      assertEquals(1, stale.size());
      assertEquals("apisix", stale.get(0).adapter());

      assertEquals(1, cleaned.size());
      assertEquals("frost", cleaned.get(0).adapter());
    }

    @Test
    @DisplayName("skips SKIPPED steps")
    void shouldSkipSkippedSteps() {
      SagaContext context =
          saga(
              Map.of(),
              step("delete-project", "frost", SagaStepStatus.SUCCESS, Map.of()),
              step("delete-pipeline", "redpanda", SagaStepStatus.SKIPPED, Map.of()));

      List<SagaAction.StaleResource> stale = new ArrayList<>();
      List<SagaAction.CleanedResource> cleaned = new ArrayList<>();
      SagaPayloadBuilder.collectDeleteResults(context, stale, cleaned);

      assertEquals(0, stale.size());
      assertEquals(1, cleaned.size());
    }
  }

  @Nested
  @DisplayName("extractResourceId")
  class ExtractResourceId {

    @Test
    @DisplayName("returns projectId when present in result")
    void shouldReturnProjectId() {
      SagaStep frostStep =
          step("create-project", "frost", SagaStepStatus.SUCCESS, Map.of("projectId", "42"));

      assertEquals("42", SagaPayloadBuilder.extractResourceId(frostStep));
    }

    @Test
    @DisplayName("returns routeId when present in result")
    void shouldReturnRouteId() {
      SagaStep apisixStep =
          step(
              "create-route",
              "apisix",
              SagaStepStatus.SUCCESS,
              Map.of("routeId", "ds-1", "serviceId", "ds-1"));

      assertEquals("ds-1", SagaPayloadBuilder.extractResourceId(apisixStep));
    }

    @Test
    @DisplayName("falls back to stepId when result is null")
    void shouldFallBackToStepIdWhenResultNull() {
      SagaStep nullResultStep =
          new SagaStep(
              "create-project", "frost", "OP", SagaStepStatus.FAILED, null, null, null, NOW, NOW);

      assertEquals("create-project", SagaPayloadBuilder.extractResourceId(nullResultStep));
    }

    @Test
    @DisplayName("falls back to stepId when result is empty")
    void shouldFallBackToStepIdWhenResultEmpty() {
      SagaStep emptyResultStep = step("create-project", "frost", SagaStepStatus.FAILED, Map.of());

      assertEquals("create-project", SagaPayloadBuilder.extractResourceId(emptyResultStep));
    }

    @Test
    @DisplayName("falls back to stepId when no known keys in result")
    void shouldFallBackToStepIdWhenNoKnownKeys() {
      SagaStep unknownKeysStep =
          step("custom-step", "custom", SagaStepStatus.SUCCESS, Map.of("customField", "value"));

      assertEquals("custom-step", SagaPayloadBuilder.extractResourceId(unknownKeysStep));
    }
  }
}
