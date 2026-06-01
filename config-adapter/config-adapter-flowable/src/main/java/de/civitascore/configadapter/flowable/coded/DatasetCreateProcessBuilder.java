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
 * Builds the Dataset Create saga process programmatically using Flowable's BpmnModel API. Produces
 * equivalent behavior to {@code processes/dataset-create.bpmn} (Approach A).
 *
 * <p>The full topology (FROST → APISIX → conditional Redpanda + reverse-order compensation chain)
 * lives in {@link DatasetProvisioningSagaTemplate}; this class only supplies the CREATE-specific
 * operation codes.
 */
public final class DatasetCreateProcessBuilder {

  private DatasetCreateProcessBuilder() {}

  /** Builds the BpmnModel for the dataset-create saga process. */
  public static BpmnModel build() {
    return DatasetProvisioningSagaTemplate.build(
        "dataset-create", "Dataset Create Saga (Coded)", DatasetProvisioningSagaTemplate.CREATE);
  }
}
