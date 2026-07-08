/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.nifi.flow.SinkResolutionContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code parseSpec}'s schema→match-key resolution — the seam every mapped FROST deployment
 * crosses: a wrong result here deploys wrong {@code $filter}s (cross-entity dedup) or fails every
 * publish.
 */
class FrostSinkStageTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final FrostSinkStage stage = new FrostSinkStage("http://frost:8080/v1.1");
  private final SinkResolutionContext ctx = new SinkResolutionContext("7");

  private static Map<String, Object> json(String json) {
    try {
      return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** A wrapper-rooted Thing structure, parameterized on the two entity classes' properties. */
  private static Map<String, Object> datasinkWith(String thingProps, String datastreamProps) {
    return json(
        """
        { "dataStructure": {
            "title": "SensorThingsDataModel",
            "properties": { "thing": { "$ref": "#/$defs/Thing" } },
            "$defs": {
              "Thing": { "properties": { %s
                  "Datastreams": { "type": "array", "items": { "$ref": "#/$defs/Datastream" } } } },
              "Datastream": { "properties": { %s "name": { "type": "string" } } } } } }
        """
            .formatted(thingProps, datastreamProps));
  }

  @Test
  void primaryKeyMarkerWinsOverACoexistingReferenceAttribute() throws Exception {
    FrostSinkSpec spec =
        stage.parseSpec(
            datasinkWith(
                "\"stationId\": { \"type\": \"string\", \"x-core-primaryKey\": true },"
                    + " \"reference\": { \"type\": \"string\" },",
                "\"dsId\": { \"type\": \"string\", \"x-core-primaryKey\": true },"),
            ctx);

    assertEquals(List.of("stationId"), spec.staKeys().thingKeys());
    assertEquals(List.of("dsId"), spec.staKeys().datastreamKeys());
  }

  @Test
  void fallsBackToADeclaredReferenceAttributeWithoutAMarker() throws Exception {
    FrostSinkSpec spec =
        stage.parseSpec(
            datasinkWith(
                "\"reference\": { \"type\": \"string\" },",
                "\"reference\": { \"type\": \"string\" },"),
            ctx);

    assertEquals(List.of("reference"), spec.staKeys().thingKeys());
    assertEquals(List.of("reference"), spec.staKeys().datastreamKeys());
  }

  @Test
  void yieldsEmptyKeysWhenNeitherMarkerNorReferenceExists() throws Exception {
    FrostSinkSpec spec = stage.parseSpec(datasinkWith("", ""), ctx);

    assertTrue(spec.staKeys().thingKeys().isEmpty());
    assertTrue(spec.staKeys().datastreamKeys().isEmpty());
  }

  @Test
  void missingDatastreamsClassYieldsEmptyDatastreamKeys() throws Exception {
    Map<String, Object> datasink =
        json(
            """
            { "dataStructure": {
                "properties": { "thing": { "$ref": "#/$defs/Thing" } },
                "$defs": { "Thing": { "properties": {
                    "reference": { "type": "string", "x-core-primaryKey": true } } } } } }
            """);

    FrostSinkSpec spec = stage.parseSpec(datasink, ctx);

    assertEquals(List.of("reference"), spec.staKeys().thingKeys());
    assertTrue(spec.staKeys().datastreamKeys().isEmpty());
  }

  @Test
  void missingDataStructureYieldsNullKeysForPassthrough() throws Exception {
    FrostSinkSpec spec = stage.parseSpec(Map.of("configuration", Map.of()), ctx);
    assertNull(spec.staKeys());
  }

  @Test
  void aStructurallyBrokenSchemaFailsThePlanInsteadOfDegradingToNoKeys() {
    // An item-less Datastreams array is a broken schema, not an absent class — degrading it to
    // "no keys" would surface as the misleading "declares no match key" compiler error.
    Map<String, Object> datasink =
        json(
            """
            { "dataStructure": {
                "properties": { "thing": { "$ref": "#/$defs/Thing" } },
                "$defs": { "Thing": { "properties": {
                    "reference": { "type": "string", "x-core-primaryKey": true },
                    "Datastreams": { "type": "array" } } } } } }
            """);

    FatalAdapterException ex =
        assertThrows(FatalAdapterException.class, () -> stage.parseSpec(datasink, ctx));
    assertTrue(
        ex.getMessage().contains("Datastreams") && ex.getMessage().contains("no items"),
        "the broken-schema error must name the offending array property, was: " + ex.getMessage());
  }
}
