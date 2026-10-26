/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connectiontest;

import java.util.Map;

/** Tests the connection of one source type with its own client. */
interface ConnectionProber {

  /** The {@code connectionType} of the connector documents this prober tests. */
  String connectionType();

  /**
   * Connects to the source and asks it for an answer, without reading data into the platform.
   *
   * @param connector the connector document, secrets decrypted
   * @param apiCall the name of the API call to run, for an HTTP connector
   * @param applyWhere whether an SQL connector's where clause is part of the query
   * @param cancellation takes every connection the test opens
   * @throws ConnectionTestFailure if the source does not answer as configured
   */
  ProbeResult probe(
      Map<String, Object> connector, String apiCall, boolean applyWhere, Cancellation cancellation)
      throws ConnectionTestFailure;
}
