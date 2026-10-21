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

import de.civitascore.configadapter.connector.ConnectorValues;
import java.util.List;
import java.util.function.BiConsumer;

/** Shared value coercions for the plan-time binding halves of the stages. */
public final class BindingSupport {

  private BindingSupport() {}

  /** A value as a trimmed string, or {@code ""} for null. */
  public static String trimmedString(Object value) {
    return ConnectorValues.trimmedString(value);
  }

  /** A scalar or list value as trimmed, non-blank strings (empty for null/all-blank). */
  public static List<String> trimmedNonBlank(Object value) {
    return ConnectorValues.trimmedNonBlank(value);
  }

  public static void putIfPresent(BiConsumer<String, String> target, String key, Object value) {
    if (value != null) {
      target.accept(key, String.valueOf(value));
    }
  }

  public static boolean isEncrypted(Object value) {
    return ConnectorValues.isEncrypted(value);
  }
}
