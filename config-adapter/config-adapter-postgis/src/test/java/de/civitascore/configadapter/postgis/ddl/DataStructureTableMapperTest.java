/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.postgis.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.model.postgis.ColumnConfig;
import de.civitascore.configadapter.model.postgis.ColumnType;
import de.civitascore.configadapter.model.postgis.GeometryType;
import de.civitascore.configadapter.postgis.ddl.DataStructureTableMapper.TableColumns;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DataStructureTableMapperTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static Map<String, Object> json(String json) {
    try {
      return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static Map<String, ColumnConfig> byName(TableColumns derived) {
    return derived.columns().stream()
        .collect(Collectors.toMap(ColumnConfig::name, Function.identity()));
  }

  @Test
  void definitionWithRequiredMapsToNotNullColumns() {
    Map<String, Object> schema =
        json(
            """
            { "$id": "urn:core:datastructure:08e34478-8aa3-42a5-9431-d805e209ceec",
              "title": "Observation",
              "definitions": {
                "Observation": {
                  "type": "object",
                  "properties": {
                    "station_id": { "type": "string" },
                    "temperature": { "type": "string" }
                  },
                  "required": ["station_id", "temperature"]
                } } }
            """);

    Map<String, ColumnConfig> named =
        byName(DataStructureTableMapper.deriveColumns(schema, Set.of()));

    assertEquals(2, named.size());
    assertEquals(ColumnType.TEXT, named.get("station_id").type());
    assertEquals(false, named.get("station_id").nullable());
    assertEquals(false, named.get("temperature").nullable());
  }

  @Test
  void rootPropertiesWithFormatsAndScalarTypes() {
    Map<String, Object> schema =
        json(
            """
            { "properties": {
                "id":       { "type": "string", "format": "uuid" },
                "observed": { "type": "string", "format": "date-time" },
                "day":      { "type": "string", "format": "date" },
                "count":    { "type": "integer" },
                "value":    { "type": "number" },
                "active":   { "type": "boolean" },
                "extra":    { "type": "object" } } }
            """);

    Map<String, ColumnConfig> named =
        byName(DataStructureTableMapper.deriveColumns(schema, Set.of()));

    assertEquals(ColumnType.UUID, named.get("id").type());
    assertEquals(ColumnType.TIMESTAMPTZ, named.get("observed").type());
    assertEquals(ColumnType.DATE, named.get("day").type());
    assertEquals(ColumnType.BIGINT, named.get("count").type());
    assertEquals(ColumnType.DOUBLE_PRECISION, named.get("value").type());
    assertEquals(ColumnType.BOOLEAN, named.get("active").type());
    assertEquals(ColumnType.JSONB, named.get("extra").type());
    assertTrue(named.get("id").isNullable());
  }

  @Test
  void geometryRefBecomesGeometryColumnWithDefaultSrid() {
    // The shape Model Atlas produces once the geometry package resolves: the property references
    // an inlined geometry definition. Verified against the schema generator's $ref emission.
    Map<String, Object> schema =
        json(
            """
            { "title": "GeoProbe",
              "definitions": {
                "Point": { "type": "object" },
                "Observation": {
                  "type": "object",
                  "properties": {
                    "station_id": { "type": "string" },
                    "location":   { "$ref": "#/definitions/Point" }
                  },
                  "required": ["location"]
                } } }
            """);

    TableColumns derived = DataStructureTableMapper.deriveColumns(schema, Set.of());

    assertEquals(1, derived.columns().size());
    assertEquals("station_id", derived.columns().get(0).name());
    assertEquals(1, derived.geometryColumns().size());
    var geometry = derived.geometryColumns().get(0);
    assertEquals("location", geometry.name());
    assertEquals(GeometryType.POINT, geometry.geometryType());
    assertEquals(4326, geometry.srid());
    assertEquals(false, geometry.nullable());
  }

  @Test
  void externalGeometryRefIsRecognized() {
    Map<String, Object> schema =
        json(
            """
            { "properties": {
                "area": { "$ref": "http://models.civitasconnect.org/models/postgis/1.0#//MultiPolygon" } } }
            """);

    TableColumns derived = DataStructureTableMapper.deriveColumns(schema, Set.of());

    assertEquals(GeometryType.MULTIPOLYGON, derived.geometryColumns().get(0).geometryType());
  }

  @Test
  void nonGeometryRefMapsToJsonb() {
    // Includes the generator's unresolved-name bug ("#/definitions/null"): an unidentifiable
    // reference is a nested object, not a geometry.
    Map<String, Object> schema =
        json(
            """
            { "properties": {
                "address": { "$ref": "#/definitions/Address" },
                "broken":  { "$ref": "#/definitions/null" } } }
            """);

    TableColumns derived = DataStructureTableMapper.deriveColumns(schema, Set.of());

    Map<String, ColumnConfig> named = byName(derived);
    assertEquals(ColumnType.JSONB, named.get("address").type());
    assertEquals(ColumnType.JSONB, named.get("broken").type());
    assertTrue(derived.geometryColumns().isEmpty());
  }

  @Test
  void selectsThePropertyCarryingDefinitionAmongInlinedTypeDefinitions() {
    // Inlined referenced types (no properties) must not shadow the table definition, even when
    // the title matches neither.
    Map<String, Object> schema =
        json(
            """
            { "title": "GeoProbeLocal",
              "definitions": {
                "Point":       { "type": "object" },
                "Observation": { "properties": { "value": { "type": "number" } } } } }
            """);

    TableColumns derived = DataStructureTableMapper.deriveColumns(schema, Set.of());

    assertEquals(1, derived.columns().size());
    assertEquals("value", derived.columns().get(0).name());
  }

  @Test
  void selectsDefinitionMatchingTitleAmongSeveral() {
    Map<String, Object> schema =
        json(
            """
            { "title": "Observation",
              "definitions": {
                "Station":     { "properties": { "name": { "type": "string" } } },
                "Observation": { "properties": { "value": { "type": "number" } } } } }
            """);

    TableColumns derived = DataStructureTableMapper.deriveColumns(schema, Set.of());

    assertEquals(1, derived.columns().size());
    assertEquals("value", derived.columns().get(0).name());
  }

  @Test
  void excludesExplicitGeometryColumnNames() {
    Map<String, Object> schema =
        json(
            """
            { "properties": {
                "station_id": { "type": "string" },
                "location":   { "type": "string" } } }
            """);

    TableColumns derived = DataStructureTableMapper.deriveColumns(schema, Set.of("location"));

    assertEquals(1, derived.columns().size());
    assertEquals("station_id", derived.columns().get(0).name());
  }

  @Test
  void emptyDefinitionsIsRejected() {
    Map<String, Object> schema =
        json("{ \"$id\": \"urn:x\", \"title\": \"T\", \"definitions\": {} }");

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> DataStructureTableMapper.deriveColumns(schema, Set.of()));
    assertTrue(error.getMessage().contains("empty definitions"));
  }

  @Test
  void definitionWithoutPropertiesIsRejected() {
    Map<String, Object> schema =
        json("{ \"definitions\": { \"Observation\": { \"type\": \"object\" } } }");

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> DataStructureTableMapper.deriveColumns(schema, Set.of()));
    assertTrue(error.getMessage().contains("no properties"));
  }
}
