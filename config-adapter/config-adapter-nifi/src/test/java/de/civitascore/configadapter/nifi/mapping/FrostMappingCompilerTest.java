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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.nifi.mapping.FrostEntityPlan.FilterTerm;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.FreeAttribute;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.FrostCompilation;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.KeyAttribute;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.StaProperties;
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
 * The body templates are asserted byte-exact on purpose: they are part of the byte-deterministic
 * snapshot contract, and the EL placeholder forms are load-bearing (quoting, escapeJson, the
 * isEmpty→null fallback, the raw GeoJSON embed).
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
  void rendersRelatedBodiesForLookupOnlyParents() throws Exception {
    MappingConfig mapping =
        mapping(
            "$.properties.reference", new CopyNode("$.thingRef"),
            "$.Locations[].name", new ConstNode("Location", null),
            "$.Locations[].description", new ConstNode("Station location", null),
            "$.Locations[].encodingType", new ConstNode("application/geo+json", null),
            "$.Locations[].location",
                new GeoPointNode(new CopyNode("$.lon"), new CopyNode("$.lat")),
            "$.Datastreams[].properties.reference", new CopyNode("$.dsRef"),
            "$.Datastreams[].Sensor.name", new ConstNode("Sensor", null),
            "$.Datastreams[].Sensor.description", new ConstNode("Description", null),
            "$.Datastreams[].Sensor.encodingType", new ConstNode("text/html", null),
            "$.Datastreams[].Sensor.metadata", new ConstNode("https://example.test", null),
            "$.Datastreams[].ObservedProperty.name", new ConstNode("Temperature", null),
            "$.Datastreams[].ObservedProperty.definition",
                new ConstNode("https://example.test/temperature", null),
            "$.Datastreams[].ObservedProperty.description", new ConstNode("Temperature", null));

    FrostCompilation compilation = compiler.compile(mapping, KEYS);

    assertNull(compilation.plan().thingBody());
    assertNotNull(compilation.plan().locationBody());
    assertNull(compilation.plan().datastreamBody());
    assertNotNull(compilation.plan().sensorBody());
    assertNotNull(compilation.plan().observedPropertyBody());
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
  void rendersOptionalObservationFieldsResultQualityAndValidTime() throws Exception {
    MappingConfig mapping =
        mapping(
            "$.properties.reference", new CopyNode("$.ref"),
            "$.Datastreams[].properties.reference", new CopyNode("$.ref"),
            "$.Datastreams[].Observations[].result",
                new ConvertNode(ConversionOp.TO_FLOAT, new CopyNode("$.temp"), null),
            "$.Datastreams[].Observations[].resultQuality", new CopyNode("$.quality"),
            "$.Datastreams[].Observations[].validTime", new CopyNode("$.valid"));

    FrostCompilation compilation = compiler.compile(mapping, KEYS);

    assertEquals(
        "{\"result\":${sta_2_result:isEmpty():ifElse('null', ${sta_2_result})},"
            + "\"resultQuality\":${sta_3_resultquality:isEmpty():ifElse('null',"
            + " ${sta_3_resultquality:escapeJson():prepend('\"'):append('\"')})},"
            + "\"validTime\":${sta_4_validtime:isEmpty():ifElse('null',"
            + " ${sta_4_validtime:escapeJson():prepend('\"'):append('\"')})},"
            + "\"Datastream\":{\"@iot.id\":${frost.ds.id}}}",
        compilation.plan().observationBody());
  }

  @Test
  void rendersAnOptionalPropertiesBagOnALocation() throws Exception {
    MappingConfig mapping =
        mapping(
            "$.name", new CopyNode("$.station"),
            "$.description", new ConstNode("s", null),
            "$.properties.reference", new CopyNode("$.ref"),
            "$.Locations[].name", new ConstNode("loc", null),
            "$.Locations[].description", new ConstNode("d", null),
            "$.Locations[].encodingType", new ConstNode("application/geo+json", null),
            "$.Locations[].location",
                new GeoPointNode(new CopyNode("$.lon"), new CopyNode("$.lat")),
            "$.Locations[].properties", new CopyNode("$.meta"));

    FrostCompilation compilation = compiler.compile(mapping, KEYS);

    // The properties bag embeds verbatim (RAW_JSON), after the fixed Location fields.
    assertTrue(compilation.plan().thingBody().contains("\"location\":${sta_6_location},"));
    assertTrue(compilation.plan().thingBody().contains("\"properties\":${sta_7_properties}}]"));
  }

  @Test
  void deepInsertsAMappedFeatureOfInterestIntoTheObservationBody() throws Exception {
    MappingConfig mapping =
        mapping(
            "$.properties.reference", new CopyNode("$.ref"),
            "$.Datastreams[].properties.reference", new CopyNode("$.ref"),
            "$.Datastreams[].Observations[].result",
                new ConvertNode(ConversionOp.TO_FLOAT, new CopyNode("$.temp"), null),
            "$.Datastreams[].Observations[].featureOfInterest.name", new ConstNode("foi", null),
            "$.Datastreams[].Observations[].featureOfInterest.description",
                new ConstNode("d", null),
            "$.Datastreams[].Observations[].featureOfInterest.encodingType",
                new ConstNode("application/geo+json", null),
            "$.Datastreams[].Observations[].featureOfInterest.feature",
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
            "$.properties.reference", new CopyNode("$.ref"),
            "$.Datastreams[].properties.reference", new CopyNode("$.ref"),
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
            "$.properties.reference", new CopyNode("$.ref"),
            "$.Datastreams[].properties.reference", new CopyNode("$.ref"),
            "$.Datastreams[].Observations[].result", new CopyNode("$.temp"),
            "$.Datastreams[].Observations[].FeatureOfInterest.name", new ConstNode("foi", null));

    assertThrows(FatalAdapterException.class, () -> compiler.compile(mapping, KEYS));
  }

  @Test
  void rendersDeepInsertBodiesForACreatableChain() throws Exception {
    Map<String, ValueNode> fields = new LinkedHashMap<>();
    fields.put("$.name", new CopyNode("$.station"));
    fields.put("$.description", new ConstNode("station", null));
    fields.put("$.properties.reference", new CopyNode("$.ref"));
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
    fields.put("$.Datastreams[].sensor.name", new ConstNode("DHT22", null));
    fields.put("$.Datastreams[].sensor.description", new ConstNode("sensor", null));
    fields.put("$.Datastreams[].sensor.encodingType", new ConstNode("application/pdf", null));
    fields.put("$.Datastreams[].sensor.metadata", new ConstNode("https://x/d.pdf", null));
    fields.put("$.Datastreams[].observedProperty.name", new ConstNode("Temperature", null));
    fields.put("$.Datastreams[].observedProperty.definition", new ConstNode("http://t", null));
    fields.put("$.Datastreams[].observedProperty.description", new ConstNode("temp", null));
    fields.put("$.Datastreams[].properties.reference", new CopyNode("$.ref"));
    fields.put(
        "$.Datastreams[].Observations[].result",
        new ConvertNode(ConversionOp.TO_FLOAT, new CopyNode("$.temp"), null));

    FrostCompilation compilation = compiler.compile(new MappingConfig(null, null, fields), KEYS);

    String thingBody = compilation.plan().thingBody();
    // Locations ride inside the Thing body (deep insert); the geometry embeds verbatim.
    assertTrue(thingBody.contains("\"Locations\":[{\"name\":"));
    assertTrue(thingBody.contains("\"location\":${sta_6_location}}]"));
    assertFalse(compilation.plan().thingUpdateBody().contains("\"Locations\""));
    assertTrue(compilation.plan().locationBody().contains("\"name\":\"${sta_3_name"));
    assertTrue(compilation.plan().locationBody().contains("\"location\":${sta_6_location}"));

    String datastreamBody = compilation.plan().datastreamBody();
    assertTrue(datastreamBody.contains("\"unitOfMeasurement\":{\"name\":"));
    assertTrue(datastreamBody.contains("\"Sensor\":{\"name\":"));
    assertTrue(datastreamBody.contains("\"ObservedProperty\":{\"name\":"));
    String datastreamUpdateBody = compilation.plan().datastreamUpdateBody();
    assertFalse(datastreamUpdateBody.contains("\"Sensor\""));
    assertFalse(datastreamUpdateBody.contains("\"ObservedProperty\""));
    assertFalse(datastreamUpdateBody.contains("\"Thing\""));
    assertTrue(datastreamUpdateBody.contains("\"unitOfMeasurement\":{\"name\":"));
    assertTrue(compilation.plan().sensorBody().contains("\"name\":\"${sta_13_name"));
    assertFalse(compilation.plan().sensorBody().contains("\"Sensor\""));
    assertTrue(compilation.plan().observedPropertyBody().contains("\"name\":\"${sta_17_name"));
    assertFalse(compilation.plan().observedPropertyBody().contains("\"ObservedProperty\""));
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
    assertEquals("/sta_0_reference", compilation.flatProperties().getFirst().recordPath());
  }

  // ─── Validation ─────────────────────────────────────────────────────────────

  @Test
  void acceptsUserModelledRelationshipCapitalizationAndRendersCanonicalFrostBody()
      throws Exception {
    MappingConfig mapping =
        mapping(
            "$.properties.reference", new CopyNode("$.ref"),
            "$.datastream[].properties.reference", new CopyNode("$.ref"),
            "$.datastream[].observation[].result", new CopyNode("$.temp"));

    FrostCompilation compilation = compiler.compile(mapping, KEYS);

    assertEquals(
        List.of(new FilterTerm("properties/reference", "sta_1_reference")),
        compilation.plan().datastreamFilter());
    assertTrue(compilation.plan().observationBody().contains("\"Datastream\""));
    assertEquals(
        List.of("sta_0_reference", "sta_1_reference", "sta_2_result"),
        compilation.plan().flatKeys());
  }

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
            properties);

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
                        "$.properties.reference", new CopyNode("$.ref"),
                        "$.name", new CopyNode("$.station")),
                    KEYS));
    assertTrue(ex.getMessage().contains("$.description"));
  }

  @Test
  void acceptsALocationWithALookupOnlyThing() throws Exception {
    FrostCompilation compilation =
        compiler.compile(
            mapping(
                "$.properties.reference", new CopyNode("$.ref"),
                "$.Locations[].name", new ConstNode("loc", null),
                "$.Locations[].description", new ConstNode("d", null),
                "$.Locations[].encodingType", new ConstNode("e", null),
                "$.Locations[].location",
                    new GeoPointNode(new CopyNode("$.lon"), new CopyNode("$.lat"))),
            KEYS);

    assertNull(compilation.plan().thingBody());
    assertNotNull(compilation.plan().locationBody());
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
                    KEYS));
    assertTrue(ex.getMessage().contains("result"));
  }

  @Test
  void rejectsADatastreamTouchWithoutADatastreamKeyInTheStructure() {
    StaProperties noDsKey = StaProperties.ofKeys(List.of("reference"), List.of());
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () -> compiler.compile(lookupOnlyWithObservationMapping(), noDsKey));
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
  void acceptsAMatchKeyNamedLikeAStandardStaField() throws Exception {
    // Inside the properties bag, a key named like a top-level field ('name') no longer collides.
    StaProperties named = StaProperties.ofKeys(List.of("name"), List.of());
    FrostCompilation compilation =
        compiler.compile(
            mapping(
                "$.name", new CopyNode("$.station"),
                "$.description", new CopyNode("$.desc"),
                "$.properties.name", new CopyNode("$.ref")),
            named);

    assertEquals(
        List.of(new FilterTerm("properties/name", "sta_2_name")), compilation.plan().thingFilter());
  }

  @Test
  void rendersFreeBagAttributesAlongsideTheMatchKeyButFiltersOnlyOnTheKey() throws Exception {
    // Thing bag: match key 'reference' + two free attributes (a scalar 'owner', a raw-json 'meta').
    StaProperties props =
        new StaProperties(
            List.of(
                new KeyAttribute("reference"),
                new FreeAttribute("owner", StaJsonType.ANY),
                new FreeAttribute("meta", StaJsonType.RAW_JSON)),
            List.of());
    FrostCompilation compilation =
        compiler.compile(
            mapping(
                "$.name", new CopyNode("$.station"),
                "$.description", new CopyNode("$.desc"),
                "$.properties.reference", new CopyNode("$.ref"),
                "$.properties.owner", new CopyNode("$.owner"),
                "$.properties.meta", new CopyNode("$.meta")),
            props);

    // All three bag attributes render under properties, in declaration order: the match key stays a
    // plain quoted string, the free scalar 'owner' is an optional ANY (null-fallback + quoted when
    // present), 'meta' embeds verbatim as raw json.
    assertTrue(
        compilation
            .plan()
            .thingBody()
            .contains(
                "\"properties\":{\"reference\":\"${sta_2_reference:escapeJson()}\","
                    + "\"owner\":${sta_3_owner:isEmpty():ifElse('null',"
                    + " ${sta_3_owner:escapeJson():prepend('\"'):append('\"')})},"
                    + "\"meta\":${sta_4_meta}}"),
        compilation.plan().thingBody());
    // Only the match key drives the $filter — free attributes never do.
    assertEquals(
        List.of(new FilterTerm("properties/reference", "sta_2_reference")),
        compilation.plan().thingFilter());
  }

  @Test
  void freeBagAttributesAreOptional() throws Exception {
    // A bag declaring free attributes the mapping does NOT touch is fine — only the key is
    // required.
    StaProperties props =
        new StaProperties(
            List.of(new KeyAttribute("reference"), new FreeAttribute("owner", StaJsonType.ANY)),
            List.of());
    FrostCompilation compilation = compiler.compile(thingOnlyMapping(), props);

    assertTrue(compilation.plan().thingBody().contains("\"properties\":{\"reference\":"));
    assertFalse(compilation.plan().thingBody().contains("owner"));
  }

  @Test
  void rendersFreeBagAttributesOnTheDatastreamBesideTheThingLink() throws Exception {
    // A free Datastream bag attribute renders under the datastream's properties alongside the
    // injected Thing @iot.id link; the datastream $filter still keys only on the match key.
    StaProperties props =
        new StaProperties(
            List.of(new KeyAttribute("reference")),
            List.of(new KeyAttribute("reference"), new FreeAttribute("unit", StaJsonType.ANY)));
    FrostCompilation compilation =
        compiler.compile(
            mapping(
                "$.name", new CopyNode("$.station"),
                "$.description", new CopyNode("$.desc"),
                "$.properties.reference", new CopyNode("$.ref"),
                "$.Datastreams[].name", new CopyNode("$.dsName"),
                "$.Datastreams[].description", new ConstNode("d", null),
                "$.Datastreams[].observationType", new ConstNode("om", null),
                "$.Datastreams[].unitOfMeasurement.name", new ConstNode("°C", null),
                "$.Datastreams[].unitOfMeasurement.symbol", new ConstNode("C", null),
                "$.Datastreams[].unitOfMeasurement.definition", new ConstNode("ucum", null),
                "$.Datastreams[].Sensor.name", new ConstNode("s", null),
                "$.Datastreams[].Sensor.description", new ConstNode("s", null),
                "$.Datastreams[].Sensor.encodingType", new ConstNode("application/pdf", null),
                "$.Datastreams[].Sensor.metadata", new ConstNode("m", null),
                "$.Datastreams[].ObservedProperty.name", new ConstNode("t", null),
                "$.Datastreams[].ObservedProperty.definition", new ConstNode("d", null),
                "$.Datastreams[].ObservedProperty.description", new ConstNode("d", null),
                "$.Datastreams[].properties.reference", new CopyNode("$.ref"),
                "$.Datastreams[].properties.unit", new CopyNode("$.unit")),
            props);

    String datastreamBody = compilation.plan().datastreamBody();
    assertTrue(
        datastreamBody.contains(
            "\"properties\":{\"reference\":\"${sta_16_reference:escapeJson()}\","
                + "\"unit\":${sta_17_unit:isEmpty():ifElse('null',"
                + " ${sta_17_unit:escapeJson():prepend('\"'):append('\"')})}}"),
        datastreamBody);
    assertTrue(datastreamBody.contains("\"Thing\":{\"@iot.id\":${frost.thing.id}}"));
    assertEquals(
        List.of(new FilterTerm("properties/reference", "sta_16_reference")),
        compilation.plan().datastreamFilter());
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
                    props));
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
                    KEYS));
    assertTrue(ex.getMessage().contains("null constant"));
  }

  @Test
  void rejectsAnEmptyMapping() {
    assertThrows(
        FatalAdapterException.class,
        () -> compiler.compile(new MappingConfig(null, null, Map.of()), KEYS));
  }

  // ─── ANY-typed result placeholder branches ───────────────────────────────────

  private String observationResult(ValueNode resultNode) throws Exception {
    FrostCompilation compilation =
        compiler.compile(
            mapping(
                "$.properties.reference", new CopyNode("$.ref"),
                "$.Datastreams[].properties.reference", new CopyNode("$.ref"),
                "$.Datastreams[].Observations[].result", resultNode),
            KEYS);
    String body = compilation.plan().observationBody();
    // "result":<placeholder>,"Datastream": …
    int start = body.indexOf("\"result\":") + "\"result\":".length();
    return body.substring(start, body.indexOf(",\"Datastream\""));
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

  @Test
  void bodyIsRenderedInCatalogOrderRegardlessOfMappingOrder() throws Exception {
    // The Thing body must be catalog-ordered (name, description, then the key properties) even when
    // the mapping supplies the fields in a scrambled order — the snapshot must be reproducible.
    String scrambled =
        compiler
            .compile(
                mapping(
                    "$.description", new ConstNode("d", null),
                    "$.properties.reference", new CopyNode("$.ref"),
                    "$.name", new CopyNode("$.station")),
                KEYS)
            .plan()
            .thingBody();
    String ordered =
        compiler
            .compile(
                mapping(
                    "$.name", new CopyNode("$.station"),
                    "$.description", new ConstNode("d", null),
                    "$.properties.reference", new CopyNode("$.ref")),
                KEYS)
            .plan()
            .thingBody();
    // Field order in the rendered JSON follows the catalog, not the input map — the "name" key
    // precedes "description" precedes the "properties" bag in both.
    assertTrue(scrambled.indexOf("\"name\"") < scrambled.indexOf("\"description\""));
    assertTrue(scrambled.indexOf("\"description\"") < scrambled.indexOf("\"properties\""));
    // The two differ only by the flat-key index suffix (input order drives sta_<n>), never by
    // JSON field order.
    assertEquals(
        scrambled.replaceAll("sta_\\d+_", "sta_N_"), ordered.replaceAll("sta_\\d+_", "sta_N_"));
  }
}
