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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConcatNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConstNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConvertNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.CopyNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.GeoPointNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class MappingConfigParserTest {

  private static final String SOURCE_URN =
      "urn:core:platform:civitas:datastructure:common:Source:2dmtus8w40:1.0.0";
  private static final String TARGET_URN =
      "urn:core:platform:civitas:datastructure:common:Target:abcdefghij:1.0.0";

  private final ObjectMapper mapper = new ObjectMapper();
  private final MappingConfigParser parser = new MappingConfigParser();

  private MappingConfig parse(String json) throws Exception {
    JsonNode node = mapper.readTree(json);
    return parser.parse(node);
  }

  @Test
  void parsesSourceAndTarget() throws Exception {
    MappingConfig mc =
        parse(
            """
            {
              "$schema": "https://civitasconnect.digital/core/mapping/v1",
              "source": "%s",
              "target": "%s",
              "fields": { "$.a": "$.b" }
            }
            """
                .formatted(SOURCE_URN, TARGET_URN));

    assertEquals(SOURCE_URN, mc.source());
    assertEquals(TARGET_URN, mc.target());
    assertEquals(1, mc.fields().size());
  }

  @Test
  void acceptsAbsentSourceAndTarget() throws Exception {
    MappingConfig mc = parse("{ \"fields\": { \"$.a\": \"$.b\" } }");

    assertEquals(null, mc.source());
    assertEquals(null, mc.target());
  }

  @Test
  void rejectsMalformedSourceUrn() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                parse(
                    """
                    { "source": "not-a-core-urn", "fields": { "$.a": "$.b" } }
                    """));
    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
  }

  @Test
  void shorthandStringIsCopy() throws Exception {
    MappingConfig mc =
        parse(
            """
        { "fields": { "$.title": "$.name" } }
        """);

    ValueNode v = mc.fields().get("$.title");
    CopyNode copy = assertInstanceOf(CopyNode.class, v);
    assertEquals("$.name", copy.sourcePath());
  }

  @Test
  void explicitCopyOp() throws Exception {
    MappingConfig mc =
        parse(
            """
        { "fields": { "$.title": { "op": "copy", "sourcePath": "$.name" } } }
        """);

    CopyNode copy = assertInstanceOf(CopyNode.class, mc.fields().get("$.title"));
    assertEquals("$.name", copy.sourcePath());
  }

  @Test
  void constStringWithType() throws Exception {
    MappingConfig mc =
        parse(
            """
        { "fields": { "$.unit": { "op": "const", "value": "celsius", "valueType": "string" } } }
        """);

    ConstNode c = assertInstanceOf(ConstNode.class, mc.fields().get("$.unit"));
    assertEquals("celsius", c.value());
    assertEquals("string", c.valueType());
  }

  @Test
  void constNumber() throws Exception {
    MappingConfig mc =
        parse(
            """
        { "fields": { "$.factor": { "op": "const", "value": 42 } } }
        """);

    ConstNode c = assertInstanceOf(ConstNode.class, mc.fields().get("$.factor"));
    assertEquals(42, ((Number) c.value()).intValue());
  }

  @Test
  void concatWithMixedInputs() throws Exception {
    MappingConfig mc =
        parse(
            """
        { "fields": { "$.geom": { "op": "concat", "separator": " ",
            "inputs": [ "$.lon", { "op": "const", "value": "X" } ] } } }
        """);

    ConcatNode concat = assertInstanceOf(ConcatNode.class, mc.fields().get("$.geom"));
    assertEquals(" ", concat.separator());
    assertEquals(2, concat.inputs().size());
    assertInstanceOf(CopyNode.class, concat.inputs().get(0));
    assertInstanceOf(ConstNode.class, concat.inputs().get(1));
  }

  @Test
  void toDateWithPattern() throws Exception {
    MappingConfig mc =
        parse(
            """
        { "fields": { "$.observed_at": { "op": "toDate", "input": "$.ts", "pattern": "yyyy-MM-dd" } } }
        """);

    ConvertNode conv = assertInstanceOf(ConvertNode.class, mc.fields().get("$.observed_at"));
    assertEquals(ConversionOp.TO_DATE, conv.op());
    assertEquals("yyyy-MM-dd", conv.pattern());
    assertInstanceOf(CopyNode.class, conv.input());
  }

  @Test
  void toStringWrapsNestedNode() throws Exception {
    MappingConfig mc =
        parse(
            """
        { "fields": { "$.s": { "op": "toString", "input": { "op": "copy", "sourcePath": "$.n" } } } }
        """);

    ConvertNode conv = assertInstanceOf(ConvertNode.class, mc.fields().get("$.s"));
    assertEquals(ConversionOp.TO_STRING, conv.op());
    CopyNode inner = assertInstanceOf(CopyNode.class, conv.input());
    assertEquals("$.n", inner.sourcePath());
  }

  @Test
  void geoPointParsesNamedLonLatOperands() throws Exception {
    MappingConfig mc =
        parse(
            """
        { "fields": { "$.geo": { "op": "geoPoint", "lon": "$.lon",
            "lat": { "op": "toFloat", "input": "$.lat" } } } }
        """);

    GeoPointNode geo = assertInstanceOf(GeoPointNode.class, mc.fields().get("$.geo"));
    CopyNode lon = assertInstanceOf(CopyNode.class, geo.lon());
    assertEquals("$.lon", lon.sourcePath());
    ConvertNode lat = assertInstanceOf(ConvertNode.class, geo.lat());
    assertEquals(ConversionOp.TO_FLOAT, lat.op());
  }

  @Test
  void geoPointMissingLatIsRejected() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                parse(
                    """
            { "fields": { "$.geo": { "op": "geoPoint", "lon": "$.lon" } } }
            """));
    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
  }

  @Test
  void blankShorthandSourcePathIsRejected() {
    // A blank path would resolve to the record root '/' and silently copy the whole record — for a
    // coordinate that yields garbage WKT, so it must fail at parse time.
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                parse(
                    """
            { "fields": { "$.geo": { "op": "geoPoint", "lon": "", "lat": "$.lat" } } }
            """));
    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
  }

  @Test
  void blankExplicitCopySourcePathIsRejected() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                parse(
                    """
            { "fields": { "$.x": { "op": "copy", "sourcePath": "   " } } }
            """));
    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
  }

  @Test
  void nestedGeoPointOperandIsRejected() {
    // A geoPoint coordinate must be a scalar; a geometry nested as an operand would render to
    // malformed WKT, so it is rejected at parse time.
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                parse(
                    """
            { "fields": { "$.geo": { "op": "geoPoint",
                "lon": { "op": "geoPoint", "lon": "$.a", "lat": "$.b" }, "lat": "$.lat" } } }
            """));
    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
  }

  @Test
  void nonDateOpWithStrayPatternIsRejected() {
    // toInt does not take a pattern; a stray one is an illegal combination and must be rejected
    // (not silently ignored), so it can never reach the RecordPath compiler.
    assertThrows(
        FatalAdapterException.class,
        () ->
            parse(
                """
        { "fields": { "$.i": { "op": "toInt", "input": "$.a", "pattern": "###" } } }
        """));
  }

  @Test
  void numericConversionOpsParse() throws Exception {
    MappingConfig mc =
        parse(
            """
        { "fields": {
            "$.i": { "op": "toInt", "input": "$.a" },
            "$.f": { "op": "toFloat", "input": "$.b" } } }
        """);

    assertEquals(
        ConversionOp.TO_INT, assertInstanceOf(ConvertNode.class, mc.fields().get("$.i")).op());
    assertEquals(
        ConversionOp.TO_FLOAT, assertInstanceOf(ConvertNode.class, mc.fields().get("$.f")).op());
  }

  @Test
  void fieldOrderIsPreserved() throws Exception {
    MappingConfig mc =
        parse(
            """
        { "fields": { "$.c": "$.x", "$.a": "$.y", "$.b": "$.z" } }
        """);

    assertEquals(List.of("$.c", "$.a", "$.b"), List.copyOf(mc.fields().keySet()));
  }

  @Test
  void unknownOpIsRejected() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                parse(
                    """
            { "fields": { "$.x": { "op": "evalScript", "code": "system('rm -rf /')" } } }
            """));
    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
  }

  @Test
  void toDateWithoutPatternIsRejected() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                parse(
                    """
            { "fields": { "$.ts": { "op": "toDate", "input": "$.raw" } } }
            """));
    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
  }

  @Test
  void formatWithBlankPatternIsRejected() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                parse(
                    """
            { "fields": { "$.s": { "op": "format", "input": "$.d", "pattern": "  " } } }
            """));
    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
  }

  @Test
  void copyWithoutSourcePathIsRejected() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                parse(
                    """
            { "fields": { "$.x": { "op": "copy" } } }
            """));
    assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
  }

  @Test
  void nonStringNonObjectFieldValueIsRejected() {
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                parse(
                    """
            { "fields": { "$.x": 123 } }
            """));
    assertTrue(ex.getErrorCode() == AdapterErrorCode.NIFI_MAPPING_ERROR);
  }
}
