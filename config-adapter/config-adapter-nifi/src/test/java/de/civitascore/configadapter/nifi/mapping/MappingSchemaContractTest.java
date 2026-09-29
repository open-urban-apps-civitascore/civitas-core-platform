/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// Model Forge rejects a mapping on save when its schema lacks an op, so the editor can build a
// mapping that this adapter would deploy but that never gets stored.
class MappingSchemaContractTest {

  private static final Path MAPPING_SCHEMA =
      Path.of("../../model-forge/model-forge-runtime/src/main/resources/mapping.schema.json");

  private static final Set<String> STRUCTURAL_OPS = Set.of("copy", "const", "concat", "geoPoint");

  private static Map<String, JsonNode> schemaOperationsByOp;

  @BeforeAll
  static void loadSchemaOperations() throws IOException {
    JsonNode schema = new ObjectMapper().readTree(MAPPING_SCHEMA.toFile());
    JsonNode defs = schema.get("$defs");
    schemaOperationsByOp = new HashMap<>();
    for (JsonNode ref : defs.get("MappingOperation").get("oneOf")) {
      String defName = ref.get("$ref").asText().substring("#/$defs/".length());
      JsonNode operation = defs.get(defName);
      schemaOperationsByOp.put(operation.at("/properties/op/const").asText(), operation);
    }
  }

  @Test
  void schemaAcceptsExactlyTheOpsTheParserCompiles() {
    Set<String> parserOps =
        Stream.concat(
                STRUCTURAL_OPS.stream(),
                Arrays.stream(ConversionOp.values()).map(ConversionOp::rawOp))
            .collect(Collectors.toCollection(TreeSet::new));

    assertEquals(parserOps, new TreeSet<>(schemaOperationsByOp.keySet()));
  }

  @ParameterizedTest
  @EnumSource(ConversionOp.class)
  void schemaRequiresAPatternExactlyWhenTheParserDoes(ConversionOp op) {
    JsonNode operation = schemaOperationsByOp.get(op.rawOp());
    assertNotNull(operation, "mapping schema has no operation for " + op.rawOp());
    Set<String> required =
        StreamSupport.stream(operation.get("required").spliterator(), false)
            .map(JsonNode::asText)
            .collect(Collectors.toSet());

    assertEquals(
        op.requiresPattern(), required.contains("pattern"), op.rawOp() + " required 'pattern'");
    assertEquals(
        op.requiresPattern(),
        operation.get("properties").has("pattern"),
        op.rawOp() + " declares 'pattern'");
  }
}
