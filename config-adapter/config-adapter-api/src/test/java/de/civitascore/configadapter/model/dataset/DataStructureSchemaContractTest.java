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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pins the Java model resolution against the shared DataStructure-model contract fixtures. The same
 * JSON documents live in the frontend mapping editor's test fixtures ({@code
 * portal-frontend/.../mapping-editor/schema/__fixtures__/datastructure-model/}), where the TS model
 * walker asserts its field tree — both sides must keep agreeing on how a persisted model resolves,
 * since the editor's field tree and the engine's record shape are two readings of one artifact. The
 * byte-level hash pins below (duplicated in the TS test) turn silent fixture drift between the two
 * copies into a red test on whichever side lags behind.
 */
class DataStructureSchemaContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static InputStream open(String name) {
    InputStream in =
        DataStructureSchemaContractTest.class
            .getClassLoader()
            .getResourceAsStream("fixtures/datastructure-model/" + name);
    assertNotNull(in, "missing fixture " + name);
    return in;
  }

  private static Map<String, Object> fixture(String name) {
    try (InputStream in = open(name)) {
      return MAPPER.readValue(in, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "broken-missing-parent.json, 4b8aa70c36ee85b86db5adf34cb9214f4a7d6c14254c8be5217437199f69fa82",
    "legacy-definitions.json, dff36ea7e76be8fb31a69b848654a73a844fcefe7b3704f11531287aff14fb85",
    "legacy-flat-root.json, 2503d11def93c1ee4a189cb66d9bd2309cfad9645f009be2badfea910ceb6fbe",
    "multi-root.json, 6c4d22c7f796c069f3e0386beac609f8af28ef895cddd910747c83dacaea0def",
    "wrapper-root.json, 95b6b74650a5a01350f251240ec003e215cfda30c2378aabef7c11a679a3d84d"
  })
  void fixtureMatchesThePinnedContractHash(String name, String expectedHash) throws Exception {
    try (InputStream in = open(name)) {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
      assertEquals(expectedHash, HexFormat.of().formatHex(digest));
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

  @Test
  void legacyDefinitionsSectionIsShadowedByDefsOnKeyCollision() {
    Map<String, Object> schema = fixture("legacy-definitions.json");

    var resolved = DataStructureSchema.resolveDefinition(schema);
    assertEquals(List.of("shadowed", "current"), List.copyOf(resolved.properties().keySet()));
    assertEquals(Set.of("shadowed"), resolved.required());
  }

  @Test
  void danglingInheritanceParentIsRejectedNotSilentlySkipped() {
    Map<String, Object> schema = fixture("broken-missing-parent.json");

    assertThrows(
        IllegalArgumentException.class, () -> DataStructureSchema.resolveDefinition(schema));
  }
}
