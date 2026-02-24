/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.frost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FrostUtilsTest {

  @Nested
  @DisplayName("extractIdFromLocation")
  class ExtractIdFromLocation {

    @Test
    @DisplayName("extracts numeric ID from standard Location header")
    void shouldExtractNumericId() {
      String header = "http://localhost:8080/v1.1/Projects(42)";

      assertEquals("42", FrostUtils.extractIdFromLocation(header));
    }

    @Test
    @DisplayName("extracts UUID from Location header")
    void shouldExtractUuid() {
      String header = "http://frost:8080/v1.1/Things(a1b2c3d4-e5f6-7890-abcd-ef1234567890)";

      assertEquals(
          "a1b2c3d4-e5f6-7890-abcd-ef1234567890", FrostUtils.extractIdFromLocation(header));
    }

    @Test
    @DisplayName("extracts ID from nested entity path")
    void shouldExtractIdFromNestedPath() {
      String header = "http://localhost:8080/v1.1/Projects(1)/Things(99)";

      assertEquals("99", FrostUtils.extractIdFromLocation(header));
    }

    @Test
    @DisplayName("extracts string ID with single quotes")
    void shouldExtractStringIdWithQuotes() {
      String header = "http://localhost:8080/v1.1/Projects('my-project')";

      assertEquals("'my-project'", FrostUtils.extractIdFromLocation(header));
    }

    @Test
    @DisplayName("throws on null header")
    void shouldThrowOnNullHeader() {
      IllegalStateException ex =
          assertThrows(IllegalStateException.class, () -> FrostUtils.extractIdFromLocation(null));

      assertEquals(
          "FROST response missing Location header — cannot extract entity ID", ex.getMessage());
    }

    @Test
    @DisplayName("throws on blank header")
    void shouldThrowOnBlankHeader() {
      assertThrows(IllegalStateException.class, () -> FrostUtils.extractIdFromLocation("   "));
    }

    @Test
    @DisplayName("throws on empty header")
    void shouldThrowOnEmptyHeader() {
      assertThrows(IllegalStateException.class, () -> FrostUtils.extractIdFromLocation(""));
    }

    @Test
    @DisplayName("throws on header without parentheses")
    void shouldThrowOnHeaderWithoutParentheses() {
      IllegalStateException ex =
          assertThrows(
              IllegalStateException.class,
              () -> FrostUtils.extractIdFromLocation("http://localhost/v1.1/Projects/42"));

      assertEquals(
          "FROST Location header has unexpected format: http://localhost/v1.1/Projects/42",
          ex.getMessage());
    }
  }
}
