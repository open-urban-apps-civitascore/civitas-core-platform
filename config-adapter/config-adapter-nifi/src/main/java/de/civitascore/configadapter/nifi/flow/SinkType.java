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

/** The datasink kinds the curated NiFi templates support as a pipeline output. */
public enum SinkType {
  POSTGIS,
  FROST;

  /**
   * Leniently resolves a raw datasink {@code type} (case-insensitive) to a sink type.
   *
   * @param raw the raw type string
   * @return the matching sink type, or empty if unsupported
   */
  public static Optional<SinkType> fromRaw(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    return switch (raw.toLowerCase(Locale.ROOT)) {
      case "postgis", "postgresql", "postgres" -> Optional.of(POSTGIS);
      case "frost", "sta" -> Optional.of(FROST);
      default -> Optional.empty();
    };
  }
}
