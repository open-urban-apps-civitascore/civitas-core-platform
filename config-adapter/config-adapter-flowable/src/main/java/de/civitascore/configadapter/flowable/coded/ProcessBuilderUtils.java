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

import de.civitascore.configadapter.flowable.common.delegate.ResultPublishDelegate;
import de.civitascore.configadapter.flowable.common.delegate.SagaCompensationDelegate;
import de.civitascore.configadapter.flowable.common.delegate.SagaStepDelegate;
import java.util.List;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.ErrorEventDefinition;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;

/** Shared BPMN model building utilities used by all saga process builders. */
final class ProcessBuilderUtils {

  static final String STEP_DELEGATE = SagaStepDelegate.class.getName();
  static final String COMP_DELEGATE = SagaCompensationDelegate.class.getName();
  static final String RESULT_DELEGATE = ResultPublishDelegate.class.getName();
  static final String ERROR_CODE = "STEP_FAILED";

  private ProcessBuilderUtils() {}

  static ServiceTask sagaStep(String id, String name, String adapter, String operation) {
    ServiceTask task = new ServiceTask();
    task.setId(id);
    task.setName(name);
    task.setImplementationType("class");
    task.setImplementation(STEP_DELEGATE);
    task.setFieldExtensions(
        List.of(field("adapterName", adapter), field("operation", operation), field("stepId", id)));
    return task;
  }

  static ServiceTask compensationStep(String id, String adapter, String operation, String stepId) {
    ServiceTask task = new ServiceTask();
    task.setId(id);
    task.setName("Compensate: " + operation);
    task.setImplementationType("class");
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

  static SequenceFlow flow(String id, String source, String target) {
    SequenceFlow sf = new SequenceFlow(source, target);
    sf.setId(id);
    return sf;
  }

  static SequenceFlow conditionalFlow(String id, String source, String target, String condition) {
    SequenceFlow sf = new SequenceFlow(source, target);
    sf.setId(id);
    sf.setConditionExpression(condition);
    return sf;
  }

  /** Creates an async result publishing service task (success or failure). */
  static ServiceTask resultPublishTask(String id, String resultType) {
    ServiceTask task = new ServiceTask();
    task.setId(id);
    task.setName("Publish " + resultType + " result");
    task.setImplementationType("class");
    task.setImplementation(RESULT_DELEGATE);
    task.setAsynchronous(true);
    task.setFieldExtensions(List.of(field("resultType", resultType)));
    return task;
  }

  /** Creates an async result publishing service task with explicit compensation support flag. */
  static ServiceTask resultPublishTask(String id, String resultType, boolean supportsCompensation) {
    ServiceTask task = new ServiceTask();
    task.setId(id);
    task.setName("Publish " + resultType + " result");
    task.setImplementationType("class");
    task.setImplementation(RESULT_DELEGATE);
    task.setAsynchronous(true);
    task.setFieldExtensions(
        List.of(
            field("resultType", resultType),
            field("supportsCompensation", String.valueOf(supportsCompensation))));
    return task;
  }
}
