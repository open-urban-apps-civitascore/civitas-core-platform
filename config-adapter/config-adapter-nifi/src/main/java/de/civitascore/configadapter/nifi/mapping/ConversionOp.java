/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.mapping;

import java.util.Optional;

/**
 * The conversion operations supported by a CORE Mapping ({@code .../core/mapping/v1}). Each maps to
 * a pure NiFi RecordPath function or to a target-schema-driven coercion — never to in-band
 * scripting.
 */
public enum ConversionOp {
  TO_STRING("toString", false),
  TO_INT("toInt", false),
  TO_FLOAT("toFloat", false),
  TO_DATE("toDate", true),
  FORMAT("format", true);

  private final String rawOp;
  private final boolean requiresPattern;

  ConversionOp(String rawOp, boolean requiresPattern) {
    this.rawOp = rawOp;
    this.requiresPattern = requiresPattern;
  }

  /**
   * Returns the raw {@code op} token as it appears in the mapping JSON.
   *
   * @return the raw op token
   */
  public String rawOp() {
    return rawOp;
  }

  /**
   * Whether this op needs a non-blank {@code pattern} operand ({@code toDate}/{@code format}).
   *
   * @return true if a pattern is mandatory
   */
  public boolean requiresPattern() {
    return requiresPattern;
  }

  /**
   * Resolves a raw {@code op} token to a conversion operation.
   *
   * @param raw the raw op token (e.g. {@code "toDate"})
   * @return the matching operation, or empty if the token is not a conversion op
   */
  public static Optional<ConversionOp> fromRaw(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    for (ConversionOp op : values()) {
      if (op.rawOp.equals(raw)) {
        return Optional.of(op);
      }
    }
    return Optional.empty();
  }
}
