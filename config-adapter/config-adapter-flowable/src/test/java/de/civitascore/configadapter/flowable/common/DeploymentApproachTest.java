/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeploymentApproachTest {

  @Test
  @DisplayName("Defaults to CODED when the approach is unset (null)")
  void nullDefaultsToCoded() {
    assertEquals(DeploymentApproach.CODED, DeploymentApproach.fromConfig(null));
  }

  @Test
  @DisplayName("Defaults to CODED for blank or unknown values instead of silently picking BPMN")
  void blankOrUnknownDefaultsToCoded() {
    assertEquals(DeploymentApproach.CODED, DeploymentApproach.fromConfig(""));
    assertEquals(DeploymentApproach.CODED, DeploymentApproach.fromConfig("   "));
    assertEquals(DeploymentApproach.CODED, DeploymentApproach.fromConfig("garbage"));
    assertEquals(DeploymentApproach.CODED, DeploymentApproach.fromConfig("bpmnn"));
  }

  @Test
  @DisplayName("Selects CODED when explicitly configured (case-insensitive)")
  void codedSelectedExplicitly() {
    assertEquals(DeploymentApproach.CODED, DeploymentApproach.fromConfig("coded"));
    assertEquals(DeploymentApproach.CODED, DeploymentApproach.fromConfig("CODED"));
  }

  @Test
  @DisplayName("Only an explicit 'bpmn' (case-insensitive) selects the XML-deployment path")
  void bpmnSelectedExplicitly() {
    assertEquals(DeploymentApproach.BPMN, DeploymentApproach.fromConfig("bpmn"));
    assertEquals(DeploymentApproach.BPMN, DeploymentApproach.fromConfig("BPMN"));
    assertEquals(DeploymentApproach.BPMN, DeploymentApproach.fromConfig("Bpmn"));
  }
}
