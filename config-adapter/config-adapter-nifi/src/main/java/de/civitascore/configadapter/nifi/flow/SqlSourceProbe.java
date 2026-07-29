/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow;

import de.civitascore.configadapter.exception.FatalAdapterException;

/**
 * Validates that a SQL source is actually reachable before deploy, so a misconfigured source (wrong
 * host/credentials) fails the saga loudly instead of deploying a flow that silently produces no
 * data. The default {@link #NO_OP} skips the check (used in unit tests with no real DB); production
 * wires a real JDBC probe.
 */
@FunctionalInterface
public interface SqlSourceProbe {
  /** A probe that performs no check. */
  SqlSourceProbe NO_OP = (jdbcUrl, user, password) -> {};

  void probe(String jdbcUrl, String user, String password) throws FatalAdapterException;
}
