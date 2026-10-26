/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connectiontest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonBodyTest {

  @Test
  void detectsUtf16AsThePipelineDoes() {
    String body = "[{\"name\":\"Grüße\"}]";

    assertEquals(
        JsonBody.RECORDS, JsonBody.classify(body.getBytes(StandardCharsets.UTF_16), false));
    assertEquals(
        JsonBody.RECORDS, JsonBody.classify(body.getBytes(StandardCharsets.UTF_16LE), false));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"a\":1}", "[{\"a\":1},{\"b\":2}]", " \n[ {\"a\":[1,2]} ]"})
  void anObjectOrAnArrayOfObjectsGivesRecords(String body) {
    assertEquals(JsonBody.RECORDS, classify(body, false));
  }

  @ParameterizedTest
  @ValueSource(strings = {"[]", "[1,2]", "\"text\"", "42", "null"})
  void otherJsonGivesNoRecords(String body) {
    assertEquals(JsonBody.NO_RECORDS, classify(body, false));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"<html></html>", "Grüße", "{\"a\":1", "{\"a\":1} trailing", "id,name\n1,a"})
  void anythingElseIsNotJson(String body) {
    assertEquals(JsonBody.NOT_JSON, classify(body, false));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"a\":\"cut", "[{\"a\":1},{\"b\":"})
  void aCutBodyIsJudgedByItsStart(String start) {
    assertEquals(JsonBody.RECORDS, classify(start, true));
  }

  @ParameterizedTest
  @ValueSource(strings = {"<html><body>cut", "{\"a\":1} <b"})
  void aCutBodyThatIsNotJsonBeforeTheCutIsNotJson(String start) {
    assertEquals(JsonBody.NOT_JSON, classify(start, true));
  }

  private static JsonBody classify(String body, boolean truncated) {
    return JsonBody.classify(body.getBytes(StandardCharsets.UTF_8), truncated);
  }
}
