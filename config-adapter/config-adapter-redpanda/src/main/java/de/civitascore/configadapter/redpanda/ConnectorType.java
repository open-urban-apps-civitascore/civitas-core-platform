/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.redpanda;

import java.util.Arrays;
import java.util.Optional;

/**
 * Datasource connector types supported for Redpanda Connect pipeline injection. Each constant
 * carries its Redpanda input key and the payload aliases it accepts.
 */
enum ConnectorType {
  MQTT("mqtt", "mqtt", "MQTT"),
  SQL("sql_raw", "postgresql", "sql", "SQL");

  /** Redpanda Connect input key, e.g. {@code "mqtt"} or {@code "sql_raw"}. */
  final String redpandaKey;

  private final String[] aliases;

  ConnectorType(String redpandaKey, String... aliases) {
    this.redpandaKey = redpandaKey;
    this.aliases = aliases;
  }

  /** Resolves a raw {@code type} / {@code connectorType} string (case-insensitive). */
  static Optional<ConnectorType> fromRaw(String raw) {
    if (raw == null) return Optional.empty();
    String lower = raw.toLowerCase();
    return Arrays.stream(values())
        .filter(ct -> Arrays.asList(ct.aliases).contains(lower))
        .findFirst();
  }
}
