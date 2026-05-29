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

import org.flowable.bpmn.model.BpmnModel;

/**
 * Builds the Dataset Update saga process programmatically. Same topology as Create — only the
 * adapter operation codes differ (UPDATE_* / RESTORE_*). Produces equivalent behavior to {@code
 * dataset-update.bpmn}.
 */
public final class DatasetUpdateProcessBuilder {

  private DatasetUpdateProcessBuilder() {}

  public static BpmnModel build() {
    return DatasetProvisioningSagaTemplate.build(
        "dataset-update", "Dataset Update Saga (Coded)", DatasetProvisioningSagaTemplate.UPDATE);
  }
}
