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

/**
 * Selectable saga process deployment style, configured via the {@code flowable.approach} property.
 */
enum DeploymentApproach {
  BPMN,
  CODED;

  /**
   * Resolves the approach from a config value; defaults to {@link #BPMN} for null or unknown
   * values.
   */
  static DeploymentApproach fromConfig(String value) {
    return "coded".equalsIgnoreCase(value) ? CODED : BPMN;
  }
}
