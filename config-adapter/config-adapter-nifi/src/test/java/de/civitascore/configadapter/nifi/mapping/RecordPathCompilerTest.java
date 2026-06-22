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

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.ReplacementStrategy;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RecordPathCompilerTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final MappingConfigParser parser = new MappingConfigParser();
  private final RecordPathCompiler compiler = new RecordPathCompiler();

  private List<UpdateRecordProperty> compile(String fieldsJson) throws Exception {
    MappingConfig mc = parser.parse(mapper.readTree("{ \"fields\": " + fieldsJson + " }"));
    return compiler.compile(mc);
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
}
