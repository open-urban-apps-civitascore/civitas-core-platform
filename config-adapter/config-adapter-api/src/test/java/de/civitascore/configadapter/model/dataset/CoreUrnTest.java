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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CoreUrn")
class CoreUrnTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "urn:core:platform:civitas:datastructure:common:WeatherModel:2dmtus8w40:1.0.0",
        "urn:core:tenant:stadt-muenster:mapping:mobility.parking:ParkingSensor:0000000001:2.1.0",
        "urn:core:standard:xoev:element:environment.air.quality:GeoPoint:abcdefghij:10.20.30",
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
        "urn:core:platform:civitas:datastructure:common:WeatherModel:2dmtus8w40",
        // scope not in vocabulary
        "urn:core:galaxy:civitas:datastructure:common:WeatherModel:2dmtus8w40:1.0.0",
        // artifact-type not in vocabulary
        "urn:core:platform:civitas:widget:common:WeatherModel:2dmtus8w40:1.0.0",
        // owner not lowercase
        "urn:core:platform:Civitas:datastructure:common:WeatherModel:2dmtus8w40:1.0.0",
        // space in name
        "urn:core:platform:civitas:datastructure:common:Weather Model:2dmtus8w40:1.0.0",
        // disambiguator too short
        "urn:core:platform:civitas:datastructure:common:WeatherModel:2dmtus8w4:1.0.0",
        // disambiguator uppercase
        "urn:core:platform:civitas:datastructure:common:WeatherModel:2DMTUS8W40:1.0.0",
        // version not semver
        "urn:core:platform:civitas:datastructure:common:WeatherModel:2dmtus8w40:1.0",
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

  @ParameterizedTest
  @CsvSource({
    "a1b2c3d4-e5f6-7890-abcd-ef1234567890, 2dmtus8w40",
    "00000000-0000-0000-0000-000000000001, 0000000001",
    "8cc31216-5417-4d0a-abea-dde0659ce00d, ggb6odzea5",
  })
  @DisplayName("derives the same base36 disambiguator as the frontend")
  void disambiguatorFor(String id, String expected) {
    assertEquals(expected, CoreUrn.disambiguatorFor(UUID.fromString(id)));
  }

  @Test
  @DisplayName("matchesId accepts a URN whose disambiguator was derived from the id")
  void matchesId_accepts() {
    UUID id = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    assertTrue(
        CoreUrn.matchesId(
            "urn:core:platform:civitas:datastructure:common:WeatherModel:2dmtus8w40:1.0.0", id));
  }

  @Test
  @DisplayName("matchesId rejects a well-formed URN whose disambiguator belongs to another id")
  void matchesId_rejectsForeignDisambiguator() {
    UUID id = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    assertFalse(
        CoreUrn.matchesId(
            "urn:core:platform:civitas:datastructure:common:WeatherModel:0000000001:1.0.0", id));
  }

  @Test
  @DisplayName("matchesId rejects a malformed URN")
  void matchesId_rejectsMalformed() {
    UUID id = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    assertFalse(CoreUrn.matchesId("not-a-core-urn", id));
  }

  @Test
  @DisplayName("matchesId rejects a non-datastructure URN reusing the correct disambiguator")
  void matchesId_rejectsForeignArtifactType() {
    UUID id = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    assertFalse(
        CoreUrn.matchesId(
            "urn:core:platform:civitas:mapping:common:WeatherModel:2dmtus8w40:1.0.0", id));
  }

  @Test
  @DisplayName("matchesId rejects a non-platform-scoped URN reusing the correct disambiguator")
  void matchesId_rejectsForeignScope() {
    UUID id = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    assertFalse(
        CoreUrn.matchesId(
            "urn:core:tenant:civitas:datastructure:common:WeatherModel:2dmtus8w40:1.0.0", id));
  }

  @Test
<<<<<<< ours
  @DisplayName("sameStructureVersion ignores a renamed display-name segment")
  void sameStructureVersion_ignoresTheName() {
    // A rename leaves both the structure and its shape untouched, so a caller comparing identity
    // must not see a difference — otherwise a rename fails deploys that were correct.
    assertTrue(
        CoreUrn.sameStructureVersion(
            "urn:core:platform:civitas:datastructure:common:WeatherModel:2dmtus8w40:1.0.0",
            "urn:core:platform:civitas:datastructure:common:Renamed:2dmtus8w40:1.0.0"));
  }

  @Test
  @DisplayName("sameStructureVersion separates two versions of one structure")
  void sameStructureVersion_separatesVersions() {
    assertFalse(
        CoreUrn.sameStructureVersion(
            "urn:core:platform:civitas:datastructure:common:WeatherModel:2dmtus8w40:1.0.0",
            "urn:core:platform:civitas:datastructure:common:WeatherModel:2dmtus8w40:2.0.0"));
  }

  @Test
  @DisplayName("sameStructureVersion separates two structures sharing a name and version")
  void sameStructureVersion_separatesStructures() {
    assertFalse(
        CoreUrn.sameStructureVersion(
            "urn:core:platform:civitas:datastructure:common:WeatherModel:2dmtus8w40:1.0.0",
            "urn:core:platform:civitas:datastructure:common:WeatherModel:0000000001:1.0.0"));
  }

  @Test
  @DisplayName("sameStructureVersion rejects a malformed or absent URN")
  void sameStructureVersion_rejectsUnverifiable() {
    String valid = "urn:core:platform:civitas:datastructure:common:WeatherModel:2dmtus8w40:1.0.0";
    assertFalse(CoreUrn.sameStructureVersion(valid, "not-a-core-urn"));
    assertFalse(CoreUrn.sameStructureVersion(valid, null));
    assertFalse(CoreUrn.sameStructureVersion(null, null));
=======
  @DisplayName("logicalUrn strips the trailing SemVer version segment")
  void logicalUrn_stripsVersion() {
    assertEquals(
        "urn:core:tenant:stadt:mapping:mobility:Parking:0000000001",
        CoreUrn.logicalUrn("urn:core:tenant:stadt:mapping:mobility:Parking:0000000001:2.1.0"));
  }

  @Test
  @DisplayName(
      "logicalUrn returns an already-logical URN (no version tail) unchanged, and null for null")
  void logicalUrn_passthroughAndNull() {
    assertEquals(
        "urn:core:tenant:stadt:mapping:mobility:Parking:0000000001",
        CoreUrn.logicalUrn("urn:core:tenant:stadt:mapping:mobility:Parking:0000000001"));
    assertEquals("ds-1", CoreUrn.logicalUrn("ds-1"));
    assertEquals(null, CoreUrn.logicalUrn(null));
  }

  @Test
  @DisplayName("sameArtifact matches verbatim and across a version drift, but not across artifacts")
  void sameArtifact() {
    String v1 = "urn:core:tenant:stadt:mapping:mobility:Parking:0000000001:1.0.0";
    String v2 = "urn:core:tenant:stadt:mapping:mobility:Parking:0000000001:2.3.4";
    String other = "urn:core:tenant:stadt:mapping:mobility:Parking:0000000002:1.0.0";
    assertTrue(CoreUrn.sameArtifact(v1, v1));
    assertTrue(CoreUrn.sameArtifact(v1, v2));
    assertTrue(CoreUrn.sameArtifact("ds-1", "ds-1"));
    assertFalse(CoreUrn.sameArtifact(v1, other));
    assertFalse(CoreUrn.sameArtifact(v1, null));
    assertFalse(CoreUrn.sameArtifact(null, v1));
>>>>>>> theirs
  }
}
