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
}
