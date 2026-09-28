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

import java.util.regex.Pattern;

/**
 * Keeps tenant text out of NiFi's Expression Language and parameter evaluation.
 *
 * <p>NiFi evaluates {@code ${...}} against its own process environment in many properties, and
 * {@code #{...}} against the flow's parameter context in all of them. A value without {@code $} or
 * {@code #} directly before a <code>{</code> contains neither form, and NiFi leaves it unchanged
 * whatever the property's EL scope. Rejecting is used rather than escaping: NiFi collapses a
 * doubled {@code $} only in front of a <code>{</code>, so a blanket escape corrupts values such as
 * {@code US$5}.
 */
public final class NifiExpressionLanguage {

  private static final Pattern REFERENCE_START = Pattern.compile("[$#]\\{");

  private NifiExpressionLanguage() {}

  /**
   * Returns {@code value} unchanged if NiFi takes it literally.
   *
   * @param property the NiFi property name, used in the error message instead of the value
   * @throws UnsafePropertyValueException if {@code value} contains <code>${</code> or <code>#{
   *     </code>
   */
  public static String requireLiteral(String property, String value) {
    if (value != null && REFERENCE_START.matcher(value).find()) {
      throw new UnsafePropertyValueException(property);
    }
    return value;
  }
}
