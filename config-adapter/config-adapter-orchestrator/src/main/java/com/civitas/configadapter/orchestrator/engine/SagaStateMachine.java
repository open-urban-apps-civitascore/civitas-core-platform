/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.orchestrator.engine;

import com.civitas.configadapter.model.saga.SagaContext;
import com.civitas.configadapter.model.saga.SagaContextHelper;
import com.civitas.configadapter.model.saga.SagaFailure;
import com.civitas.configadapter.model.saga.SagaStatus;
import com.civitas.configadapter.model.saga.SagaStep;
import com.civitas.configadapter.model.saga.SagaStepStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Pure state machine for saga orchestration. No I/O, no Kafka, no external dependencies — only
 * model classes and step definitions.
 *
 * <p>Each method takes the current state + an event and returns the new state + a list of actions.
 * The engine dispatches the actions (persist state, send commands, publish results).
 *
 * <p>Supports three execution modes:
 *
 * <ul>
 *   <li><b>Create/Update</b>: Sequential forward execution with reverse-order compensation on
 *       failure (best-effort compensation — continues even if a compensation step fails)
 *   <li><b>Delete</b>: Sequential forward execution with best-effort execution (continues even if a
 *       step fails, no compensation)
 * </ul>
 */
public class SagaStateMachine {

  private final Predicate<Map<String, Object>> conditionalStepPredicate;

  /**
   * Creates a state machine with the given predicate for evaluating conditional steps.
   *
   * @param conditionalStepPredicate evaluates whether a conditional step should execute. Receives
   *     the trigger payload and returns true if the step should run (e.g. returns true if
   *     dataPipelines is non-empty).
   */
  public SagaStateMachine(Predicate<Map<String, Object>> conditionalStepPredicate) {
    this.conditionalStepPredicate = conditionalStepPredicate;
  }

  /**
   * Start a new saga. Creates the SagaContext with PENDING steps and begins executing the first
   * step.
   */
  public SagaTransitionResult startSaga(
      SagaDefinition definition,
      String datasetId,
      Map<String, Object> triggerPayload,
      Instant now) {

    String sagaId = UUID.randomUUID().toString();

    List<SagaStep> steps =
        definition.steps().stream()
            .map(sd -> SagaStep.pending(sd.stepId(), sd.adapter(), sd.operation()))
            .toList();

    var context =
        new SagaContext(
            sagaId,
            definition.sagaType(),
            datasetId,
            null,
            SagaStatus.PENDING,
            steps,
            null,
            triggerPayload,
            now,
            now);

    return advanceForwardExecution(context, definition, 0, now);
  }

  /**
   * Handle a step completing successfully during forward execution.
   *
   * @param resultData adapter-specific output (e.g. projectId, baseUrl)
   * @param compensationData data needed to undo this step
   */
  public SagaTransitionResult handleStepCompleted(
      SagaContext context,
      SagaDefinition definition,
      String stepId,
      Map<String, Object> resultData,
      Map<String, Object> compensationData,
      Instant now) {

    int stepIndex = SagaContextHelper.findStepIndex(context, stepId);
    if (stepIndex < 0) {
      return SagaTransitionResult.of(context); // unknown step, no-op
    }

    SagaStep step = context.steps().get(stepIndex);
    SagaStep updatedStep = step.asSucceeded(resultData, compensationData, now);
    SagaContext updatedContext = context.withStepReplaced(stepIndex, updatedStep, now);

    int nextIndex = stepIndex + 1;

    // For delete sagas with prior failures, continue in best-effort mode
    if (!definition.supportsCompensation() && hasPriorFailure(updatedContext)) {
      return advanceDeleteAfterFailure(updatedContext, definition, nextIndex, now);
    }

    return advanceForwardExecution(updatedContext, definition, nextIndex, now);
  }

  /**
   * Handle a step failing during forward execution. For create/update sagas, begins compensation.
   * For delete sagas, continues with the next step (best-effort).
   */
  public SagaTransitionResult handleStepFailed(
      SagaContext context, SagaDefinition definition, String stepId, String error, Instant now) {

    int stepIndex = SagaContextHelper.findStepIndex(context, stepId);
    if (stepIndex < 0) {
      return SagaTransitionResult.of(context);
    }

    SagaStep step = context.steps().get(stepIndex);
    SagaStep failedStep = step.asFailed(error, now);
    SagaContext updatedContext =
        context
            .withStepReplaced(stepIndex, failedStep, now)
            .withFailure(new SagaFailure(stepId, step.adapter(), error, null), now);

    if (!definition.supportsCompensation()) {
      // Delete saga: best-effort forward execution — continue with next step
      return advanceDeleteAfterFailure(updatedContext, definition, stepIndex + 1, now);
    }

    // Create/Update saga: mark unreached steps as SKIPPED, then begin compensation
    updatedContext = skipRemainingPendingSteps(updatedContext, now);
    return beginCompensation(updatedContext, definition, now);
  }

  /** Handle a step timeout (treated as failure). */
  public SagaTransitionResult handleStepTimeout(
      SagaContext context, SagaDefinition definition, String stepId, Instant now) {
    return handleStepFailed(
        context, definition, stepId, "Timeout waiting for adapter response", now);
  }

  /** Handle a compensation step completing successfully. */
  public SagaTransitionResult handleCompensationCompleted(
      SagaContext context, SagaDefinition definition, String stepId, Instant now) {

    int stepIndex = SagaContextHelper.findStepIndex(context, stepId);
    if (stepIndex < 0) {
      return SagaTransitionResult.of(context);
    }

    SagaStep step = context.steps().get(stepIndex);
    SagaStep compensatedStep = step.asCompensated(now);
    SagaContext updatedContext = context.withStepReplaced(stepIndex, compensatedStep, now);

    return advanceCompensation(updatedContext, definition, now);
  }

  /** Handle a compensation step failing. Continues with next compensation (best-effort). */
  public SagaTransitionResult handleCompensationFailed(
      SagaContext context, SagaDefinition definition, String stepId, String error, Instant now) {

    int stepIndex = SagaContextHelper.findStepIndex(context, stepId);
    if (stepIndex < 0) {
      return SagaTransitionResult.of(context);
    }

    SagaStep step = context.steps().get(stepIndex);
    SagaStep failedStep = step.asCompensationFailed(error, now);
    SagaContext updatedContext = context.withStepReplaced(stepIndex, failedStep, now);

    // Best-effort: continue compensating remaining steps
    return advanceCompensation(updatedContext, definition, now);
  }

  /** Returns true if any step in the saga has FAILED status. */
  private boolean hasPriorFailure(SagaContext context) {
    return context.steps().stream().anyMatch(s -> s.status() == SagaStepStatus.FAILED);
  }

  /**
   * Marks all remaining PENDING steps as SKIPPED. Called when the saga leaves forward execution
   * (entering compensation or finalizing after failure) so that the final state clearly shows which
   * steps were never reached.
   */
  private SagaContext skipRemainingPendingSteps(SagaContext context, Instant now) {
    var current = context;
    for (int i = 0; i < current.steps().size(); i++) {
      if (current.steps().get(i).status() == SagaStepStatus.PENDING) {
        current = current.withStepReplaced(i, current.steps().get(i).asSkipped(), now);
      }
    }
    return current;
  }

  // ─── Forward Execution ──────────────────────────────────────────────────────

  /**
   * Advance forward execution starting from the given step index. Skips conditional steps when
   * their predicate is not met.
   */
  private SagaTransitionResult advanceForwardExecution(
      SagaContext context, SagaDefinition definition, int fromIndex, Instant now) {

    var actions = new ArrayList<SagaAction>();
    var currentContext = context;

    for (int i = fromIndex; i < definition.stepCount(); i++) {
      SagaStepDefinition stepDef = definition.stepAt(i);

      if (stepDef.conditional()
          && !conditionalStepPredicate.test(currentContext.triggerPayload())) {
        // Skip conditional step
        int stepIdx = SagaContextHelper.findStepIndex(currentContext, stepDef.stepId());
        SagaStep skippedStep = currentContext.steps().get(stepIdx).asSkipped();
        currentContext = currentContext.withStepReplaced(stepIdx, skippedStep, now);
        actions.add(new SagaAction.SkipStep(stepDef.stepId(), "Precondition not met"));
        continue;
      }

      // Found a non-skipped step — execute it
      int stepIdx = SagaContextHelper.findStepIndex(currentContext, stepDef.stepId());
      SagaStep inProgressStep = currentContext.steps().get(stepIdx).asInProgress(now);
      currentContext =
          currentContext
              .withStepReplaced(stepIdx, inProgressStep, now)
              .withCurrentStep(stepDef.stepId(), now)
              .withStatus(SagaStatus.EXECUTING, now);

      actions.add(0, new SagaAction.PersistState(currentContext));
      actions.add(
          new SagaAction.ExecuteStep(
              stepDef.stepId(),
              stepDef.adapter(),
              stepDef.operation(),
              stepDef.executeTopic(),
              buildStepPayload(currentContext, stepDef)));

      return new SagaTransitionResult(currentContext, actions);
    }

    // All steps done (or skipped) — saga complete
    currentContext = currentContext.withStatus(SagaStatus.COMPLETED, now);
    actions.add(0, new SagaAction.PersistState(currentContext));
    actions.add(
        new SagaAction.CompleteSaga(currentContext.sagaId(), aggregateResults(currentContext)));

    return new SagaTransitionResult(currentContext, actions);
  }

  // ─── Delete Best-Effort Forward ─────────────────────────────────────────────

  /** Continue delete saga after a step failure. Records failure and moves to next step. */
  private SagaTransitionResult advanceDeleteAfterFailure(
      SagaContext context, SagaDefinition definition, int fromIndex, Instant now) {

    var actions = new ArrayList<SagaAction>();
    var currentContext = context;

    for (int i = fromIndex; i < definition.stepCount(); i++) {
      SagaStepDefinition stepDef = definition.stepAt(i);

      if (stepDef.conditional()
          && !conditionalStepPredicate.test(currentContext.triggerPayload())) {
        int stepIdx = SagaContextHelper.findStepIndex(currentContext, stepDef.stepId());
        SagaStep skippedStep = currentContext.steps().get(stepIdx).asSkipped();
        currentContext = currentContext.withStepReplaced(stepIdx, skippedStep, now);
        actions.add(new SagaAction.SkipStep(stepDef.stepId(), "Precondition not met"));
        continue;
      }

      // Found next executable step — execute it
      int stepIdx = SagaContextHelper.findStepIndex(currentContext, stepDef.stepId());
      SagaStep inProgressStep = currentContext.steps().get(stepIdx).asInProgress(now);
      currentContext =
          currentContext
              .withStepReplaced(stepIdx, inProgressStep, now)
              .withCurrentStep(stepDef.stepId(), now);

      actions.add(0, new SagaAction.PersistState(currentContext));
      actions.add(
          new SagaAction.ExecuteStep(
              stepDef.stepId(),
              stepDef.adapter(),
              stepDef.operation(),
              stepDef.executeTopic(),
              buildStepPayload(currentContext, stepDef)));

      return new SagaTransitionResult(currentContext, actions);
    }

    // All delete steps processed — report final state
    return finalizeDelete(currentContext, now);
  }

  /** Finalize a delete saga. Reports success only if all steps succeeded. */
  private SagaTransitionResult finalizeDelete(SagaContext context, Instant now) {
    var actions = new ArrayList<SagaAction>();

    boolean hasFailure =
        context.steps().stream().anyMatch(s -> s.status() == SagaStepStatus.FAILED);

    if (hasFailure) {
      SagaContext failedContext = context.withStatus(SagaStatus.FAILED, now);
      actions.add(new SagaAction.PersistState(failedContext));

      var stale = new ArrayList<SagaAction.StaleResource>();
      var cleaned = new ArrayList<SagaAction.CleanedResource>();
      collectDeleteResults(failedContext, stale, cleaned);

      actions.add(
          new SagaAction.FailSaga(
              failedContext.sagaId(),
              failedContext.failure() != null ? failedContext.failure().stepId() : null,
              failedContext.failure() != null
                  ? failedContext.failure().error()
                  : "Delete partially failed",
              false,
              stale,
              cleaned));
      actions.add(new SagaAction.PublishManualIntervention(failedContext.sagaId(), failedContext));

      return new SagaTransitionResult(failedContext, actions);
    }

    SagaContext completedContext = context.withStatus(SagaStatus.COMPLETED, now);
    actions.add(new SagaAction.PersistState(completedContext));
    actions.add(
        new SagaAction.CompleteSaga(completedContext.sagaId(), aggregateResults(completedContext)));

    return new SagaTransitionResult(completedContext, actions);
  }

  // ─── Compensation ───────────────────────────────────────────────────────────

  /**
   * Begin compensation: transition to COMPENSATING and start compensating the last SUCCESS step.
   */
  private SagaTransitionResult beginCompensation(
      SagaContext context, SagaDefinition definition, Instant now) {

    SagaContext currentContext = context.withStatus(SagaStatus.COMPENSATING, now);
    return advanceCompensation(currentContext, definition, now);
  }

  /**
   * Find the next step to compensate (last SUCCESS step in execution order) and issue a
   * compensation command. If no more steps need compensation, finalize.
   */
  private SagaTransitionResult advanceCompensation(
      SagaContext context, SagaDefinition definition, Instant now) {

    var actions = new ArrayList<SagaAction>();

    // Find next step to compensate: last SUCCESS step in execution order (= first in reverse)
    Optional<SagaStep> nextToCompensate = SagaContextHelper.getNextStepToCompensate(context);

    if (nextToCompensate.isEmpty()) {
      return finalizeCompensation(context, now);
    }

    SagaStep step = nextToCompensate.get();
    SagaStepDefinition stepDef = definition.findStep(step.stepId());

    if (stepDef == null || !stepDef.hasCompensation()) {
      // Step has no compensation defined — mark as compensated and continue
      int stepIdx = SagaContextHelper.findStepIndex(context, step.stepId());
      SagaStep compensated = step.asCompensated(now);
      SagaContext updatedContext = context.withStepReplaced(stepIdx, compensated, now);
      return advanceCompensation(updatedContext, definition, now);
    }

    int stepIdx = SagaContextHelper.findStepIndex(context, step.stepId());
    SagaStep compensating = step.asCompensating(now);
    SagaContext currentContext =
        context.withStepReplaced(stepIdx, compensating, now).withCurrentStep(step.stepId(), now);

    actions.add(new SagaAction.PersistState(currentContext));
    actions.add(
        new SagaAction.CompensateStep(
            step.stepId(),
            stepDef.adapter(),
            stepDef.compensationOperation(),
            stepDef.compensateTopic(),
            buildCompensationPayload(currentContext, step)));

    return new SagaTransitionResult(currentContext, actions);
  }

  /** Finalize compensation. Determine if all compensations succeeded or some failed. */
  private SagaTransitionResult finalizeCompensation(SagaContext context, Instant now) {
    var actions = new ArrayList<SagaAction>();

    boolean hasCompensationFailure = SagaContextHelper.hasCompensationFailure(context);

    if (hasCompensationFailure) {
      SagaContext failedContext = context.withStatus(SagaStatus.COMPENSATION_FAILED, now);
      actions.add(new SagaAction.PersistState(failedContext));

      var stale = new ArrayList<SagaAction.StaleResource>();
      var cleaned = new ArrayList<SagaAction.CleanedResource>();
      collectCompensationResults(failedContext, stale, cleaned);

      actions.add(
          new SagaAction.FailSaga(
              failedContext.sagaId(),
              failedContext.failure() != null ? failedContext.failure().stepId() : null,
              failedContext.failure() != null
                  ? failedContext.failure().error()
                  : "Compensation partially failed",
              false,
              stale,
              cleaned));
      actions.add(new SagaAction.PublishManualIntervention(failedContext.sagaId(), failedContext));

      return new SagaTransitionResult(failedContext, actions);
    }

    SagaContext compensatedContext = context.withStatus(SagaStatus.COMPENSATED, now);
    actions.add(new SagaAction.PersistState(compensatedContext));
    actions.add(
        new SagaAction.FailSaga(
            compensatedContext.sagaId(),
            compensatedContext.failure() != null ? compensatedContext.failure().stepId() : null,
            compensatedContext.failure() != null
                ? compensatedContext.failure().error()
                : "Step failed",
            true,
            List.of(),
            List.of()));

    return new SagaTransitionResult(compensatedContext, actions);
  }

  // ─── Payload Building ───────────────────────────────────────────────────────

  /**
   * Build the payload for a forward step command. The actual payload transformation is delegated to
   * the DataMapper (Task #6) — for now returns the trigger payload augmented with previous step
   * results.
   */
  private Map<String, Object> buildStepPayload(SagaContext context, SagaStepDefinition stepDef) {
    var payload = new HashMap<>(context.triggerPayload());
    // Add results from previous steps so downstream adapters can access them
    for (SagaStep step : context.steps()) {
      if (step.status() == SagaStepStatus.SUCCESS && step.result() != null) {
        payload.putAll(step.result());
      }
    }
    // Saga envelope fields needed by command handlers to correlate results
    payload.put("sagaId", context.sagaId());
    payload.put("datasetId", context.datasetId());
    payload.put("_operation", stepDef.operation());
    payload.put("_stepId", stepDef.stepId());
    return Map.copyOf(payload);
  }

  /** Build the compensation payload from the step's stored compensation data. */
  private Map<String, Object> buildCompensationPayload(SagaContext context, SagaStep step) {
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

  // ─── Result Aggregation ─────────────────────────────────────────────────────

  /** Aggregate results from all successful steps for the final CompleteSaga action. */
  private Map<String, Object> aggregateResults(SagaContext context) {
    var results = new HashMap<String, Object>();
    results.put("sagaId", context.sagaId());
    results.put("datasetId", context.datasetId());
    for (SagaStep step : context.steps()) {
      if (step.status() == SagaStepStatus.SUCCESS && step.result() != null) {
        results.putAll(step.result());
      }
    }
    return Map.copyOf(results);
  }

  /** Collect stale and cleaned resources from compensation results. */
  private void collectCompensationResults(
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
  private void collectDeleteResults(
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
  private String extractResourceId(SagaStep step) {
    if (step.result() == null || step.result().isEmpty()) {
      return step.stepId();
    }
    // Try common resource ID keys
    for (String key : List.of("projectId", "routeId", "serviceId", "pipelineIds")) {
      Object value = step.result().get(key);
      if (value != null) {
        return value.toString();
      }
    }
    return step.stepId();
  }
}
