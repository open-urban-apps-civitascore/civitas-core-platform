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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.nifi.mapping.FrostEntityPlan.FilterTerm;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.FrostCompilation;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.StaKeys;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConstNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConvertNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.CopyNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.GeoPointNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The body templates are asserted byte-exact on purpose: they are part of the byte-deterministic
 * snapshot contract, and the EL placeholder forms are load-bearing (quoting, escapeJson, the
 * isEmpty→null fallback, the raw GeoJSON embed).
 */
class FrostMappingCompilerTest {

  private static final StaKeys KEYS = new StaKeys(List.of("reference"), List.of("reference"));

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
        "$.reference", new CopyNode("$.ref"));
  }

  private static MappingConfig lookupOnlyWithObservationMapping() {
    return mapping(
        "$.reference", new CopyNode("$.ref"),
        "$.Datastreams[].reference", new CopyNode("$.ref"),
        "$.Datastreams[].Observations[].result",
            new ConvertNode(ConversionOp.TO_FLOAT, new CopyNode("$.temp"), null),
        "$.Datastreams[].Observations[].phenomenonTime", new CopyNode("$.ts"));
  }

  // ─── Body templates ─────────────────────────────────────────────────────────

  @Test
  void rendersThingBodyInCatalogOrderWithKeyProperties() throws Exception {
    FrostCompilation compilation = compiler.compile(thingOnlyMapping(), KEYS);

    assertEquals(
        "{\"name\":\"${sta_0_name:escapeJson()}\","
            + "\"description\":\"${sta_1_description:escapeJson()}\","
            + "\"properties\":{\"reference\":\"${sta_2_reference:escapeJson()}\"}}",
        compilation.plan().thingBody());
    assertNull(compilation.plan().datastreamBody());
    assertNull(compilation.plan().observationBody());
    assertTrue(compilation.plan().datastreamFilter().isEmpty());
  }

  @Test
  void lookupOnlyThingHasNoBodyButAFilter() throws Exception {
    FrostCompilation compilation = compiler.compile(lookupOnlyWithObservationMapping(), KEYS);

    assertNull(compilation.plan().thingBody());
    assertEquals(
        List.of(new FilterTerm("properties/reference", "sta_0_reference")),
        compilation.plan().thingFilter());
    assertEquals(
        List.of(new FilterTerm("properties/reference", "sta_1_reference")),
        compilation.plan().datastreamFilter());
  }

  @Test
  void rendersObservationBodyWithDatastreamIdReference() throws Exception {
    FrostCompilation compilation = compiler.compile(lookupOnlyWithObservationMapping(), KEYS);

    assertEquals(
        "{\"result\":${sta_2_result:isEmpty():ifElse('null', ${sta_2_result})},"
            + "\"phenomenonTime\":${sta_3_phenomenontime:isEmpty():ifElse('null',"
            + " ${sta_3_phenomenontime:escapeJson():prepend('\"'):append('\"')})},"
            + "\"Datastream\":{\"@iot.id\":${frost.ds.id}}}",
        compilation.plan().observationBody());
  }

  @Test
  void deepInsertsAMappedFeatureOfInterestIntoTheObservationBody() throws Exception {
    MappingConfig mapping =
        mapping(
            "$.reference", new CopyNode("$.ref"),
            "$.Datastreams[].reference", new CopyNode("$.ref"),
            "$.Datastreams[].Observations[].result",
                new ConvertNode(ConversionOp.TO_FLOAT, new CopyNode("$.temp"), null),
            "$.Datastreams[].Observations[].FeatureOfInterest.name", new ConstNode("foi", null),
            "$.Datastreams[].Observations[].FeatureOfInterest.description",
                new ConstNode("d", null),
            "$.Datastreams[].Observations[].FeatureOfInterest.encodingType",
                new ConstNode("application/geo+json", null),
            "$.Datastreams[].Observations[].FeatureOfInterest.feature",
                new GeoPointNode(new CopyNode("$.lon"), new CopyNode("$.lat")));

    FrostCompilation compilation = compiler.compile(mapping, KEYS);

    assertEquals(
        "{\"result\":${sta_2_result:isEmpty():ifElse('null', ${sta_2_result})},"
            + "\"FeatureOfInterest\":{\"name\":\"${sta_3_name:escapeJson()}\","
            + "\"description\":\"${sta_4_description:escapeJson()}\","
            + "\"encodingType\":\"${sta_5_encodingtype:escapeJson()}\","
            + "\"feature\":${sta_6_feature}},"
            + "\"Datastream\":{\"@iot.id\":${frost.ds.id}}}",
        compilation.plan().observationBody());
  }

  @Test
  void rejectsAFeatureOfInterestWithoutAnObservation() {
    MappingConfig mapping =
        mapping(
            "$.reference", new CopyNode("$.ref"),
            "$.Datastreams[].reference", new CopyNode("$.ref"),
            "$.Datastreams[].Observations[].FeatureOfInterest.name", new ConstNode("foi", null),
            "$.Datastreams[].Observations[].FeatureOfInterest.description",
                new ConstNode("d", null),
            "$.Datastreams[].Observations[].FeatureOfInterest.encodingType",
                new ConstNode("application/geo+json", null),
            "$.Datastreams[].Observations[].FeatureOfInterest.feature",
                new GeoPointNode(new CopyNode("$.lon"), new CopyNode("$.lat")));

    assertThrows(FatalAdapterException.class, () -> compiler.compile(mapping, KEYS));
  }

  @Test
  void rejectsAPartiallyMappedFeatureOfInterestCreateSet() {
    MappingConfig mapping =
        mapping(
            "$.reference", new CopyNode("$.ref"),
            "$.Datastreams[].reference", new CopyNode("$.ref"),
            "$.Datastreams[].Observations[].result", new CopyNode("$.temp"),
            "$.Datastreams[].Observations[].FeatureOfInterest.name", new ConstNode("foi", null));

    assertThrows(FatalAdapterException.class, () -> compiler.compile(mapping, KEYS));
  }

  @Test
  void rendersDeepInsertBodiesForACreatableChain() throws Exception {
    Map<String, ValueNode> fields = new LinkedHashMap<>();
    fields.put("$.name", new CopyNode("$.station"));
    fields.put("$.description", new ConstNode("station", null));
    fields.put("$.reference", new CopyNode("$.ref"));
    fields.put("$.Locations[].name", new ConstNode("loc", null));
    fields.put("$.Locations[].description", new ConstNode("d", null));
    fields.put("$.Locations[].encodingType", new ConstNode("application/geo+json", null));
    fields.put(
        "$.Locations[].location", new GeoPointNode(new CopyNode("$.lon"), new CopyNode("$.lat")));
    fields.put("$.Datastreams[].name", new CopyNode("$.dsName"));
    fields.put("$.Datastreams[].description", new ConstNode("dd", null));
    fields.put("$.Datastreams[].observationType", new ConstNode("om", null));
    fields.put("$.Datastreams[].unitOfMeasurement.name", new ConstNode("Degree Celsius", null));
    fields.put("$.Datastreams[].unitOfMeasurement.symbol", new ConstNode("°C", null));
    fields.put("$.Datastreams[].unitOfMeasurement.definition", new ConstNode("ucum:Cel", null));
    fields.put("$.Datastreams[].Sensor.name", new ConstNode("DHT22", null));
    fields.put("$.Datastreams[].Sensor.description", new ConstNode("sensor", null));
    fields.put("$.Datastreams[].Sensor.encodingType", new ConstNode("application/pdf", null));
    fields.put("$.Datastreams[].Sensor.metadata", new ConstNode("https://x/d.pdf", null));
    fields.put("$.Datastreams[].ObservedProperty.name", new ConstNode("Temperature", null));
    fields.put("$.Datastreams[].ObservedProperty.definition", new ConstNode("http://t", null));
    fields.put("$.Datastreams[].ObservedProperty.description", new ConstNode("temp", null));
    fields.put("$.Datastreams[].reference", new CopyNode("$.ref"));
    fields.put(
        "$.Datastreams[].Observations[].result",
        new ConvertNode(ConversionOp.TO_FLOAT, new CopyNode("$.temp"), null));

    FrostCompilation compilation = compiler.compile(new MappingConfig(null, null, fields), KEYS);

    String thingBody = compilation.plan().thingBody();
    // Locations ride inside the Thing body (deep insert); the geometry embeds verbatim.
    assertTrue(thingBody.contains("\"Locations\":[{\"name\":"));
    assertTrue(thingBody.contains("\"location\":${sta_6_location}}]"));

    String datastreamBody = compilation.plan().datastreamBody();
    assertTrue(datastreamBody.contains("\"unitOfMeasurement\":{\"name\":"));
    assertTrue(datastreamBody.contains("\"Sensor\":{\"name\":"));
    assertTrue(datastreamBody.contains("\"ObservedProperty\":{\"name\":"));
    // The created datastream carries its match key and the parent Thing link.
    assertTrue(
        datastreamBody.contains(
            "\"properties\":{\"reference\":\"${sta_20_reference:escapeJson()}\"}"));
    assertTrue(datastreamBody.endsWith("\"Thing\":{\"@iot.id\":${frost.thing.id}}}"));
  }

  // ─── Flat properties ────────────────────────────────────────────────────────

  @Test
  void flatKeysFollowMappingInsertionOrder() throws Exception {
    FrostCompilation compilation = compiler.compile(lookupOnlyWithObservationMapping(), KEYS);

    assertEquals(
        List.of("sta_0_reference", "sta_1_reference", "sta_2_result", "sta_3_phenomenontime"),
        compilation.plan().flatKeys());
    assertEquals(4, compilation.flatProperties().size());
    assertEquals("/sta_0_reference", compilation.flatProperties().get(0).recordPath());
  }

  // ─── Validation ─────────────────────────────────────────────────────────────

  @Test
  void rejectsAPathOutsideTheCatalog() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                compiler.compile(
                    mapping(
                        "$.reference", new CopyNode("$.ref"),
                        "$.serialNumber", new CopyNode("$.sn")),
                    KEYS));
    assertTrue(ex.getMessage().contains("$.serialNumber"));
  }

  @Test
  void rejectsAMappingWithoutTheThingMatchKey() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () -> compiler.compile(mapping("$.name", new CopyNode("$.station")), KEYS));
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
                        "$.reference", new CopyNode("$.ref"),
                        "$.name", new CopyNode("$.station")),
                    KEYS));
    assertTrue(ex.getMessage().contains("$.description"));
  }

  @Test
  void rejectsALocationWithoutACreatableThing() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                compiler.compile(
                    mapping(
                        "$.reference", new CopyNode("$.ref"),
                        "$.Locations[].name", new ConstNode("loc", null),
                        "$.Locations[].description", new ConstNode("d", null),
                        "$.Locations[].encodingType", new ConstNode("e", null),
                        "$.Locations[].location",
                            new GeoPointNode(new CopyNode("$.lon"), new CopyNode("$.lat"))),
                    KEYS));
    assertTrue(ex.getMessage().contains("creatable Thing"));
  }

  @Test
  void rejectsAnObservationWithoutResult() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                compiler.compile(
                    mapping(
                        "$.reference", new CopyNode("$.ref"),
                        "$.Datastreams[].reference", new CopyNode("$.ref"),
                        "$.Datastreams[].Observations[].phenomenonTime", new CopyNode("$.ts")),
                    KEYS));
    assertTrue(ex.getMessage().contains("result"));
  }

  @Test
  void rejectsADatastreamTouchWithoutADatastreamKeyInTheStructure() {
    StaKeys noDsKey = new StaKeys(List.of("reference"), List.of());
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () -> compiler.compile(lookupOnlyWithObservationMapping(), noDsKey));
    assertTrue(ex.getMessage().contains("Datastream"));
  }

  @Test
  void compositeMatchKeysProduceOrderedFilterTermsAndProperties() throws Exception {
    StaKeys composite = new StaKeys(List.of("tenant", "station"), List.of());
    FrostCompilation compilation =
        compiler.compile(
            mapping(
                "$.tenant", new CopyNode("$.t"),
                "$.station", new CopyNode("$.s"),
                "$.name", new CopyNode("$.n"),
                "$.description", new CopyNode("$.d")),
            composite);

    assertEquals(
        List.of(
            new FilterTerm("properties/tenant", "sta_0_tenant"),
            new FilterTerm("properties/station", "sta_1_station")),
        compilation.plan().thingFilter());
    assertTrue(
        compilation
            .plan()
            .thingBody()
            .contains(
                "\"properties\":{\"tenant\":\"${sta_0_tenant:escapeJson()}\","
                    + "\"station\":\"${sta_1_station:escapeJson()}\"}"));
  }

  @Test
  void namesEveryMissingCompositeKey() {
    StaKeys composite = new StaKeys(List.of("tenant", "station"), List.of());
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () -> compiler.compile(mapping("$.name", new CopyNode("$.n")), composite));
    assertTrue(ex.getMessage().contains("$.tenant, $.station"));
  }

  @Test
  void rejectsAMatchKeyNamedLikeAStandardStaField() {
    // 'name' as {id} would make $.name mean both the STA field and the properties-bag key.
    StaKeys reserved = new StaKeys(List.of("name"), List.of());
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () -> compiler.compile(mapping("$.name", new CopyNode("$.n")), reserved));
    assertTrue(ex.getMessage().contains("collides with a standard SensorThings field"));
  }

  @Test
  void rejectsAMatchKeyNamedLikeANestedStaContainer() {
    StaKeys reserved = new StaKeys(List.of("reference"), List.of("Sensor"));
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () -> compiler.compile(lookupOnlyWithObservationMapping(), reserved));
    assertTrue(ex.getMessage().contains("collides with a standard SensorThings field"));
  }

  @Test
  void rejectsAnUnsafeKeyName() {
    StaKeys unsafe = new StaKeys(List.of("ref' or true"), List.of());
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class, () -> compiler.compile(thingOnlyMapping(), unsafe));
    assertTrue(ex.getMessage().contains("safe identifier"));
  }

  @Test
  void rejectsANullConstant() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                compiler.compile(
                    mapping(
                        "$.reference", new CopyNode("$.ref"),
                        "$.Datastreams[].Observations[].result", new ConstNode(null, null),
                        "$.Datastreams[].reference", new CopyNode("$.ref")),
                    KEYS));
    assertTrue(ex.getMessage().contains("null constant"));
  }

  @Test
  void rejectsAnEmptyMapping() {
    assertThrows(
        FatalAdapterException.class,
        () -> compiler.compile(new MappingConfig(null, null, Map.of()), KEYS));
  }
}
