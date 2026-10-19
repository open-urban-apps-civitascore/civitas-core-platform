/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost.port;

import com.fasterxml.jackson.databind.node.ObjectNode;
import de.civitascore.nifi.frost.batch.RecordPlan;

/**
 * Turns one record into the sub-requests of its atomicity group.
 *
 * <p>A planner reaches no network. It reads the record, decides the sub-requests and their order,
 * and rejects a record it cannot plan. That is what makes the port testable against an expected
 * batch document.
 */
@FunctionalInterface
public interface PortPlanner {

  /**
   * Plans one record.
   *
   * @throws RecordRejectedException when the record does not carry what the port needs
   */
  void plan(ObjectNode record, RecordPlan plan, String projectId);
}
