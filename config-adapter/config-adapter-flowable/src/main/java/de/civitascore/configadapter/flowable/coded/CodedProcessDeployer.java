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

import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deploys programmatically built saga process definitions into the Flowable engine. This is
 * Approach B: workflows defined as Java code via the BpmnModel API.
 */
public final class CodedProcessDeployer {

  private static final Logger LOG = LoggerFactory.getLogger(CodedProcessDeployer.class);

  private CodedProcessDeployer() {}

  /**
   * Deploys all saga process definitions built via the process builder classes.
   *
   * @param repositoryService the Flowable repository service
   */
  public static void deploy(RepositoryService repositoryService) {
    Deployment deployment =
        repositoryService
            .createDeployment()
            .name("config-adapter-saga-processes-coded")
            .enableDuplicateFiltering()
            .addBpmnModel("dataset-create-coded.bpmn", DatasetCreateProcessBuilder.build())
            .addBpmnModel("dataset-update-coded.bpmn", DatasetUpdateProcessBuilder.build())
            .addBpmnModel("dataset-delete-coded.bpmn", DatasetDeleteProcessBuilder.build())
            .addBpmnModel("dataset-unrelease-coded.bpmn", DatasetUnreleaseProcessBuilder.build())
            .deploy();
    LOG.info("Deployed 4 coded saga processes (deployment ID: {})", deployment.getId());
  }
}
