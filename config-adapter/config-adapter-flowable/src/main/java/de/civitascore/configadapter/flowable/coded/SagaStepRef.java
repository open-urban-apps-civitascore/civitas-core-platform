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

import java.util.Objects;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.ServiceTask;

/**
 * Handle for a saga step added via {@link SagaProcessBuilder#sagaStep}. Bundles the service task,
 * its attached error boundary event, and the originating adapter — so callers can wire error flows
 * and compensations without restating those values.
 */
record SagaStepRef(ServiceTask task, BoundaryEvent boundary, String adapter) {
  SagaStepRef {
    Objects.requireNonNull(task, "task");
    Objects.requireNonNull(boundary, "boundary");
    Objects.requireNonNull(adapter, "adapter");
  }
}
