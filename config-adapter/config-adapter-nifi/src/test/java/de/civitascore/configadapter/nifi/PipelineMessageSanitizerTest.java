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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PipelineMessageSanitizer")
class PipelineMessageSanitizerTest {

  @Test
  @DisplayName("returns null for null input")
  void handlesNull() {
    assertNull(PipelineMessageSanitizer.sanitize(null));
  }

  @Test
  @DisplayName("redacts URIs of any scheme")
  void redactsUris() {
    String jdbc = PipelineMessageSanitizer.sanitize("fail jdbc:postgresql://db-host:5432/core");
    assertFalse(jdbc.contains("db-host"));
    assertFalse(jdbc.contains("jdbc:postgresql"));
    assertTrue(jdbc.contains("[redacted-url]"));

    assertFalse(
        PipelineMessageSanitizer.sanitize("broker tcp://mqtt.internal:1883 down")
            .contains("mqtt.internal"));
    assertFalse(
        PipelineMessageSanitizer.sanitize("GET https://nifi:8443/x failed").contains("nifi:8443"));
  }

  @Test
  @DisplayName("redacts credential assignments in = and : forms, quoted or not")
  void redactsCredentials() {
    assertFalse(PipelineMessageSanitizer.sanitize("password=hunter2").contains("hunter2"));
    assertFalse(PipelineMessageSanitizer.sanitize("token: \"abc-123\"").contains("abc-123"));
    assertFalse(PipelineMessageSanitizer.sanitize("secret='s3cr3t'").contains("s3cr3t"));
  }

  @Test
  @DisplayName("leaves an already-safe message unchanged")
  void leavesSafeMessageUnchanged() {
    String safe = "Processor 'Consume' is INVALID: missing required property";
    assertEquals(safe, PipelineMessageSanitizer.sanitize(safe));
  }
}
