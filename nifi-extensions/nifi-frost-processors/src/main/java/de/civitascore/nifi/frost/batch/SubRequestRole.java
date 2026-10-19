/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost.batch;

/** What the response division does with the answer to a sub-request. */
public enum SubRequestRole {

  /**
   * Resolves a reference the record does not own. An empty result is a data error: the port creates
   * only its own entity, so the record goes to the error sink.
   */
  PARENT_LOOKUP,

  /**
   * Resolves the record's own reference. An empty result is the usual condition of an upsert and
   * lets the create run.
   */
  OWN_LOOKUP,

  /** Changes data. A status of 400 or above fails the record. */
  WRITE
}
