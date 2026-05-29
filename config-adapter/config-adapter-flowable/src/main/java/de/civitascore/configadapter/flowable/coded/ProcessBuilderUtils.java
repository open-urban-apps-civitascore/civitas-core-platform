/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.coded;

import java.util.List;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.ErrorEventDefinition;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.bpmn.model.ServiceTask;

/**
 * Shared BPMN model building utilities used by all saga process builders.
 *
 * <p>Delegate class names are intentionally referenced as string literals rather than {@code
 * SomeDelegate.class.getName()}: the delegates live in the {@code common.delegate} package, and a
 * compile-time reference from this {@code coded} package would create a {@code coded → common}
 * dependency cycle (the {@code common} orchestrator already depends on {@code coded}), which {@code
 * ArchitectureTest.noCircularPackageDependencies} forbids. The BPMN XML references the same
 * delegates by string for the same reason.
 */
final class ProcessBuilderUtils {

  static final String STEP_DELEGATE =
      "de.civitascore.configadapter.flowable.common.delegate.SagaStepDelegate";
  static final String COMP_DELEGATE =
      "de.civitascore.configadapter.flowable.common.delegate.SagaCompensationDelegate";
  static final String RESULT_DELEGATE =
      "de.civitascore.configadapter.flowable.common.delegate.ResultPublishDelegate";
  static final String ERROR_CODE = "STEP_FAILED";

  static final String PIPELINE_GATEWAY_ID = "pipeline-gateway";
  static final String PUBLISH_SUCCESS_ID = "publish-success";
  static final String PUBLISH_FAILURE_ID = "publish-failure";
  static final String RESULT_GATEWAY_ID = "result-gateway";

  private static final String IMPL_TYPE_CLASS = "class";

  private ProcessBuilderUtils() {}

  static ServiceTask sagaStep(String id, String name, String adapter, String operation) {
    ServiceTask task = new ServiceTask();
    task.setId(id);
    task.setName(name);
    task.setImplementationType(IMPL_TYPE_CLASS);
    task.setImplementation(STEP_DELEGATE);
    task.setFieldExtensions(
        List.of(field("adapterName", adapter), field("operation", operation), field("stepId", id)));
    return task;
  }

  static ServiceTask compensationStep(String id, String adapter, String operation, String stepId) {
    ServiceTask task = new ServiceTask();
    task.setId(id);
    task.setName("Compensate: " + operation);
    task.setImplementationType(IMPL_TYPE_CLASS);
    task.setImplementation(COMP_DELEGATE);
    task.setFieldExtensions(
        List.of(
            field("adapterName", adapter), field("operation", operation), field("stepId", stepId)));
    return task;
  }

  static BoundaryEvent errorBoundary(String id, ServiceTask attachedTo) {
    BoundaryEvent boundary = new BoundaryEvent();
    boundary.setId(id);
    boundary.setAttachedToRefId(attachedTo.getId());
    boundary.setAttachedToRef(attachedTo);
    ErrorEventDefinition errorDef = new ErrorEventDefinition();
    errorDef.setErrorCode(ERROR_CODE);
    boundary.addEventDefinition(errorDef);
    return boundary;
  }

  static FieldExtension field(String name, String value) {
    FieldExtension f = new FieldExtension();
    f.setFieldName(name);
    f.setStringValue(value);
    return f;
  }

  /**
   * Creates an async result publishing service task (success or failure). Defaults to
   * supportsCompensation=true.
   */
  static ServiceTask resultPublishTask(String id, String resultType) {
    return resultPublishTask(id, resultType, true);
  }

  /** Creates an async result publishing service task with explicit compensation support flag. */
  static ServiceTask resultPublishTask(String id, String resultType, boolean supportsCompensation) {
    ServiceTask task = new ServiceTask();
    task.setId(id);
    task.setName("Publish " + resultType + " result");
    task.setImplementationType(IMPL_TYPE_CLASS);
    task.setImplementation(RESULT_DELEGATE);
    task.setAsynchronous(true);
    task.setFieldExtensions(
        List.of(
            field("resultType", resultType),
            field("supportsCompensation", String.valueOf(supportsCompensation))));
    return task;
  }
}
