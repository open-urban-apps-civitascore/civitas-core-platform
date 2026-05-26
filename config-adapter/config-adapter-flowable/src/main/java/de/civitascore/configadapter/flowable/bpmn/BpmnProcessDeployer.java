/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.bpmn;

import java.util.List;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deploys BPMN XML process definitions from the classpath into the Flowable engine. This is
 * Approach A: workflows defined as standard BPMN 2.0 XML files.
 */
public final class BpmnProcessDeployer {

  private static final Logger LOG = LoggerFactory.getLogger(BpmnProcessDeployer.class);

  private static final List<String> PROCESS_RESOURCES =
      List.of(
          "processes/dataset-create.bpmn",
          "processes/dataset-update.bpmn",
          "processes/dataset-delete.bpmn");

  private BpmnProcessDeployer() {}

  /**
   * Deploys all saga BPMN process definitions. If a process definition with the same key already
   * exists, Flowable automatically creates a new version.
   *
   * @param repositoryService the Flowable repository service
   */
  public static void deploy(RepositoryService repositoryService) {
    DeploymentBuilder deploymentBuilder =
        repositoryService
            .createDeployment()
            .name("config-adapter-saga-processes")
            .enableDuplicateFiltering();

    for (String resource : PROCESS_RESOURCES) {
      deploymentBuilder.addClasspathResource(resource);
    }

    Deployment deployment = deploymentBuilder.deploy();
    LOG.info(
        "Deployed {} BPMN saga processes (deployment ID: {})",
        PROCESS_RESOURCES.size(),
        deployment.getId());
  }
}
