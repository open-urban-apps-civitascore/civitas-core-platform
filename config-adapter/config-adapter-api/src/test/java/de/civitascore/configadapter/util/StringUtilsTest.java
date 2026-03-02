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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StringUtils")
class StringUtilsTest {

  @Test
  @DisplayName("truncate returns null for null input")
  void truncate_null_returnsNull() {
    assertNull(StringUtils.truncate(null, 50));
  }

  @Test
  @DisplayName("truncate returns empty string unchanged")
  void truncate_empty_returnsEmpty() {
    assertEquals("", StringUtils.truncate("", 50));
  }

  @Test
  @DisplayName("truncate returns short string unchanged")
  void truncate_shortString_returnsUnchanged() {
    assertEquals("hello", StringUtils.truncate("hello", 50));
  }

  @Test
  @DisplayName("truncate returns exact-length string unchanged")
  void truncate_exactLength_returnsUnchanged() {
    String s = "abcde";
    assertEquals(s, StringUtils.truncate(s, 5));
  }

  @Test
  @DisplayName("truncate cuts long string to maxLen")
  void truncate_longString_cutsToMaxLen() {
    assertEquals("abc", StringUtils.truncate("abcdef", 3));
  }

  @Test
  @DisplayName("truncate with zero maxLen returns empty")
  void truncate_zeroMaxLen_returnsEmpty() {
    assertEquals("", StringUtils.truncate("hello", 0));
  }
}
