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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CoreUrn")
class CoreUrnTest {

  private static final UUID ID = UUID.fromString("a1b2c3d4-e5f6-4890-abcd-ef1234567890");
  private static final String STRUCTURE =
      "urn:core:platform:civitas:datastructure:common:" + ID + ":1.0.0";

  @ParameterizedTest
  @ValueSource(
      strings = {
        "urn:core:platform:civitas:datastructure:common:a1b2c3d4-e5f6-4890-abcd-ef1234567890:1.0.0",
        "urn:core:tenant:stadt-muenster:mapping:mobility.parking:00000000-0000-0000-0000-000000000001:2.1.0",
        "urn:core:standard:xoev:element:environment.air.quality:8cc31216-5417-4d0a-abea-dde0659ce00d:10.20.30",
      })
  @DisplayName("accepts well-formed URNs across the scope/artifact-type vocabularies")
  void accepts(String urn) {
    assertTrue(CoreUrn.isValid(urn));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // an HTTP URL, not a URN
        "https://civitasconnect.digital/core/WeatherModel",
        // missing version segment
        "urn:core:platform:civitas:datastructure:common:a1b2c3d4-e5f6-4890-abcd-ef1234567890",
        // scope not in vocabulary
        "urn:core:galaxy:civitas:datastructure:common:a1b2c3d4-e5f6-4890-abcd-ef1234567890:1.0.0",
        // artifact-type not in vocabulary
        "urn:core:platform:civitas:widget:common:a1b2c3d4-e5f6-4890-abcd-ef1234567890:1.0.0",
        // owner not lowercase
        "urn:core:platform:Civitas:datastructure:common:a1b2c3d4-e5f6-4890-abcd-ef1234567890:1.0.0",
        // UUID uppercase
        "urn:core:platform:civitas:datastructure:common:A1B2C3D4-E5F6-4890-ABCD-EF1234567890:1.0.0",
        // UUID without hyphens
        "urn:core:platform:civitas:datastructure:common:a1b2c3d4e5f64890abcdef1234567890:1.0.0",
        // a name segment and a short token where the UUID belongs
        "urn:core:platform:civitas:datastructure:common:WeatherModel:2dmtus8w40:1.0.0",
        // version not semver
        "urn:core:platform:civitas:datastructure:common:a1b2c3d4-e5f6-4890-abcd-ef1234567890:1.0",
      })
  @DisplayName("rejects malformed URNs and out-of-vocabulary segments")
  void rejects(String urn) {
    assertFalse(CoreUrn.isValid(urn));
  }

  @Test
  @DisplayName("rejects null")
  void rejectsNull() {
    assertFalse(CoreUrn.isValid(null));
  }

  @Test
  @DisplayName("sameStructureVersion matches a DataStructure and its root Element")
  void sameStructureVersion_matchesTheRootElement() {
    assertTrue(
        CoreUrn.sameStructureVersion(
            STRUCTURE, "urn:core:platform:civitas:element:common:" + ID + ":1.0.0"));
  }

  @Test
  @DisplayName("sameStructureVersion separates two versions of one structure")
  void sameStructureVersion_separatesVersions() {
    assertFalse(
        CoreUrn.sameStructureVersion(
            STRUCTURE, "urn:core:platform:civitas:datastructure:common:" + ID + ":2.0.0"));
  }

  @Test
  @DisplayName("sameStructureVersion separates two structures at the same version")
  void sameStructureVersion_separatesStructures() {
    assertFalse(
        CoreUrn.sameStructureVersion(
            STRUCTURE,
            "urn:core:platform:civitas:datastructure:common:00000000-0000-0000-0000-000000000001:1.0.0"));
  }

  @Test
  @DisplayName("sameStructureVersion rejects a malformed or absent URN")
  void sameStructureVersion_rejectsUnverifiable() {
    assertFalse(CoreUrn.sameStructureVersion(STRUCTURE, "not-a-core-urn"));
    assertFalse(CoreUrn.sameStructureVersion(STRUCTURE, null));
    assertFalse(CoreUrn.sameStructureVersion(null, null));
  }

  @Test
  @DisplayName("logicalUrn strips the trailing SemVer version segment")
  void logicalUrn_stripsVersion() {
    assertEquals(
        "urn:core:platform:civitas:datastructure:common:" + ID, CoreUrn.logicalUrn(STRUCTURE));
  }

  @Test
  @DisplayName(
      "logicalUrn returns an already-logical URN (no version tail) unchanged, and null for null")
  void logicalUrn_passthroughAndNull() {
    String logical = "urn:core:platform:civitas:datastructure:common:" + ID;
    assertEquals(logical, CoreUrn.logicalUrn(logical));
    assertEquals("ds-1", CoreUrn.logicalUrn("ds-1"));
    assertEquals(null, CoreUrn.logicalUrn(null));
  }

  @Test
  @DisplayName("sameArtifact matches verbatim and across a version drift, but not across artifacts")
  void sameArtifact() {
    String v1 = "urn:core:tenant:stadt:mapping:mobility:" + ID + ":1.0.0";
    String v2 = "urn:core:tenant:stadt:mapping:mobility:" + ID + ":2.3.4";
    String other =
        "urn:core:tenant:stadt:mapping:mobility:00000000-0000-0000-0000-000000000002:1.0.0";
    assertTrue(CoreUrn.sameArtifact(v1, v1));
    assertTrue(CoreUrn.sameArtifact(v1, v2));
    assertTrue(CoreUrn.sameArtifact("ds-1", "ds-1"));
    assertFalse(CoreUrn.sameArtifact(v1, other));
    assertFalse(CoreUrn.sameArtifact(v1, null));
    assertFalse(CoreUrn.sameArtifact(null, v1));
  }
}
