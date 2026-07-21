/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi;

import java.util.regex.Pattern;

/** Redacts URLs and credential-like values before pipeline status messages leave the adapter. */
final class PipelineMessageSanitizer {
  private static final Pattern URI = Pattern.compile("(?i)\\b[a-z][a-z0-9+.-]*://[^\\s\\\"'<>]+");
  private static final Pattern CREDENTIAL =
      Pattern.compile(
          "(?i)\\b(password|token|credential|secret)\\b\\s*[:=]\\s*(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;}\\]]+)");

  private PipelineMessageSanitizer() {}

  static String sanitize(String value) {
    if (value == null) {
      return null;
    }
    return CREDENTIAL
        .matcher(URI.matcher(value).replaceAll("[redacted-url]"))
        .replaceAll("$1=[redacted]");
  }
}
