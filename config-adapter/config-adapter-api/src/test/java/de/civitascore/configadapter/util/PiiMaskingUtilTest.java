/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PiiMaskingUtilTest {

  @Nested
  @DisplayName("maskEmail")
  class MaskEmail {

    @Test
    @DisplayName("masks standard email address")
    void shouldMaskStandardEmail() {
      assertEquals("use***@***.***", PiiMaskingUtil.maskEmail("user@example.com"));
    }

    @Test
    @DisplayName("masks email with short local part")
    void shouldMaskShortLocalPart() {
      assertEquals("ab***@***.***", PiiMaskingUtil.maskEmail("ab@example.com"));
    }

    @Test
    @DisplayName("masks email with single-char local part")
    void shouldMaskSingleCharLocalPart() {
      assertEquals("a***@***.***", PiiMaskingUtil.maskEmail("a@example.com"));
    }

    @Test
    @DisplayName("returns *** for null")
    void shouldReturnMaskedForNull() {
      assertEquals("***", PiiMaskingUtil.maskEmail(null));
    }

    @Test
    @DisplayName("returns *** for string without @")
    void shouldReturnMaskedForInvalidEmail() {
      assertEquals("***", PiiMaskingUtil.maskEmail("not-an-email"));
    }

    @Test
    @DisplayName("returns *** for empty string")
    void shouldReturnMaskedForEmpty() {
      assertEquals("***", PiiMaskingUtil.maskEmail(""));
    }
  }

  @Nested
  @DisplayName("maskId")
  class MaskId {

    @Test
    @DisplayName("masks UUID showing first 8 characters")
    void shouldMaskUuid() {
      assertEquals("06702f15***", PiiMaskingUtil.maskId("06702f15-1439-4958-b069-2ac5716c7a5c"));
    }

    @Test
    @DisplayName("masks longer ID showing first 8 characters")
    void shouldMaskLongId() {
      assertEquals("abcdefgh***", PiiMaskingUtil.maskId("abcdefghijklmnop"));
    }

    @Test
    @DisplayName("returns *** for null")
    void shouldReturnMaskedForNull() {
      assertEquals("***", PiiMaskingUtil.maskId(null));
    }

    @Test
    @DisplayName("returns *** for short ID (8 chars or less)")
    void shouldReturnMaskedForShortId() {
      assertEquals("***", PiiMaskingUtil.maskId("12345678"));
    }

    @Test
    @DisplayName("returns *** for empty string")
    void shouldReturnMaskedForEmpty() {
      assertEquals("***", PiiMaskingUtil.maskId(""));
    }

    @Test
    @DisplayName("masks ID with exactly 9 characters")
    void shouldMaskNineCharId() {
      assertEquals("12345678***", PiiMaskingUtil.maskId("123456789"));
    }
  }

  @Nested
  @DisplayName("maskPII")
  class MaskPII {

    @Test
    @DisplayName("returns 'Unknown error' for null")
    void shouldReturnUnknownErrorForNull() {
      assertEquals("Unknown error", PiiMaskingUtil.maskPII(null));
    }

    @Test
    @DisplayName("masks email addresses in message")
    void shouldMaskEmailInMessage() {
      String result = PiiMaskingUtil.maskPII("User user@example.com not found");
      assertEquals("User ***@***.*** not found", result);
    }

    @Test
    @DisplayName("masks UUIDs in message")
    void shouldMaskUuidInMessage() {
      String result =
          PiiMaskingUtil.maskPII("Resource 06702f15-1439-4958-b069-2ac5716c7a5c not found");
      assertEquals("Resource ***-***-*** not found", result);
    }

    @Test
    @DisplayName("masks file paths in message")
    void shouldMaskFilePathInMessage() {
      String result = PiiMaskingUtil.maskPII("Error reading /etc/keycloak/config.json");
      assertEquals("Error reading /***", result);
    }

    @Test
    @DisplayName("masks multiple PII patterns in single message")
    void shouldMaskMultiplePatterns() {
      String result =
          PiiMaskingUtil.maskPII(
              "User admin@test.org with id a1b2c3d4-e5f6-7890-abcd-ef1234567890 at /home/user");
      assertEquals("User ***@***.*** with id ***-***-*** at /***", result);
    }

    @Test
    @DisplayName("preserves message without PII")
    void shouldPreserveCleanMessage() {
      String result = PiiMaskingUtil.maskPII("Connection refused");
      assertEquals("Connection refused", result);
    }

    @Test
    @DisplayName("handles empty string")
    void shouldHandleEmptyString() {
      assertEquals("", PiiMaskingUtil.maskPII(""));
    }
  }
}
