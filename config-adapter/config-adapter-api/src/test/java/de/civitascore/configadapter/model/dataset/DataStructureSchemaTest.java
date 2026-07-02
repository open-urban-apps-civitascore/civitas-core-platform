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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataStructureSchemaTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static Map<String, Object> json(String json) {
    try {
      return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void singleMarkerYieldsOnePrimaryKey() {
    assertEquals(
        List.of("id"),
        DataStructureSchema.primaryKeyColumns(
            json(
                """
                { "properties": {
                    "id":   { "type": "string", "x-core-primaryKey": true },
                    "name": { "type": "string" } } }
                """)));
  }

  @Test
  void multipleMarkersYieldCompositeKeyInDeclarationOrder() {
    assertEquals(
        List.of("tenant", "id"),
        DataStructureSchema.primaryKeyColumns(
            json(
                """
                { "properties": {
                    "tenant": { "type": "string", "x-core-primaryKey": true },
                    "name":   { "type": "string" },
                    "id":     { "type": "string", "x-core-primaryKey": true } } }
                """)));
  }

  @Test
  void noMarkerYieldsEmpty() {
    assertTrue(
        DataStructureSchema.primaryKeyColumns(
                json("{ \"properties\": { \"id\": { \"type\": \"string\" } } }"))
            .isEmpty());
  }

  @Test
  void nullSchemaYieldsEmpty() {
    assertTrue(DataStructureSchema.primaryKeyColumns(null).isEmpty());
  }

  @Test
  void resolvesMarkerInTitleMatchedDefinition() {
    assertEquals(
        List.of("station_id"),
        DataStructureSchema.primaryKeyColumns(
            json(
                """
                { "title": "Observation",
                  "definitions": { "Observation": { "properties": {
                      "station_id": { "type": "string", "x-core-primaryKey": true },
                      "t": { "type": "string" } } } } }
                """)));
  }

  @Test
  void resolvesMarkerInSingleDefsDefinition() {
    assertEquals(
        List.of("id"),
        DataStructureSchema.primaryKeyColumns(
            json(
                """
                { "$defs": { "Thing": { "properties": {
                    "id": { "type": "string", "x-core-primaryKey": true } } } } }
                """)));
  }

  @Test
  void ambiguousMultiDefinitionsYieldEmpty() {
    // two definitions carry properties and none matches the (absent) title → cannot pick one →
    // empty, so no PK is forced on a guessed definition
    assertTrue(
        DataStructureSchema.primaryKeyColumns(
                json(
                    """
                    { "definitions": {
                        "A": { "properties": { "a": { "x-core-primaryKey": true } } },
                        "B": { "properties": { "b": { "type": "string" } } } } }
                    """))
            .isEmpty());
  }

  @Test
  void stringTrueIsNotTreatedAsMarker() {
    // strict Boolean.TRUE check — the JSON string "true" must NOT be read as the marker
    assertTrue(
        DataStructureSchema.primaryKeyColumns(
                json("{ \"properties\": { \"id\": { \"x-core-primaryKey\": \"true\" } } }"))
            .isEmpty());
  }

  @Test
  void markerOnRefPropertyIsDropped() {
    // a $ref property maps to a geometry/JSONB column, which cannot back a B-tree primary key, so
    // its marker is ignored rather than yielding a key both adapters would fail to create
    assertEquals(
        List.of("id"),
        DataStructureSchema.primaryKeyColumns(
            json(
                """
                { "properties": {
                    "id":  { "type": "string", "x-core-primaryKey": true },
                    "geo": { "$ref": "https://geojson.org/schema/Point.json",
                             "x-core-primaryKey": true } } }
                """)));
  }

  @Test
  void wrapperRootResolvesToTheReferencedClass() {
    var resolved =
        DataStructureSchema.resolveDefinition(
            json(
                """
                { "title": "MyStructure",
                  "type": "object",
                  "properties": { "trafficsensor": { "$ref": "#/$defs/TrafficSensor" } },
                  "$defs": {
                    "TrafficSensor": {
                      "properties": {
                        "stationId":   { "type": "string" },
                        "temperature": { "type": "number" } },
                      "required": ["stationId"] } } }
                """));
    assertEquals(List.of("stationId", "temperature"), List.copyOf(resolved.properties().keySet()));
    assertEquals(List.of("stationId"), List.copyOf(resolved.required()));
  }

  @Test
  void wrapperRootResolvesInheritanceOfTheReferencedClass() {
    var resolved =
        DataStructureSchema.resolveDefinition(
            json(
                """
                { "title": "MyStructure",
                  "properties": { "dog": { "$ref": "#/$defs/Dog" } },
                  "$defs": {
                    "Animal": { "properties": { "id": { "type": "string" } } },
                    "Dog": {
                      "allOf": [ { "$ref": "#/$defs/Animal" } ],
                      "properties": { "breed": { "type": "string" } } } } }
                """));
    assertEquals(List.of("id", "breed"), List.copyOf(resolved.properties().keySet()));
  }

  @Test
  void wrapperMarkerResolvesThroughToTheReferencedClass() {
    assertEquals(
        List.of("stationId"),
        DataStructureSchema.primaryKeyColumns(
            json(
                """
                { "title": "MyStructure",
                  "properties": { "trafficsensor": { "$ref": "#/$defs/TrafficSensor" } },
                  "$defs": {
                    "TrafficSensor": {
                      "properties": {
                        "stationId":   { "type": "string", "x-core-primaryKey": true },
                        "temperature": { "type": "number" } } } } }
                """)));
  }

  @Test
  void markerOnObjectOrArrayPropertyIsDropped() {
    // object/array properties map to JSONB, which likewise cannot back a primary key
    assertTrue(
        DataStructureSchema.primaryKeyColumns(
                json(
                    """
                    { "properties": {
                        "nested": { "type": "object", "x-core-primaryKey": true },
                        "list":   { "type": "array", "x-core-primaryKey": true } } }
                    """))
            .isEmpty());
  }

  @Test
  void externalRefParentInAllOfIsSkippedNotRejected() {
    // A class whose allOf lists an external (non-local) $ref parent — e.g. a GeoJSON geometry
    // schema — must resolve its own properties rather than throw; only local $refs are followed.
    DataStructureSchema.ResolvedDefinition resolved =
        DataStructureSchema.resolveDefinition(
            json(
                """
                { "title": "Station",
                  "allOf": [
                    { "$ref": "https://geojson.org/schema/Point.json" },
                    { "properties": { "station_id": { "type": "string" } } } ] }
                """));
    assertTrue(resolved.properties().containsKey("station_id"));
  }

  @Test
  void singleRefPropertyWithSiblingsIsNotAWrapper() {
    // A single property that carries more than a bare $ref (here a crs) is a real column, not the
    // wrapper indirection, so it is not resolved through to the referenced class.
    var resolved =
        DataStructureSchema.resolveDefinition(
            json(
                """
                { "title": "Sensor",
                  "properties": {
                    "location": { "$ref": "#/$defs/Point", "crs": "EPSG:25832" } },
                  "$defs": { "Point": { "properties": { "x": { "type": "number" } } } } }
                """));
    assertEquals(List.of("location"), List.copyOf(resolved.properties().keySet()));
  }

  @Test
  void wrapperRootWithMissingRefTargetIsRejected() {
    // A wrapper root pointing at a $ref absent from $defs is a broken schema; rejecting it here
    // stops resolveDefinition from silently deriving a table from the unresolved wrapper.
    assertThrows(
        IllegalArgumentException.class,
        () ->
            DataStructureSchema.resolveDefinition(
                json(
                    """
                    { "title": "MyStructure",
                      "properties": { "trafficsensor": { "$ref": "#/$defs/TrafficSensor" } },
                      "$defs": {} }
                    """)));
  }

  @Test
  void singleScalarPropertyIsNotAWrapper() {
    var resolved =
        DataStructureSchema.resolveDefinition(
            json("{ \"title\": \"T\", \"properties\": { \"id\": { \"type\": \"string\" } } }"));
    assertEquals(List.of("id"), List.copyOf(resolved.properties().keySet()));
  }
}
