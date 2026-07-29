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
 * A chain segment between source and sink — the build half of one compiled transform unit, or a
 * structurally inserted step. Node-carried transforms reach the chain through their {@link
 * TransformNodeType}; the convert step is different: it is never user-modelled, its existence is a
 * structural function of source output form and sink input shape (the coercion table), so the
 * builder inserts it directly.
 */
@FunctionalInterface
public interface TransformStage {

  /**
   * Loads and configures this segment's processor(s). Returns without appending — the orchestrator
   * owns array order.
   */
  StageResult build(BuildContext ctx) throws FatalAdapterException;
}
