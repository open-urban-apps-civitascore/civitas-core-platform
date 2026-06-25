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

import java.util.Locale;
import java.util.Optional;

/** The datasource connector kinds the curated NiFi templates support as a pipeline input. */
public enum SourceType {
  MQTT,
  SQL;

  /**
   * Leniently resolves a raw datasource {@code type} (case-insensitive, common aliases) to a source
   * type.
   *
   * @param raw the raw type string
   * @return the matching source type, or empty if unsupported
   */
  public static Optional<SourceType> fromRaw(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    return switch (raw.toLowerCase(Locale.ROOT)) {
      case "mqtt" -> Optional.of(MQTT);
      case "sql", "postgresql", "postgres", "jdbc" -> Optional.of(SQL);
      default -> Optional.empty();
    };
  }
}
