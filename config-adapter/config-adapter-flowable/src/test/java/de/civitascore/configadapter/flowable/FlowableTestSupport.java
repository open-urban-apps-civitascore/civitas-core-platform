/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.flowable.common.FlowableEngineFactory;
import de.civitascore.configadapter.flowable.common.SagaHandlerRegistry;
import de.civitascore.configadapter.flowable.common.kafka.FlowableResultPublisher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.job.api.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared test utilities for Flowable process tests. Eliminates duplication across BPMN and coded
 * test classes.
 */
public final class FlowableTestSupport {

  private static final Logger LOG = LoggerFactory.getLogger(FlowableTestSupport.class);

  private FlowableTestSupport() {}

  /**
   * Creates a test ProcessEngine with the given beans plus a no-op FlowableResultPublisher (if not
   * already present). Ensures ResultPublishDelegate doesn't throw in tests.
   */
  public static ProcessEngine createTestEngine(Map<String, Object> beans) {
    Map<String, Object> allBeans = new HashMap<>(beans);
    allBeans.putIfAbsent("resultPublisher", mock(FlowableResultPublisher.class));
    return FlowableEngineFactory.createWithH2(allBeans);
  }

  /**
   * Executes all pending jobs until none remain. A job that fails is left for Flowable to retry
   * (its retry count is decremented and the loop re-runs it). A job that exhausts its retries lands
   * in the dead-letter queue and fails this method — so transient/retried failures (e.g. a Kafka
   * publish that succeeds on retry) are tolerated, while a permanently failing job (e.g. a buggy
   * delegate) surfaces instead of being silently swallowed.
   */
  public static void executeAllJobs(ProcessEngine engine) {
    ManagementService mgmt = engine.getManagementService();
    for (int i = 0; i < 100; i++) {
      List<Job> jobs = mgmt.createJobQuery().list();
      if (jobs.isEmpty()) {
        List<Job> deadLetterJobs = mgmt.createDeadLetterJobQuery().list();
        if (!deadLetterJobs.isEmpty()) {
          Job dead = deadLetterJobs.get(0);
          throw new IllegalStateException(
              "Job "
                  + dead.getId()
                  + " exhausted its retries and moved to the dead-letter queue: "
                  + dead.getExceptionMessage());
        }
        List<Job> timerJobs = mgmt.createTimerJobQuery().list();
        if (timerJobs.isEmpty()) {
          break;
        }
        for (Job timerJob : timerJobs) {
          mgmt.moveTimerToExecutableJob(timerJob.getId());
        }
        continue;
      }
      for (Job job : jobs) {
        try {
          mgmt.executeJob(job.getId());
        } catch (FlowableException e) {
          // The job failed; Flowable decremented its retry count and keeps it executable, so the
          // loop re-runs it. Permanent failures end up in the dead-letter queue (surfaced above).
          LOG.debug("Job {} failed, will retry: {}", job.getId(), e.getMessage());
        }
      }
    }
  }

  /** Asserts the process completed normally (finished, not cancelled). */
  public static void assertProcessCompleted(
      HistoryService historyService, String processInstanceId) {
    HistoricProcessInstance instance =
        historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .finished()
            .singleResult();
    assertNotNull(instance, "Process should be completed");
    assertNull(instance.getDeleteReason(), "Process should not be cancelled");
  }

  /** Asserts the process finished (completed or ended via error path). */
  public static void assertProcessFinished(
      HistoryService historyService, String processInstanceId) {
    HistoricProcessInstance instance =
        historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .finished()
            .singleResult();
    assertNotNull(instance, "Process should be finished");
  }

  /** Returns the IDs of completed forward (non-compensation) service tasks in execution order. */
  public static List<String> getForwardServiceTaskIds(
      HistoryService historyService, String processInstanceId) {
    return historyService
        .createHistoricActivityInstanceQuery()
        .processInstanceId(processInstanceId)
        .activityType("serviceTask")
        .finished()
        .orderByHistoricActivityInstanceStartTime()
        .asc()
        .list()
        .stream()
        .map(HistoricActivityInstance::getActivityId)
        .filter(id -> !id.startsWith("compensate-") && !id.startsWith("publish-"))
        .toList();
  }

  /**
   * Creates a mock SagaCommandHandler for the given adapter name, pre-stubbed with the field
   * aliases the real adapter would declare. Keeps tests realistic — payload field renaming (e.g.
   * baseUrl→upstreamUrl) happens just like in production.
   */
  public static SagaCommandHandler mockHandler(String adapter) {
    SagaCommandHandler handler = mock(SagaCommandHandler.class);
    when(handler.adapter()).thenReturn(adapter);
    when(handler.fieldAliases()).thenReturn(fieldAliasesFor(adapter));
    return handler;
  }

  private static Map<String, String> fieldAliasesFor(String adapter) {
    return switch (adapter) {
      case "apisix" -> Map.of("baseUrl", "upstreamUrl");
      // The NiFi pipeline adapter declares no field aliases (its sink target is platform-managed,
      // not derived from the FROST baseUrl as the former pipeline adapter required).
      default -> Map.of();
    };
  }

  /** Creates a SagaHandlerRegistry with the given handlers. */
  public static SagaHandlerRegistry registry(SagaCommandHandler... handlers) {
    SagaHandlerRegistry registry = new SagaHandlerRegistry();
    for (SagaCommandHandler handler : handlers) {
      registry.register(handler);
    }
    return registry;
  }

  /** Creates a mock Flowable Expression that resolves to the given string value. */
  public static Expression mockExpression(String value) {
    Expression expr = mock(Expression.class);
    when(expr.getValue(any())).thenReturn(value);
    when(expr.getExpressionText()).thenReturn(value);
    return expr;
  }
}
