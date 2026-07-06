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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.ReplacementStrategy;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import de.civitascore.configadapter.nifi.mapping.StaEnvelopeCompiler.EnvelopeCompilation;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConcatNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConstNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConvertNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.CopyNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The template strings are asserted byte-exact on purpose: the template is part of the
 * byte-deterministic snapshot contract, and the EL placeholder forms are load-bearing (quoting,
 * escapeJson, the isEmpty→null fallback).
 */
class StaEnvelopeCompilerTest {

  private final StaEnvelopeCompiler compiler = new StaEnvelopeCompiler(new RecordPathCompiler());

  private static MappingConfig mapping(Object... pathsAndValues) {
    Map<String, ValueNode> fields = new LinkedHashMap<>();
    for (int i = 0; i < pathsAndValues.length; i += 2) {
      fields.put((String) pathsAndValues[i], (ValueNode) pathsAndValues[i + 1]);
    }
    return new MappingConfig(null, null, fields);
  }

  private static MappingConfig thingsOnlyMapping() {
    return mapping(
        "$.things[].name", new CopyNode("$.station"),
        "$.things[].description", new CopyNode("$.desc"),
        "$.things[].properties.reference", new CopyNode("$.ref"));
  }

  private static MappingConfig fullMapping() {
    return mapping(
        "$.things[].name", new CopyNode("$.station"),
        "$.things[].description", new CopyNode("$.desc"),
        "$.things[].properties.reference", new CopyNode("$.ref"),
        "$.observations[].result",
            new ConvertNode(ConversionOp.TO_FLOAT, new CopyNode("$.temp"), null),
        "$.observations[].phenomenonTime", new CopyNode("$.ts"),
        "$.observations[].parameters.reference", new CopyNode("$.ref"),
        "$.observations[].parameters.name", new CopyNode("$.dsName"));
  }

  // ─── Template generation ────────────────────────────────────────────────────

  @Test
  void rendersFullTemplateInCatalogOrder() throws Exception {
    EnvelopeCompilation compilation = compiler.compile(fullMapping());

    assertEquals(
        "{\"things\":[{\"name\":\"${sta_0_name:escapeJson()}\","
            + "\"description\":\"${sta_1_description:escapeJson()}\","
            + "\"properties\":{\"reference\":\"${sta_2_reference:escapeJson()}\"}}],"
            + "\"observations\":[{\"result\":${sta_3_result:isEmpty():ifElse('null',"
            + " ${sta_3_result})},"
            + "\"phenomenonTime\":${sta_4_phenomenontime:isEmpty():ifElse('null',"
            + " ${sta_4_phenomenontime:escapeJson():prepend('\"'):append('\"')})},"
            + "\"parameters\":{\"reference\":\"${sta_5_reference:escapeJson()}\","
            + "\"name\":\"${sta_6_name:escapeJson()}\"}}]}",
        compilation.plan().template());
  }

  @Test
  void rendersUnmappedGroupAsEmptyArray() throws Exception {
    // Both top-level keys are always present: SplitJson simply yields 0 splits for [], so the
    // find-or-create legs stay wired identically whether or not a group is mapped.
    EnvelopeCompilation compilation = compiler.compile(thingsOnlyMapping());

    assertEquals(
        "{\"things\":[{\"name\":\"${sta_0_name:escapeJson()}\","
            + "\"description\":\"${sta_1_description:escapeJson()}\","
            + "\"properties\":{\"reference\":\"${sta_2_reference:escapeJson()}\"}}],"
            + "\"observations\":[]}",
        compilation.plan().template());
  }

  @Test
  void rendersObservationsOnlyTemplate() throws Exception {
    EnvelopeCompilation compilation =
        compiler.compile(
            mapping(
                "$.observations[].result", new ConstNode(21.5, null),
                "$.observations[].parameters.reference", new CopyNode("$.ref"),
                "$.observations[].parameters.name", new CopyNode("$.dsName")));

    assertEquals(
        "{\"things\":[],"
            + "\"observations\":[{\"result\":${sta_0_result:isEmpty():ifElse('null',"
            + " ${sta_0_result})},"
            + "\"parameters\":{\"reference\":\"${sta_1_reference:escapeJson()}\","
            + "\"name\":\"${sta_2_name:escapeJson()}\"}}]}",
        compilation.plan().template());
  }

  @Test
  void templateKeyOrderIsCatalogOrderNotMappingOrder() throws Exception {
    // The tenant's field order must not leak into the template bytes — only into the flat-key
    // indices, which follow the mapping's insertion order.
    EnvelopeCompilation compilation =
        compiler.compile(
            mapping(
                "$.observations[].parameters.name", new CopyNode("$.dsName"),
                "$.observations[].result",
                    new ConvertNode(ConversionOp.TO_FLOAT, new CopyNode("$.temp"), null),
                "$.observations[].parameters.reference", new CopyNode("$.ref")));

    assertEquals(
        "{\"things\":[],"
            + "\"observations\":[{\"result\":${sta_1_result:isEmpty():ifElse('null',"
            + " ${sta_1_result})},"
            + "\"parameters\":{\"reference\":\"${sta_2_reference:escapeJson()}\","
            + "\"name\":\"${sta_0_name:escapeJson()}\"}}]}",
        compilation.plan().template());
    assertEquals(
        List.of("sta_0_name", "sta_1_result", "sta_2_reference"), compilation.plan().flatKeys());
  }

  @Test
  void stringTypedResultRendersQuoted() throws Exception {
    // result is STA 'any': a copy (no numeric coercion) serializes as a string.
    EnvelopeCompilation compilation =
        compiler.compile(
            mapping(
                "$.observations[].result", new CopyNode("$.state"),
                "$.observations[].parameters.reference", new CopyNode("$.ref"),
                "$.observations[].parameters.name", new CopyNode("$.dsName")));

    assertTrue(
        compilation.plan().template().contains("\"result\":\"${sta_0_result:escapeJson()}\""));
  }

  @Test
  void booleanConstResultRendersUnquoted() throws Exception {
    EnvelopeCompilation compilation =
        compiler.compile(
            mapping(
                "$.observations[].result", new ConstNode(true, null),
                "$.observations[].parameters.reference", new CopyNode("$.ref"),
                "$.observations[].parameters.name", new CopyNode("$.dsName")));

    assertTrue(
        compilation
            .plan()
            .template()
            .contains("\"result\":${sta_0_result:isEmpty():ifElse('null', ${sta_0_result})}"));
  }

  // ─── Flat compilation ───────────────────────────────────────────────────────

  @Test
  void compilesFlatPropertiesInMappingOrder() throws Exception {
    EnvelopeCompilation compilation = compiler.compile(fullMapping());

    List<UpdateRecordProperty> properties = compilation.flatProperties();
    assertEquals(7, properties.size());
    assertEquals(
        new UpdateRecordProperty("/sta_0_name", "/station", ReplacementStrategy.RECORD_PATH_VALUE),
        properties.get(0));
    // toInt/toFloat stay transparent — the placeholder's unquoted form owns the numeric rendering
    assertEquals(
        new UpdateRecordProperty("/sta_3_result", "/temp", ReplacementStrategy.RECORD_PATH_VALUE),
        properties.get(3));
    assertEquals(
        List.of(
            "sta_0_name",
            "sta_1_description",
            "sta_2_reference",
            "sta_3_result",
            "sta_4_phenomenontime",
            "sta_5_reference",
            "sta_6_name"),
        compilation.plan().flatKeys());
  }

  @Test
  void escapesExpressionLanguageInConstValues() throws Exception {
    // UpdateRecord evaluates EL in dynamic property values: an unescaped ${HOSTNAME} const would
    // expand and exfiltrate the environment into the record. $$ is EL's literal escape.
    EnvelopeCompilation compilation =
        compiler.compile(
            mapping(
                "$.things[].name", new CopyNode("$.station"),
                "$.things[].description", new ConstNode("${HOSTNAME}", null),
                "$.things[].properties.reference", new CopyNode("$.ref")));

    assertEquals(
        new UpdateRecordProperty(
            "/sta_1_description", "$${HOSTNAME}", ReplacementStrategy.LITERAL_VALUE),
        compilation.flatProperties().get(1));
  }

  @Test
  void escapesExpressionLanguageInConcatSeparators() throws Exception {
    EnvelopeCompilation compilation =
        compiler.compile(
            mapping(
                "$.things[].name",
                    new ConcatNode("${sep}", List.of(new CopyNode("$.a"), new CopyNode("$.b"))),
                "$.things[].description", new CopyNode("$.desc"),
                "$.things[].properties.reference", new CopyNode("$.ref")));

    assertEquals("concat(/a, '$${sep}', /b)", compilation.flatProperties().get(0).value());
  }

  // ─── Validation ─────────────────────────────────────────────────────────────

  @Test
  void rejectsTargetPathOutsideTheCatalog() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () -> compiler.compile(mapping("$.things[].custom", new CopyNode("$.x"))));
    assertTrue(
        ex.getMessage()
            .contains(
                "unsupported FROST mapping target path: '$.things[].custom'; supported paths are:"
                    + " $.things[].name, $.things[].description, $.things[].properties.reference,"
                    + " $.observations[].result, $.observations[].phenomenonTime,"
                    + " $.observations[].resultTime, $.observations[].parameters.reference,"
                    + " $.observations[].parameters.name"));
  }

  @Test
  void rejectsExpressionLanguageInTargetPath() {
    // The whitelist is also the injection guard: a tenant path segment can never become a template
    // JSON key, so ${ENV} in a path dies here.
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () -> compiler.compile(mapping("$.things[].properties.${ENV}", new CopyNode("$.x"))));
    assertTrue(ex.getMessage().contains("unsupported FROST mapping target path"));
  }

  @Test
  void rejectsEmptyMapping() {
    FatalAdapterException ex =
        assertThrows(FatalAdapterException.class, () -> compiler.compile(mapping()));
    assertTrue(
        ex.getMessage()
            .contains(
                "a FROST mapping must map at least one SensorThings element ($.things[] or"
                    + " $.observations[])"));
  }

  @Test
  void rejectsThingsGroupMissingRequiredPaths() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () -> compiler.compile(mapping("$.things[].name", new CopyNode("$.station"))));
    assertTrue(
        ex.getMessage()
            .contains(
                "a FROST mapping targeting $.things[] must also map: $.things[].description,"
                    + " $.things[].properties.reference"));
  }

  @Test
  void rejectsObservationsGroupMissingRequiredPaths() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                compiler.compile(
                    mapping(
                        "$.observations[].result",
                        new ConvertNode(ConversionOp.TO_FLOAT, new CopyNode("$.t"), null),
                        "$.observations[].parameters.reference",
                        new CopyNode("$.ref"))));
    assertTrue(
        ex.getMessage()
            .contains(
                "a FROST mapping targeting $.observations[] must also map:"
                    + " $.observations[].parameters.name"));
  }

  @Test
  void rejectsNullConstant() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () -> compiler.compile(mapping("$.observations[].result", new ConstNode(null, null))));
    assertTrue(
        ex.getMessage()
            .contains("a FROST mapping does not accept a null constant; omit the target field"));
  }

  @Test
  void compilationIsDeterministicAcrossRuns() throws Exception {
    EnvelopeCompilation first = compiler.compile(fullMapping());
    EnvelopeCompilation second = compiler.compile(fullMapping());
    assertEquals(first.plan(), second.plan());
    assertEquals(first.flatProperties(), second.flatProperties());
  }
}
