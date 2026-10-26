/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connector;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Reads single values of a connector document, as the portal stores it. */
public final class ConnectorValues {

  private static final Pattern NIFI_REFERENCE_START = Pattern.compile("[$#]\\{");

  private ConnectorValues() {}

  /** A value as a trimmed string, or {@code ""} for null. */
  public static String trimmedString(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }

  /** A scalar or list value as trimmed, non-blank strings (empty for null/all-blank). */
  public static List<String> trimmedNonBlank(Object value) {
    if (value == null) {
      return List.of();
    }
    List<String> raw =
        value instanceof List<?> list
            ? list.stream().filter(Objects::nonNull).map(String::valueOf).toList()
            : List.of(String.valueOf(value));
    return raw.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  /** Whether NiFi would evaluate a {@code ${...}} or {@code #{...}} reference in a value. */
  public static boolean containsNifiReference(String value) {
    return value != null && NIFI_REFERENCE_START.matcher(value).find();
  }

  /** Whether a value is an {@code ENC(...)} ciphertext. */
  public static boolean isEncrypted(Object value) {
    return value instanceof String text && text.startsWith("ENC(") && text.endsWith(")");
  }

  /** A nested object of the document, or an empty map if the value is not one. */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> objectOrEmpty(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }
}
