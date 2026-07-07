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
}
