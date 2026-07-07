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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins the Java model resolution against the shared DataStructure-model contract fixtures. The same
 * JSON documents live in the frontend mapping editor's test fixtures ({@code
 * portal-frontend/.../mapping-editor/schema/__fixtures__/datastructure-model/}), where the TS model
 * walker asserts its field tree — both sides must keep agreeing on how a persisted model resolves,
 * since the editor's field tree and the engine's record shape are two readings of one artifact.
 * When one side's fixture changes, change the other side identically.
 */
class DataStructureSchemaContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static Map<String, Object> fixture(String name) {
    try (InputStream in =
        DataStructureSchemaContractTest.class
            .getClassLoader()
            .getResourceAsStream("fixtures/datastructure-model/" + name)) {
      assertNotNull(in, "missing fixture " + name);
      return MAPPER.readValue(in, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void wrapperRootResolvesThroughTheWrapperAndInlinesTheParentFirst() {
    Map<String, Object> schema = fixture("wrapper-root.json");

    var resolved = DataStructureSchema.resolveDefinition(schema);
    assertEquals(
        List.of("id", "name", "breed", "home"), List.copyOf(resolved.properties().keySet()));
    assertEquals(Set.of("id", "breed"), resolved.required());
    assertEquals(List.of("id"), DataStructureSchema.primaryKeyColumns(schema));
  }

  @Test
  void multiRootDocumentResolvesToTheRecordItselfWithOneObjectPropertyPerTree() {
    Map<String, Object> schema = fixture("multi-root.json");

    // Two root properties are not a wrapper, so the record is the document root: each unconnected
    // tree stays an object-valued property of it.
    var resolved = DataStructureSchema.resolveDefinition(schema);
    assertEquals(List.of("building", "street"), List.copyOf(resolved.properties().keySet()));
    assertEquals(Set.of(), resolved.required());
    assertEquals(Map.of("$ref", "#/$defs/Building"), resolved.properties().get("building"));
    assertEquals(List.of(), DataStructureSchema.primaryKeyColumns(schema));
  }

  @Test
  void legacyFlatRootResolvesItsOwnProperties() {
    Map<String, Object> schema = fixture("legacy-flat-root.json");

    var resolved = DataStructureSchema.resolveDefinition(schema);
    assertEquals(List.of("attribut"), List.copyOf(resolved.properties().keySet()));
    assertEquals(Set.of("attribut"), resolved.required());
  }
}
