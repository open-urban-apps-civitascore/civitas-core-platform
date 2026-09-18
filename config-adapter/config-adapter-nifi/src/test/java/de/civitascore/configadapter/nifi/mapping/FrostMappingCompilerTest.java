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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.FreeAttribute;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.FrostCompilation;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.KeyAttribute;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.StaProperties;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.StaJsonType;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConstNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConvertNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.CopyNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.GeoPointNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The port body is pinned in two ways. Three golden documents hold the shapes a real Pipeline
 * produces, because the body is part of the byte-deterministic snapshot contract. The tests beside
 * them state one rule each — nesting, order, escaping, the reference block — so that a defect names
 * itself instead of showing a diff of a whole document.
 */
class FrostMappingCompilerTest {

  private static final StaProperties KEYS =
      StaProperties.ofKeys(List.of("reference"), List.of("reference"));

  private final FrostMappingCompiler compiler = new FrostMappingCompiler(new RecordPathCompiler());

  private static MappingConfig mapping(Object... pathsAndValues) {
    Map<String, ValueNode> fields = new LinkedHashMap<>();
    for (int i = 0; i < pathsAndValues.length; i += 2) {
      fields.put((String) pathsAndValues[i], (ValueNode) pathsAndValues[i + 1]);
    }
    return new MappingConfig(null, null, fields);
  }

  private static MappingConfig thingOnlyMapping() {
    return mapping(
        "$.name", new CopyNode("$.station"),
        "$.description", new CopyNode("$.desc"),
        "$.properties.reference", new CopyNode("$.ref"));
  }

  private static MappingConfig lookupOnlyWithObservationMapping() {
    return mapping(
        "$.properties.reference", new CopyNode("$.ref"),
        "$.datastreams[].properties.reference", new CopyNode("$.ref"),
        "$.datastreams[].observations[].result",
            new ConvertNode(ConversionOp.TO_FLOAT, new CopyNode("$.temp"), null),
        "$.datastreams[].observations[].phenomenonTime", new CopyNode("$.ts"));
  }

  /** The three shapes a Pipeline produces today, as whole documents. */
  private static final String GOLDEN_DIRECTORY = "/frost-port/";

  /**
   * Compares the rendered port body with the document beside this test.
   *
   * <p>The document was written from the renderer's own output, so it proves that the body did not
   * change, not that it was right on the day it was written. What it is right against is the rule
   * tests below and the review that accepted it.
   */
  private void assertBodyMatches(String resource, FrostCompilation compilation) throws Exception {
    try (var stream = getClass().getResourceAsStream(GOLDEN_DIRECTORY + resource)) {
      assertNotNull(stream, "no golden document at " + GOLDEN_DIRECTORY + resource);
      String expected = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      assertEquals(expected.strip(), compilation.plan().body());
    }
  }

  // ─── The shapes a Pipeline produces ──────────────────────────────────────────

  @Test
  void compile_aMetadataPipeline_rendersTheThingAlone() throws Exception {
    assertBodyMatches(
        "thing-only.json", compiler.compile(thingOnlyMapping(), KEYS, SinkPort.THING_TREE));
  }

  @Test
  void compile_aMeasurementPipeline_rendersTheMeasurementUnderItsDatastream() throws Exception {
    assertBodyMatches(
        "thing-datastream-observation.json",
        compiler.compile(lookupOnlyWithObservationMapping(), KEYS, SinkPort.THING_TREE));
  }

  @Test
  void compile_aFullChain_rendersSixEntitiesInOneDocument() throws Exception {
    assertBodyMatches(
        "full-chain.json", compiler.compile(fullChainMapping(), KEYS, SinkPort.THING_TREE));
  }

  /** A Thing with its Location, Datastream, Sensor, ObservedProperty and one measurement. */
  private static MappingConfig fullChainMapping() {
    return mapping(
        "$.name", new CopyNode("$.station"),
        "$.description", new CopyNode("$.desc"),
        "$.properties.reference", new CopyNode("$.ref"),
        "$.Locations[].name", new CopyNode("$.siteName"),
        "$.Locations[].description", new CopyNode("$.siteDesc"),
        "$.Locations[].encodingType", new ConstNode("application/geo+json", null),
        "$.Locations[].location", new GeoPointNode(new CopyNode("$.lon"), new CopyNode("$.lat")),
        "$.Datastreams[].name", new CopyNode("$.dsName"),
        "$.Datastreams[].description", new CopyNode("$.dsDesc"),
        "$.Datastreams[].observationType", new ConstNode("OM_Measurement", null),
        "$.Datastreams[].unitOfMeasurement.name", new ConstNode("degree Celsius", null),
        "$.Datastreams[].unitOfMeasurement.symbol", new ConstNode("degC", null),
        "$.Datastreams[].unitOfMeasurement.definition", new ConstNode("ucum:Cel", null),
        "$.Datastreams[].properties.reference", new CopyNode("$.dsRef"),
        "$.Datastreams[].Sensor.name", new CopyNode("$.sensorName"),
        "$.Datastreams[].Sensor.description", new ConstNode("air temperature", null),
        "$.Datastreams[].Sensor.encodingType", new ConstNode("application/pdf", null),
        "$.Datastreams[].Sensor.metadata", new ConstNode("http://example.org/s.pdf", null),
        "$.Datastreams[].ObservedProperty.name", new ConstNode("Temperature", null),
        "$.Datastreams[].ObservedProperty.description", new ConstNode("air temperature", null),
        "$.Datastreams[].ObservedProperty.definition", new ConstNode("http://example.org/t", null),
        "$.Datastreams[].Observations[].result",
            new ConvertNode(ConversionOp.TO_FLOAT, new CopyNode("$.temp"), null),
        "$.Datastreams[].Observations[].phenomenonTime", new CopyNode("$.ts"));
  }

  // ─── The rules the shapes follow ─────────────────────────────────────────────

  @Test
  void compile_aFullChain_nestsEveryEntityUnderTheOneItBelongsTo() throws Exception {
    // The processor writes the record in one request, so the document carries the chain. A flat
    // body could not say which reference belongs to which entity.
    String body = compiler.compile(fullChainMapping(), KEYS, SinkPort.THING_TREE).plan().body();

    int locations = body.indexOf("\"Locations\":[{");
    int datastreams = body.indexOf("\"Datastreams\":[{");
    int sensor = body.indexOf("\"Sensor\":{");
    int observations = body.indexOf("\"Observations\":[{");
    assertTrue(locations > 0 && datastreams > locations, body);
    assertTrue(sensor > datastreams, "the Sensor nests inside its Datastream");
    assertTrue(observations > datastreams, "the measurement nests inside its Datastream");
  }

  @Test
  void compile_withTheMappingInAnyOrder_rendersInCatalogOrder() throws Exception {
    // The snapshot must be byte-stable, so the catalog decides the order, not the mapping.
    String declared =
        compiler
            .compile(
                mapping(
                    "$.name", new CopyNode("$.station"),
                    "$.description", new CopyNode("$.desc"),
                    "$.properties.reference", new CopyNode("$.ref")),
                KEYS,
                SinkPort.THING_TREE)
            .plan()
            .body();
    String reversed =
        compiler
            .compile(
                mapping(
                    "$.properties.reference", new CopyNode("$.ref"),
                    "$.description", new CopyNode("$.desc"),
                    "$.name", new CopyNode("$.station")),
                KEYS,
                SinkPort.THING_TREE)
            .plan()
            .body();

    assertEquals(jsonKeyOrder(declared), jsonKeyOrder(reversed));
  }

  /** The JSON keys of a body, in the order the renderer wrote them. */
  private static List<String> jsonKeyOrder(String body) {
    List<String> keys = new java.util.ArrayList<>();
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([A-Za-z@.]+)\":").matcher(body);
    while (m.find()) {
      keys.add(m.group(1));
    }
    return keys;
  }

  @Test
  void compile_withAFreeBagAttribute_rendersItBesideTheMatchKey() throws Exception {
    StaProperties properties =
        new StaProperties(
            List.of(new KeyAttribute("reference"), new FreeAttribute("operator", StaJsonType.ANY)),
            List.of(new KeyAttribute("reference")));

    String body =
        compiler
            .compile(
                mapping(
                    "$.properties.reference", new CopyNode("$.ref"),
                    "$.properties.operator", new CopyNode("$.op")),
                properties,
                SinkPort.THING_TREE)
            .plan()
            .body();

    assertTrue(body.contains("\"reference\":"), body);
    assertTrue(body.contains("\"operator\":"), body);
  }

  @Test
  void compile_withALowerCaseRelationshipName_rendersTheFrostCasing() throws Exception {
    // A model commonly spells the edge 'datastreams'; the payload vocabulary is PascalCase.
    String body =
        compiler
            .compile(lookupOnlyWithObservationMapping(), KEYS, SinkPort.THING_TREE)
            .plan()
            .body();

    assertTrue(body.contains("\"Datastreams\":[{"), body);
    assertFalse(body.contains("\"datastreams\""), body);
  }

  @Test
  void compile_withAGeometry_embedsTheGeoJsonUnquoted() throws Exception {
    // The record chain renders GeoJSON as an object. Quoting it would make FROST read a string.
    String body = compiler.compile(fullChainMapping(), KEYS, SinkPort.THING_TREE).plan().body();

    int location = body.indexOf("\"location\":");
    assertTrue(location > 0, body);
    assertFalse(body.startsWith("\"location\":\"", location), "the geometry must not be quoted");
  }

  @Test
  void compile_withoutAField_rendersNoKeyForIt() throws Exception {
    // An unmapped catalog field must not appear as an empty value: FROST would reject the entity.
    String body = compiler.compile(thingOnlyMapping(), KEYS, SinkPort.THING_TREE).plan().body();

    assertFalse(body.contains("\"Datastreams\""), body);
    assertFalse(body.contains("\"Locations\""), body);
  }

  @Test
  void compile_withAnEmptyMapping_isRejected() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () -> compiler.compile(mapping(), KEYS, SinkPort.THING_TREE));

    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
  }

  @Test
  void compile_intoTheObservationsPort_isRejectedUntilItPublishesAStructure() {
    // The target vocabulary is rooted at the Thing. A measurement with its reference block at the
    // root is not addressable, so the port cannot be mapped into yet.
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () -> compiler.compile(thingOnlyMapping(), KEYS, SinkPort.OBSERVATIONS));

    assertTrue(ex.getMessage().contains("Observations port"), ex.getMessage());
  }

  // ─── Body templates ─────────────────────────────────────────────────────────

  @Test
  void anArraySourceFansOutEvenThoughEveryTargetPathCarriesEntityTierSelectors() throws Exception {
    // Every FROST target is compiled into a flat sta_* field, so the [] in Datastreams[]/
    // Observations[] are tier markers rather than arrays to preserve. Reading them as "the target
    // keeps its array level" would suppress the fan-out and the compile would fail on the array
    // source instead — the observations of one message must still explode into one record each.
    MappingConfig mapping =
        mapping(
            "$.properties.reference", new CopyNode("$.ref"),
            "$.datastreams[].properties.reference", new CopyNode("$.ref"),
            "$.datastreams[].observations[].result",
                new ConvertNode(
                    ConversionOp.TO_FLOAT,
                    new CopyNode("$.measurements[].measuredValues[].value"),
                    null),
            "$.datastreams[].observations[].phenomenonTime",
                new CopyNode("$.measurements[].measuredValues[].ts"));

    FrostCompilation compilation = compiler.compile(mapping, KEYS, SinkPort.THING_TREE);

    assertEquals("/measurements[*]/measuredValues", compilation.fork().recordPath());
    // The array-sourced fields read the forked element directly; the root-level one stays absolute.
    // Keyed on the rendered value rather than the flat key, whose index is an internal detail.
    List<String> values =
        compilation.flatProperties().stream().map(UpdateRecordProperty::value).toList();
    assertTrue(values.contains("/ts"), "the timestamp must read the forked element: " + values);
    assertTrue(values.contains("/ref"), "the root-level reference must stay absolute: " + values);
  }

  @Test
  void independentSiblingArraysAreRejectedOnTheFrostRouteToo() throws Exception {
    // FROST disables the target-side veto, so MORE rules reach the array-context collection than on
    // the record-shaped route — which makes sibling arrays strictly more likely here, not less. The
    // rejection must not live only on the PostGIS path.
    MappingConfig mapping =
        mapping(
            "$.properties.reference", new CopyNode("$.ref"),
            "$.datastreams[].observations[].phenomenonTime", new CopyNode("$.measurements[].ts"),
            "$.datastreams[].observations[].result", new CopyNode("$.alarms[].code"));

    FatalAdapterException error =
        assertThrows(
            FatalAdapterException.class,
            () -> compiler.compile(mapping, KEYS, SinkPort.THING_TREE));

    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, error.getErrorCode());
  }

  @Test
  void anArrayOfScalarsIsRejectedOnTheFrostRouteToo() throws Exception {
    // The natural user error on this route: pointing an Observation result at a bare scalar array.
    // ForkRecord's extract mode emits only RECORD elements and skips values silently, so accepting
    // this would deploy a flow that runs clean and writes nothing.
    MappingConfig mapping =
        mapping(
            "$.properties.reference", new CopyNode("$.ref"),
            "$.datastreams[].observations[].result", new CopyNode("$.temps[]"));

    FatalAdapterException error =
        assertThrows(
            FatalAdapterException.class,
            () -> compiler.compile(mapping, KEYS, SinkPort.THING_TREE));

    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, error.getErrorCode());
  }

  @Test
  void rejectsAFeatureOfInterestWithoutAnObservation() {
    MappingConfig mapping =
        mapping(
            "$.properties.reference", new CopyNode("$.ref"),
            "$.Datastreams[].properties.reference", new CopyNode("$.ref"),
            "$.Datastreams[].Observations[].FeatureOfInterest.name", new ConstNode("foi", null),
            "$.Datastreams[].Observations[].FeatureOfInterest.description",
                new ConstNode("d", null),
            "$.Datastreams[].Observations[].FeatureOfInterest.encodingType",
                new ConstNode("application/geo+json", null),
            "$.Datastreams[].Observations[].FeatureOfInterest.feature",
                new GeoPointNode(new CopyNode("$.lon"), new CopyNode("$.lat")));

    assertThrows(
        FatalAdapterException.class, () -> compiler.compile(mapping, KEYS, SinkPort.THING_TREE));
  }

  @Test
  void rejectsAPartiallyMappedFeatureOfInterestCreateSet() {
    MappingConfig mapping =
        mapping(
            "$.properties.reference", new CopyNode("$.ref"),
            "$.Datastreams[].properties.reference", new CopyNode("$.ref"),
            "$.Datastreams[].Observations[].result", new CopyNode("$.temp"),
            "$.Datastreams[].Observations[].FeatureOfInterest.name", new ConstNode("foi", null));

    assertThrows(
        FatalAdapterException.class, () -> compiler.compile(mapping, KEYS, SinkPort.THING_TREE));
  }

  // ─── Flat properties ────────────────────────────────────────────────────────

  @Test
  void flatKeysFollowMappingInsertionOrder() throws Exception {
    FrostCompilation compilation =
        compiler.compile(lookupOnlyWithObservationMapping(), KEYS, SinkPort.THING_TREE);

    assertEquals(
        List.of("sta_0_reference", "sta_1_reference", "sta_2_result", "sta_3_phenomenontime"),
        compilation.plan().flatKeys());
    assertEquals(4, compilation.flatProperties().size());
    assertEquals("/sta_0_reference", compilation.flatProperties().getFirst().recordPath());
  }

  // ─── Validation ─────────────────────────────────────────────────────────────

  @Test
  void preservesFreePropertiesAttributesNamedLikeNavigationEdges() throws Exception {
    StaProperties properties =
        new StaProperties(
            List.of(new KeyAttribute("reference"), new FreeAttribute("sensor", StaJsonType.ANY)),
            List.of(new KeyAttribute("reference")));

    FrostCompilation compilation =
        compiler.compile(
            mapping(
                "$.properties.reference", new CopyNode("$.ref"),
                "$.properties.sensor", new CopyNode("$.sensor")),
            properties,
            SinkPort.THING_TREE);

    assertEquals(List.of("sta_0_reference", "sta_1_sensor"), compilation.plan().flatKeys());
    assertEquals("/sta_1_sensor", compilation.flatProperties().get(1).recordPath());
  }

  @Test
  void rejectsAPathOutsideTheCatalog() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                compiler.compile(
                    mapping(
                        "$.properties.reference", new CopyNode("$.ref"),
                        "$.serialNumber", new CopyNode("$.sn")),
                    KEYS,
                    SinkPort.THING_TREE));
    assertTrue(ex.getMessage().contains("$.serialNumber"));
  }

  @Test
  void rejectsAMappingWithoutTheThingMatchKey() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                compiler.compile(
                    mapping("$.name", new CopyNode("$.station")), KEYS, SinkPort.THING_TREE));
    assertTrue(ex.getMessage().contains("match key"));
  }

  @Test
  void rejectsAPartialThingCreateSet() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                compiler.compile(
                    mapping(
                        "$.properties.reference", new CopyNode("$.ref"),
                        "$.name", new CopyNode("$.station")),
                    KEYS,
                    SinkPort.THING_TREE));
    assertTrue(ex.getMessage().contains("$.description"));
  }

  @Test
  void rejectsAnObservationWithoutResult() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                compiler.compile(
                    mapping(
                        "$.properties.reference", new CopyNode("$.ref"),
                        "$.Datastreams[].properties.reference", new CopyNode("$.ref"),
                        "$.Datastreams[].Observations[].phenomenonTime", new CopyNode("$.ts")),
                    KEYS,
                    SinkPort.THING_TREE));
    assertTrue(ex.getMessage().contains("result"));
  }

  @Test
  void rejectsADatastreamTouchWithoutADatastreamKeyInTheStructure() {
    StaProperties noDsKey = StaProperties.ofKeys(List.of("reference"), List.of());
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                compiler.compile(lookupOnlyWithObservationMapping(), noDsKey, SinkPort.THING_TREE));
    assertTrue(ex.getMessage().contains("Datastream"));
  }

  @Test
  void rejectsAPropertiesBagWithMoreThanOneMatchKey() {
    // A FROST entity has a single find-or-create identity — a bag with two keys is not a
    // representable state.
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> StaProperties.ofKeys(List.of("tenant", "station"), List.of()));
    assertTrue(ex.getMessage().contains("more than one key"));
  }

  @Test
  void rejectsABagAttributeNamedLikeThePropertiesBag() {
    // A key named 'properties' would render as properties.properties and shadow the bag itself —
    // rejected at the type boundary, never reaching the compiler.
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> StaProperties.ofKeys(List.of("properties"), List.of()));
    assertTrue(ex.getMessage().contains("must not be named 'properties'"));
  }

  @Test
  void aFreeBagAttributeDoesNotSatisfyAPartialCreateSet() {
    // A free bag attribute is OPTIONAL — it must not make a partially-mapped Thing create set look
    // complete; the missing create field is still rejected.
    StaProperties props =
        new StaProperties(
            List.of(new KeyAttribute("reference"), new FreeAttribute("owner", StaJsonType.ANY)),
            List.of());
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                compiler.compile(
                    mapping(
                        "$.properties.reference", new CopyNode("$.ref"),
                        "$.properties.owner", new CopyNode("$.owner"),
                        "$.name", new CopyNode("$.station")),
                    props,
                    SinkPort.THING_TREE));
    assertTrue(ex.getMessage().contains("$.description"));
  }

  @Test
  void rejectsAnUnsafeAttributeNameAtTheStaPropertiesBoundary() {
    // An unsafe attribute name can never inhabit a constructed StaProperties — it is rejected at
    // the
    // type boundary before it could reach the compiler, let alone a $filter URL.
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> StaProperties.ofKeys(List.of("ref' or true"), List.of()));
    assertTrue(ex.getMessage().contains("unsafe properties attribute name"));
  }

  @Test
  void rejectsANullConstant() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                compiler.compile(
                    mapping(
                        "$.properties.reference", new CopyNode("$.ref"),
                        "$.Datastreams[].Observations[].result", new ConstNode(null, null),
                        "$.Datastreams[].properties.reference", new CopyNode("$.ref")),
                    KEYS,
                    SinkPort.THING_TREE));
    assertTrue(ex.getMessage().contains("null constant"));
  }

  // ─── ANY-typed result placeholder branches ───────────────────────────────────

  private String observationResult(ValueNode resultNode) throws Exception {
    FrostCompilation compilation =
        compiler.compile(
            mapping(
                "$.properties.reference", new CopyNode("$.ref"),
                "$.Datastreams[].properties.reference", new CopyNode("$.ref"),
                "$.Datastreams[].Observations[].result", resultNode),
            KEYS,
            SinkPort.THING_TREE);
    // The measurement is nested in the one port body now, so the placeholder ends at the next key
    // or at the end of the Observation object.
    String body = compilation.plan().body();
    int start = body.indexOf("\"result\":") + "\"result\":".length();
    int end = start;
    int depth = 0;
    while (end < body.length()) {
      char c = body.charAt(end);
      if (c == '{' || c == '[') {
        depth++;
      } else if (c == '}' || c == ']') {
        if (depth == 0) {
          break;
        }
        depth--;
      } else if (c == ',' && depth == 0) {
        break;
      }
      end++;
    }
    return body.substring(start, end);
  }

  @Test
  void anyResultFromANumericConvertRendersUnquoted() throws Exception {
    String placeholder =
        observationResult(new ConvertNode(ConversionOp.TO_FLOAT, new CopyNode("$.temp"), null));
    assertEquals("${sta_2_result:isEmpty():ifElse('null', ${sta_2_result})}", placeholder);
  }

  @Test
  void anyResultFromANumberConstRendersUnquoted() throws Exception {
    assertEquals(
        "${sta_2_result:isEmpty():ifElse('null', ${sta_2_result})}",
        observationResult(new ConstNode(42, null)));
  }

  @Test
  void anyResultFromABooleanConstRendersUnquoted() throws Exception {
    assertEquals(
        "${sta_2_result:isEmpty():ifElse('null', ${sta_2_result})}",
        observationResult(new ConstNode(true, null)));
  }

  @Test
  void anyResultFromATemporalConvertRendersQuoted() throws Exception {
    // Both temporal ops yield text; unquoted they would emit bare ISO characters into the entity
    // body, which is invalid JSON and only surfaces when FROST rejects the ingest.
    String quoted = "\"${sta_2_result:escapeJson()}\"";
    assertEquals(
        quoted,
        observationResult(
            new ConvertNode(ConversionOp.TO_DATE_TIME, new CopyNode("$.ts"), "yyyy-MM-dd")));
    assertEquals(
        quoted,
        observationResult(
            new ConvertNode(ConversionOp.TO_DATE, new CopyNode("$.ts"), "yyyy-MM-dd")));
  }

  @Test
  void anyResultFromAUuidConvertRendersQuoted() throws Exception {
    // toUuid sits beside the numeric ops in the compiler but yields text, so grouping it with them
    // would emit a bare UUID and break the entity body.
    assertEquals(
        "\"${sta_2_result:escapeJson()}\"",
        observationResult(new ConvertNode(ConversionOp.TO_UUID, new CopyNode("$.raw"), null)));
  }

  @Test
  void anyResultFromACopyRendersQuotedString() throws Exception {
    // A copy is a string source: the ANY placeholder must quote+escape it (unquoted would be
    // invalid JSON for a text result).
    assertEquals("\"${sta_2_result:escapeJson()}\"", observationResult(new CopyNode("$.reading")));
  }

  @Test
  void anyResultFromAStringConstRendersQuoted() throws Exception {
    assertEquals("\"${sta_2_result:escapeJson()}\"", observationResult(new ConstNode("ok", null)));
  }

  // ─── Byte-deterministic ordering ─────────────────────────────────────────────

}
