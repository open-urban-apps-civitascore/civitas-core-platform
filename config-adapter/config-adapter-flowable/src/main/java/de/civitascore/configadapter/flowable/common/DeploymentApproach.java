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

import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Selectable saga process deployment style, configured via the {@code flowable.approach} property.
 */
enum DeploymentApproach {
  BPMN,
  CODED;

  private static final Logger LOG = LoggerFactory.getLogger(DeploymentApproach.class);
  private static final String BPMN_VALUE = "bpmn";
  private static final String CODED_VALUE = "coded";

  /**
   * Resolves the approach from a config value; defaults to {@link #CODED} for null/blank values.
   * Only an explicit {@code bpmn} selects the XML-deployment path. An unrecognised non-blank value
   * (e.g. a typo) also defaults to {@code CODED} but is logged at WARN so a misconfiguration is not
   * silently swallowed.
   */
  static DeploymentApproach fromConfig(String value) {
    if (value == null || value.isBlank()) {
      return CODED;
    }
    if (BPMN_VALUE.equalsIgnoreCase(value)) {
      return BPMN;
    }
    if (!CODED_VALUE.equalsIgnoreCase(value)) {
      LOG.warn("Unrecognised flowable.approach '{}' — defaulting to CODED", Encode.forJava(value));
    }
    return CODED;
  }
}
