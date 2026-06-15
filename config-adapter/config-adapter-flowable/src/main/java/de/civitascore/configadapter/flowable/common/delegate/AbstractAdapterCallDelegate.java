/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.common.delegate;

import org.flowable.common.engine.api.delegate.Expression;

/**
 * Shared base for delegates that dispatch a saga step (forward or compensation) to an adapter
 * handler. Holds the three Flowable field injections common to both flows: {@code adapterName},
 * {@code operation}, {@code stepId}.
 */
abstract class AbstractAdapterCallDelegate extends AbstractSagaDelegate {

  protected Expression adapterName;
  protected Expression operation;
  protected Expression stepId;

  public void setAdapterName(Expression adapterName) {
    this.adapterName = adapterName;
  }

  public void setOperation(Expression operation) {
    this.operation = operation;
  }

  public void setStepId(Expression stepId) {
    this.stepId = stepId;
  }
}
