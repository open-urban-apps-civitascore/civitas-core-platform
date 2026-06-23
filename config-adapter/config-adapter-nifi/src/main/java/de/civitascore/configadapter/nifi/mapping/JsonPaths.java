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

/** Translates the CORE Mapping JSONPath dialect into NiFi RecordPath. */
final class JsonPaths {

  private JsonPaths() {}

  /**
   * Converts a JSONPath such as {@code $.a.b[0].c} into the equivalent RecordPath {@code
   * /a/b[0]/c}. The root {@code $} becomes {@code /}. Array-index brackets are preserved.
   *
   * @param jsonPath the source JSONPath
   * @return the equivalent RecordPath
   */
  static String toRecordPath(String jsonPath) {
    if (jsonPath == null || jsonPath.isBlank() || "$".equals(jsonPath)) {
      return "/";
    }
    String path = jsonPath;
    if (path.startsWith("$")) {
      path = path.substring(1);
    }
    if (path.startsWith(".")) {
      path = path.substring(1);
    }
    StringBuilder builder = new StringBuilder();
    for (String segment : path.split("\\.")) {
      builder.append('/').append(segment);
    }
    return builder.toString();
  }
}
