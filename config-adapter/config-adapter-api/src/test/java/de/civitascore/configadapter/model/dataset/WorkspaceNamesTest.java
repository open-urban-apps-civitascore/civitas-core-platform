/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WorkspaceNames")
class WorkspaceNamesTest {

  @Test
  @DisplayName("fromDatasetId lowercases and replaces non [a-z0-9_] with underscore")
  void fromDatasetId_normalizes() {
    assertEquals("ds_123", WorkspaceNames.fromDatasetId("ds-123"));
    assertEquals("my_dataset_1", WorkspaceNames.fromDatasetId("My Dataset 1"));
    assertEquals("ds_abc123", WorkspaceNames.fromDatasetId("DS:ABC123"));
  }

  @Test
  @DisplayName("fromDatasetId prefixes digit-initial names with ds_ (XML NCName rule)")
  void fromDatasetId_prefixesDigitInitial() {
    // GeoServer publishes the workspace as an XML namespace prefix; an NCName must not start
    // with a digit, so digit-initial UUIDs get a letter prefix.
    assertEquals(
        "ds_550e8400_e29b_41d4_a716_446655440000",
        WorkspaceNames.fromDatasetId("550e8400-e29b-41d4-a716-446655440000"));
    assertEquals("ds_1abc", WorkspaceNames.fromDatasetId("1abc"));
    // Letter- and underscore-initial names are valid NCNames and stay unprefixed.
    assertEquals(
        "f47ac10b_58cc_4372_a567_0e02b2c3d479",
        WorkspaceNames.fromDatasetId("f47ac10b-58cc-4372-a567-0e02b2c3d479"));
    assertEquals("_1abc", WorkspaceNames.fromDatasetId("_1abc"));
  }

  @Test
  @DisplayName("fromDatasetId keeps already-normalized ids unchanged")
  void fromDatasetId_idempotentOnNormalizedInput() {
    String normalized = "ds_abc_123";
    assertEquals(normalized, WorkspaceNames.fromDatasetId(normalized));
    // A previously prefixed digit-initial name is letter-initial and thus stable too.
    String prefixed = WorkspaceNames.fromDatasetId("550e8400-e29b-41d4-a716-446655440000");
    assertEquals(prefixed, WorkspaceNames.fromDatasetId(prefixed));
  }

  @Test
  @DisplayName("fromDatasetId rejects null or blank dataset id")
  void fromDatasetId_rejectsBlank() {
    assertThrows(IllegalArgumentException.class, () -> WorkspaceNames.fromDatasetId(null));
    assertThrows(IllegalArgumentException.class, () -> WorkspaceNames.fromDatasetId(""));
    assertThrows(IllegalArgumentException.class, () -> WorkspaceNames.fromDatasetId("   "));
  }
}
