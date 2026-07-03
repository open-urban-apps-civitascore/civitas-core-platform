/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage;

import de.civitascore.configadapter.exception.FatalAdapterException;

/**
 * A chain segment between source and sink. Transforms are not registry-dispatched: whether a
 * convert or mapping step exists is a structural function of source capabilities, sink input shape,
 * and the compiled mapping — not of a node type.
 */
public interface TransformStage {

  /**
   * Loads and configures this segment's processor(s). Returns without appending — the orchestrator
   * owns array order.
   */
  StageResult build(BuildContext ctx) throws FatalAdapterException;
}
