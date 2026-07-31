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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.ReplacementStrategy;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.nifi.record.path.RecordPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RecordPathCompilerTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final MappingConfigParser parser = new MappingConfigParser();
  private final RecordPathCompiler compiler = new RecordPathCompiler();

  private List<UpdateRecordProperty> compile(String fieldsJson) throws Exception {
    return compile(fieldsJson, GeometryEncoding.WKT);
  }

  private List<UpdateRecordProperty> compile(String fieldsJson, GeometryEncoding encoding)
      throws Exception {
    return compiler.compile(parse(fieldsJson), encoding).properties();
  }

  private MappingConfig parse(String fieldsJson) throws Exception {
    return parser.parse(mapper.readTree("{ \"fields\": " + fieldsJson + " }"));
  }

  private Map<String, UpdateRecordProperty> byPath(List<UpdateRecordProperty> props) {
    return props.stream().collect(Collectors.toMap(UpdateRecordProperty::recordPath, p -> p));
  }

  @Test
  void copyBecomesRecordPathValue() throws Exception {
    var props = byPath(compile("{ \"$.title\": \"$.name\" }"));

    UpdateRecordProperty p = props.get("/title");
    assertEquals("/name", p.value());
    assertEquals(ReplacementStrategy.RECORD_PATH_VALUE, p.strategy());
  }

  @Test
  void nestedAndIndexedPathsTranslate() throws Exception {
    var props = byPath(compile("{ \"$.a.b[0].c\": \"$.x.y\" }"));

    UpdateRecordProperty p = props.get("/a/b[0]/c");
    assertEquals("/x/y", p.value());
  }

  @Test
  void coreArraySelectorsBecomeNifiWildcardsAndCopyWithinTheCurrentElement() throws Exception {
    var props = byPath(compile("{ \"$.items[].name\": \"$.items[].sourceName\" }"));

    UpdateRecordProperty p = props.get("/items[*]/name");
    assertEquals("../sourceName", p.value());
  }

  @Test
  void nestedArraySelectorsUseTheInnermostElementAsTheirRelativeContext() throws Exception {
    var props =
        byPath(
            compile(
                "{ \"$.orders[].items[].details.name\":"
                    + " \"$.orders[].items[].source.label\" }"));

    UpdateRecordProperty p = props.get("/orders[*]/items[*]/details/name");
    assertEquals("../../source/label", p.value());
  }

  @Test
  void rootScalarCanBeBroadcastIntoEveryArrayElement() throws Exception {
    var props = byPath(compile("{ \"$.items[].tenant\": \"$.tenant\" }"));

    assertEquals("/tenant", props.get("/items[*]/tenant").value());
  }

  @Test
  void conversionAndConcatInputsKeepTheTargetArrayContext() throws Exception {
    var props =
        byPath(
            compile(
                "{ \"$.items[].label\": { \"op\": \"concat\", \"separator\": \"-\","
                    + " \"inputs\": [\"$.items[].code\","
                    + " { \"op\": \"toString\", \"input\": \"$.items[].number\" }] } }"));

    assertEquals(
        "concat(../code, '-', toString(../number, 'UTF-8'))", props.get("/items[*]/label").value());
  }

  @Test
  void differentArrayContextsAreRejectedInsteadOfBeingPositionallyGuessed() throws Exception {
    // The target keeps its own array level, so no fan-out applies: a fork flattens one element per
    // record, which is precisely what a target that stays an array does not want. Pairing the two
    // levels element-wise is not expressible either — UpdateRecord assigns a multi-value selection
    // to every match rather than matching by position, so the result would be silently wrong data
    // instead of an error.
    FatalAdapterException error =
        assertThrows(
            FatalAdapterException.class,
            () -> compile("{ \"$.target[].name\": \"$.source[].name\" }"));

    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, error.getErrorCode());
  }

  @Test
  void arraySourceOnAFlatTargetFansOutAndReadsTheElementDirectly() throws Exception {
    // An array source on a flat target is the fan-out case: an upstream ForkRecord turns each
    // element into its own record, so the path below the array becomes a plain root-level selection
    // — no wildcard, and no relative '../' walk, both of which would re-introduce the multi-value
    // selection the fork exists to avoid.
    CompiledMapping compiled =
        compiler.compile(parse("{ \"$.name\": \"$.items[].name\" }"), GeometryEncoding.WKT);

    assertEquals("/items", compiled.fork().recordPath());
    assertEquals("/name", byPath(compiled.properties()).get("/name").value());
  }

  @Test
  void nestedArraysOnOnePathForkTheInnermostOne() throws Exception {
    // Two array levels on one hierarchical line are a single fan-out over the innermost array; the
    // outer level rides along as a parent field, which is why its path also flattens to root level.
    CompiledMapping compiled =
        compiler.compile(
            parse(
                "{ \"$.station\": \"$.stations[].name\","
                    + " \"$.value\": \"$.stations[].readings[].value\" }"),
            GeometryEncoding.WKT);

    assertEquals("/stations[*]/readings", compiled.fork().recordPath());
    Map<String, UpdateRecordProperty> props = byPath(compiled.properties());
    assertEquals("/name", props.get("/station").value());
    assertEquals("/value", props.get("/value").value());
  }

  @Test
  void anArrayTargetKeepsTheInPlaceFormAndDoesNotFanOut() throws Exception {
    // Source and target share one array context, so the mapping rewrites fields WITHIN each element
    // and the element count is unchanged. Forking here would flatten the array away and leave the
    // '/items[*]/name' target pointing at nothing — the fan-out must stay limited to flat targets.
    CompiledMapping compiled =
        compiler.compile(
            parse(
                "{ \"$.stationid\": \"$.stationid\","
                    + " \"$.items[].name\": \"$.items[].sourceName\" }"),
            GeometryEncoding.WKT);

    assertFalse(compiled.fork().required(), "an array-to-array mapping must not fan out");
    assertEquals("../sourceName", byPath(compiled.properties()).get("/items[*]/name").value());
  }

  @Test
  void anInPlaceRuleCannotShareAMappingWithAFanOutOverItsOwnArray() throws Exception {
    // Accepted, the fork would flatten '/items' away and '/items[*]/name' would address nothing —
    // an UpdateRecord no-op that drops the rule with no bulletin and no bad row.
    FatalAdapterException error =
        assertThrows(
            FatalAdapterException.class,
            () ->
                compile(
                    "{ \"$.value\": \"$.items[].value\","
                        + " \"$.items[].name\": \"$.items[].sourceName\" }"));

    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, error.getErrorCode());
    assertTrue(error.getMessage().contains("in-place array target"), error.getMessage());
  }

  @Test
  void anInPlaceRuleCannotShareAMappingWithAFanOutOverAnotherArray() throws Exception {
    // Accepted, the fork would repeat '$.items' into every fanned-out record, so one authored
    // rewrite would silently become N rows of duplicated data.
    FatalAdapterException error =
        assertThrows(
            FatalAdapterException.class,
            () ->
                compile(
                    "{ \"$.items[].name\": \"$.items[].sourceName\","
                        + " \"$.ts\": \"$.measurements[].ts\" }"));

    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, error.getErrorCode());
    assertTrue(error.getMessage().contains("in-place array target"), error.getMessage());
  }

  @Test
  void twoSourcesCollapsingOntoOnePostForkPathAreRejected() throws Exception {
    // ForkRecord hoists the element field over the ancestor one, so both rules would read '/id' and
    // silently carry the same value where the author asked for two distinct fields.
    FatalAdapterException error =
        assertThrows(
            FatalAdapterException.class,
            () -> compile("{ \"$.a\": \"$.id\", \"$.b\": \"$.readings[].id\" }"));

    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, error.getErrorCode());
    assertTrue(error.getMessage().contains("after the fan-out over"), error.getMessage());
  }

  @Test
  void theSameSourceReadTwiceIsNotACollision() throws Exception {
    // The collision check keys on the post-fork path, so it must not fire on two rules that
    // legitimately read the same source.
    CompiledMapping compiled =
        compiler.compile(
            parse("{ \"$.a\": \"$.items[].v\", \"$.b\": \"$.items[].v\" }"), GeometryEncoding.WKT);

    assertEquals("/items", compiled.fork().recordPath());
    Map<String, UpdateRecordProperty> props = byPath(compiled.properties());
    assertEquals("/v", props.get("/a").value());
    assertEquals("/v", props.get("/b").value());
  }

  @Test
  void aNestedAncestorObjectStaysAddressableThroughItsOwnField() throws Exception {
    // ForkRecord copies ancestor fields up under their own name and keeps their value shape, so
    // dropping the ancestor segments would resolve to nothing and write a silent NULL.
    CompiledMapping compiled =
        compiler.compile(
            parse("{ \"$.gw\": \"$.gateway.id\", \"$.v\": \"$.items[].value\" }"),
            GeometryEncoding.WKT);

    assertEquals("/gateway/id", byPath(compiled.properties()).get("/gw").value());
  }

  @Test
  void aConcreteArrayIndexIsNoFanOut() throws Exception {
    // The derivation keys on the '[]' selector alone; a concrete index multiplies nothing.
    CompiledMapping compiled =
        compiler.compile(parse("{ \"$.v\": \"$.items[0].value\" }"), GeometryEncoding.WKT);

    assertFalse(compiled.fork().required(), "a concrete index must not fan out");
    assertEquals("/items[0]/value", byPath(compiled.properties()).get("/v").value());
  }

  @Test
  void anArrayOfValuesIsRejectedRatherThanFannedOutIntoNothing() throws Exception {
    // ForkRecord's extract mode emits only RECORD elements and skips values silently — an array of
    // scalars would deploy a flow that runs cleanly and writes nothing. Rejecting at compile time
    // keeps that from looking like a working pipeline.
    FatalAdapterException error =
        assertThrows(FatalAdapterException.class, () -> compile("{ \"$.temp\": \"$.temps[]\" }"));

    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, error.getErrorCode());
    // Four compilers share this error code, so the code alone would not show which rule fired.
    assertTrue(error.getMessage().contains("selects the array itself"), error.getMessage());
  }

  @Test
  void aFixedElementOfTheForkedArrayIsRejected() throws Exception {
    // The index carries no array context of its own, so it survives into the UpdateRecord
    // unchanged — but ForkRecord drops the forked array from the record it emits, so the rule would
    // read nothing and write NULL on every row without failing.
    FatalAdapterException error =
        assertThrows(
            FatalAdapterException.class,
            () -> compile("{ \"$.x\": \"$.items[].x\", \"$.y\": \"$.items[0].y\" }"));

    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, error.getErrorCode());
    assertTrue(error.getMessage().contains("reads a fixed element"), error.getMessage());
  }

  @Test
  void anExpressionLanguageReferenceInAPathIsRejected() throws Exception {
    // A source segment reaches ForkRecord's EL-enabled fork property as its VALUE, where NiFi would
    // expand it against the process environment — and escaping is not available there, because the
    // escaped form can render the processor invalid, which NiFi skips silently on start.
    FatalAdapterException error =
        assertThrows(
            FatalAdapterException.class, () -> compile("{ \"$.v\": \"$.${HOSTNAME}[].v\" }"));

    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, error.getErrorCode());
    assertTrue(error.getMessage().contains("Expression Language"), error.getMessage());
  }

  @Test
  void independentSiblingArraysCannotFeedTheSameFlatTarget() throws Exception {
    // Two source arrays of unrelated length pair no elements: 3 measurements and 2 alarms is
    // neither 3, 2 nor 6 rows. This must stay rejected once array sources are allowed to fan out —
    // the fan-out has exactly one array context, and a cross product would be silently expensive.
    FatalAdapterException error =
        assertThrows(
            FatalAdapterException.class,
            () ->
                compile(
                    "{ \"$.ts\": \"$.measurements[].ts\"," + " \"$.code\": \"$.alarms[].code\" }"));

    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, error.getErrorCode());
    assertTrue(error.getMessage().contains("independent source arrays"), error.getMessage());
  }

  @Test
  void severalFieldsFromOneArrayShareASingleContext() throws Exception {
    // Several fields drawn from the SAME array are one fan-out, not several — the normal case from
    // the report, and the counterpart to the sibling case above that must stay rejected. Asserting
    // the context rather than the throw keeps this test meaningful after the fan-out fix, when the
    // compile itself starts to succeed.
    JsonPaths.ParsedPath ts = JsonPaths.parse("$.measurements[].ts");
    JsonPaths.ParsedPath value = JsonPaths.parse("$.measurements[].value");

    assertEquals(ts.arrayContext(), value.arrayContext());
    assertEquals(List.of("measurements[]"), ts.arrayContext());
  }

  @Test
  void constStringIsLiteralValue() throws Exception {
    // a bare RecordPath literal is not evaluated as a value by UpdateRecord, so const uses the
    // literal-value strategy (the builder isolates it in its own UpdateRecord)
    var props = byPath(compile("{ \"$.unit\": { \"op\": \"const\", \"value\": \"celsius\" } }"));

    UpdateRecordProperty p = props.get("/unit");
    assertEquals("celsius", p.value());
    assertEquals(ReplacementStrategy.LITERAL_VALUE, p.strategy());
  }

  @Test
  void constNumberIsLiteralValue() throws Exception {
    var props = byPath(compile("{ \"$.factor\": { \"op\": \"const\", \"value\": 42 } }"));

    UpdateRecordProperty p = props.get("/factor");
    assertEquals("42", p.value());
    assertEquals(ReplacementStrategy.LITERAL_VALUE, p.strategy());
  }

  @Test
  void escapesExpressionLanguageInConstValues() throws Exception {
    // UpdateRecord evaluates EL in dynamic property values: an unescaped ${…} const would expand
    // against the NiFi process environment and exfiltrate it into the record. $$ is EL's literal
    // escape.
    var props =
        byPath(
            compile(
                "{ \"$.note\": { \"op\": \"const\","
                    + " \"value\": \"${SINGLE_USER_CREDENTIALS_PASSWORD}\" } }"));

    UpdateRecordProperty p = props.get("/note");
    assertEquals("$${SINGLE_USER_CREDENTIALS_PASSWORD}", p.value());
    assertEquals(ReplacementStrategy.LITERAL_VALUE, p.strategy());
  }

  @Test
  void escapesExpressionLanguageInConcatSeparators() throws Exception {
    var props =
        byPath(
            compile(
                "{ \"$.k\": { \"op\": \"concat\", \"separator\": \"${HOSTNAME}\","
                    + " \"inputs\": [ \"$.a\", \"$.b\" ] } }"));

    assertEquals("concat(/a, '$${HOSTNAME}', /b)", props.get("/k").value());
  }

  @Test
  void concatBecomesRecordPathFunction() throws Exception {
    var props =
        byPath(
            compile(
                "{ \"$.geom\": { \"op\": \"concat\", \"separator\": \" \","
                    + " \"inputs\": [ \"$.lon\", \"$.lat\" ] } }"));

    UpdateRecordProperty p = props.get("/geom");
    assertEquals("concat(/lon, ' ', /lat)", p.value());
    assertEquals(ReplacementStrategy.RECORD_PATH_VALUE, p.strategy());
  }

  @Test
  void concatWithoutSeparatorOmitsJoiner() throws Exception {
    var props =
        byPath(compile("{ \"$.k\": { \"op\": \"concat\", \"inputs\": [ \"$.a\", \"$.b\" ] } }"));

    assertEquals("concat(/a, /b)", props.get("/k").value());
  }

  @Test
  void concatInlinesConstInputs() throws Exception {
    var props =
        byPath(
            compile(
                "{ \"$.label\": { \"op\": \"concat\", \"separator\": \"-\","
                    + " \"inputs\": [ \"$.id\", { \"op\": \"const\", \"value\": \"X\" } ] } }"));

    assertEquals("concat(/id, '-', 'X')", props.get("/label").value());
  }

  @Test
  void toDateBecomesRecordPathFunction() throws Exception {
    var props =
        byPath(
            compile(
                "{ \"$.observed_at\": { \"op\": \"toDate\", \"input\": \"$.ts\","
                    + " \"pattern\": \"yyyy-MM-dd\" } }"));

    UpdateRecordProperty p = props.get("/observed_at");
    assertEquals("toDate(/ts, 'yyyy-MM-dd')", p.value());
    assertEquals(ReplacementStrategy.RECORD_PATH_VALUE, p.strategy());
  }

  @Test
  void formatBecomesRecordPathFunction() throws Exception {
    var props =
        byPath(
            compile(
                "{ \"$.s\": { \"op\": \"format\", \"input\": \"$.d\","
                    + " \"pattern\": \"yyyy\" } }"));

    assertEquals("format(/d, 'yyyy')", props.get("/s").value());
  }

  @Test
  void toStringBecomesRecordPathFunctionWithCharsetArgument() throws Exception {
    var props = byPath(compile("{ \"$.s\": { \"op\": \"toString\", \"input\": \"$.n\" } }"));

    UpdateRecordProperty p = props.get("/s");
    // NiFi's toString requires the charset arg; a single-arg call fails to parse at deploy time.
    assertEquals("toString(/n, 'UTF-8')", p.value());
    assertEquals(ReplacementStrategy.RECORD_PATH_VALUE, p.strategy());
  }

  @Test
  void toIntIsTransparentAndDefersToSchemaCoercion() throws Exception {
    var props = byPath(compile("{ \"$.count\": { \"op\": \"toInt\", \"input\": \"$.n\" } }"));

    UpdateRecordProperty p = props.get("/count");
    assertEquals("/n", p.value());
    assertEquals(ReplacementStrategy.RECORD_PATH_VALUE, p.strategy());
  }

  @Test
  void toFloatIsTransparent() throws Exception {
    var props = byPath(compile("{ \"$.v\": { \"op\": \"toFloat\", \"input\": \"$.raw\" } }"));

    assertEquals("/raw", props.get("/v").value());
  }

  @Test
  void geoPointForPostgisBecomesWktConcat() throws Exception {
    var props =
        byPath(
            compile(
                "{ \"$.geo\": { \"op\": \"geoPoint\", \"lon\": \"$.lon\", \"lat\": \"$.lat\" } }",
                GeometryEncoding.WKT));

    UpdateRecordProperty p = props.get("/geo");
    // WKT is POINT(lon lat); no SRID prefix — the geometry column stamps its own SRID on insert.
    assertEquals("concat('POINT(', /lon, ' ', /lat, ')')", p.value());
    assertEquals(ReplacementStrategy.RECORD_PATH_VALUE, p.strategy());
  }

  @Test
  void geoPointPreservesLonLatOrderAndInlinesConversions() throws Exception {
    // lon must come first (POINT(lon lat)); a per-coordinate conversion is inlined transparently.
    var props =
        byPath(
            compile(
                "{ \"$.geo\": { \"op\": \"geoPoint\", \"lon\": \"$.x\","
                    + " \"lat\": { \"op\": \"toFloat\", \"input\": \"$.y\" } } }",
                GeometryEncoding.WKT));

    assertEquals("concat('POINT(', /x, ' ', /y, ')')", props.get("/geo").value());
  }

  @Test
  void geoPointForFrostRendersAGeoJsonPointString() throws Exception {
    // RecordPath has no object constructor, so the GeoJSON Point is concatenated as a string; the
    // FROST body template embeds the flat field verbatim, turning it back into a JSON object.
    var props =
        byPath(
            compile(
                "{ \"$.geo\": { \"op\": \"geoPoint\", \"lon\": \"$.lon\", \"lat\": \"$.lat\" } }",
                GeometryEncoding.GEOJSON));

    assertEquals(
        "concat('{\"type\":\"Point\",\"coordinates\":[', /lon, ',', /lat, ']}')",
        props.get("/geo").value());
  }

  @Test
  void emitsOnePropertyPerField() throws Exception {
    var props = compile("{ \"$.a\": \"$.x\", \"$.b\": \"$.y\" }");

    assertEquals(2, props.size());
  }

  @Test
  void constInputWithSingleQuoteIsEscaped() throws Exception {
    var props =
        byPath(
            compile(
                "{ \"$.label\": { \"op\": \"concat\", \"inputs\": [ \"$.id\","
                    + " { \"op\": \"const\", \"value\": \"O'Brien\" } ] } }"));

    // the embedded quote must be backslash-escaped so it cannot break out of the RecordPath literal
    assertEquals("concat(/id, 'O\\'Brien')", props.get("/label").value());
  }

  @Test
  void concatSeparatorWithSingleQuoteIsEscaped() throws Exception {
    var props =
        byPath(
            compile(
                "{ \"$.k\": { \"op\": \"concat\", \"separator\": \"'\","
                    + " \"inputs\": [ \"$.a\", \"$.b\" ] } }"));

    assertEquals("concat(/a, '\\'', /b)", props.get("/k").value());
  }

  /**
   * A compiled value expression is only a string here; NiFi rejects an ill-formed one (wrong arity,
   * unknown function) at flow deploy, not at compile time in this adapter. Parsing each emitted
   * expression with NiFi's own RecordPath grammar catches that whole class of errors — including
   * #1924's single-arg {@code toString} — at unit time instead. Every op that renders a
   * record-path-value expression is covered.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "{ \"$.t\": \"$.n\" }",
        "{ \"$.items[].name\": \"$.items[].sourceName\" }",
        "{ \"$.s\": { \"op\": \"toString\", \"input\": \"$.n\" } }",
        "{ \"$.d\": { \"op\": \"toDate\", \"input\": \"$.ts\", \"pattern\": \"yyyy-MM-dd\" } }",
        "{ \"$.f\": { \"op\": \"format\", \"input\": \"$.d\", \"pattern\": \"yyyy\" } }",
        "{ \"$.i\": { \"op\": \"toInt\", \"input\": \"$.n\" } }",
        "{ \"$.label\": { \"op\": \"concat\", \"separator\": \"-\","
            + " \"inputs\": [ \"$.a\", { \"op\": \"toString\", \"input\": \"$.n\" } ] } }",
        "{ \"$.geo\": { \"op\": \"geoPoint\", \"lon\": \"$.lon\", \"lat\": \"$.lat\" } }"
      })
  void emittedRecordPathValuesParseWithNifiGrammar(String fields) throws Exception {
    // Both encodings: geoPoint renders differently per sink (WKT concat vs GeoJSON-string concat),
    // so each shape must parse — FROST deploys the GEOJSON one.
    for (GeometryEncoding encoding : GeometryEncoding.values()) {
      for (UpdateRecordProperty p : compile(fields, encoding)) {
        if (p.strategy() == ReplacementStrategy.RECORD_PATH_VALUE) {
          RecordPath.compile(p.value());
        }
      }
    }
  }
}
