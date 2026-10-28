/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class JsonPathTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final List<String> VALID =
      List.of("$.next_cursor", "$.data[0].id", "$['next cursor']", "$.a-b", "$[0]", "$._x9");

  private static final List<String> INVALID =
      List.of(
          "$",
          "next",
          "",
          "$..cursor",
          "$.items[?(@.id)]",
          "$.a.*",
          "$['']",
          "$['a\\'b']",
          "$['a\\\\b']",
          "$[-1]",
          "$.0a",
          "$.a[0",
          "$.a b",
          " $.a");

  private static final Path PORTAL_CONNECTOR_CONFIGURATION =
      Path.of(
          "../../portal-backend/src/main/java/de/civitascore/portal/model/connector"
              + "/HttpConnectorConfiguration.java");

  private static final Pattern JSON_PATH_CONSTANT =
      Pattern.compile("JSON_PATH =\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

  private static List<String> valid() {
    return VALID;
  }

  private static List<String> invalid() {
    return INVALID;
  }

  private static Stream<String> validAndInvalid() {
    return Stream.concat(VALID.stream(), INVALID.stream());
  }

  @ParameterizedTest
  @MethodSource("valid")
  void parse_withMemberNamesIndexesAndQuotedNames_accepts(String text) {
    assertEquals(Optional.of(new JsonPath(text)), JsonPath.parse(text));
  }

  @ParameterizedTest
  @MethodSource("invalid")
  void parse_withAFormOutsideTheGrammar_isEmpty(String text) {
    assertEquals(Optional.empty(), JsonPath.parse(text));
    assertThrows(IllegalArgumentException.class, () -> new JsonPath(text));
  }

  /**
   * A grammar changed in the portal backend alone would let it store a path the processor refuses.
   */
  @ParameterizedTest
  @MethodSource("validAndInvalid")
  void parse_acceptsWhatThePortalBackendAccepts(String text) throws IOException {
    Matcher constant = JSON_PATH_CONSTANT.matcher(Files.readString(PORTAL_CONNECTOR_CONFIGURATION));
    assertTrue(constant.find(), PORTAL_CONNECTOR_CONFIGURATION::toString);
    Pattern portalBackend = Pattern.compile(constant.group(1).replaceAll("\\\\(.)", "$1"));

    assertEquals(portalBackend.matcher(text).matches(), JsonPath.parse(text).isPresent());
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '\'',
      value = {
        "$.next_cursor         | \"abc\"",
        "$.data[1].id          | 7",
        "$['next cursor']      | true",
        "$['a.b'][0]           | \"dot\"",
        "$['x][0']             | \"bracket\"",
        "$.meta.items          | [1,2]"
      })
  void valueIn_followsMembersAndIndexes(String path, String expected)
      throws JsonProcessingException {
    JsonNode root =
        MAPPER.readTree(
            """
            {"next_cursor": "abc",
             "data": [{"id": 6}, {"id": 7}],
             "next cursor": true,
             "a.b": ["dot"],
             "x][0": "bracket",
             "meta": {"items": [1, 2]}}""");

    assertEquals(MAPPER.readTree(expected), new JsonPath(path).valueIn(root));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "$.absent",
        "$[0]",
        "$.list.id",
        "$.list[5]",
        "$.text.length",
        "$.text[0]",
        "$.list[2147483648]",
        "$.list[99999999999999999999]"
      })
  void valueIn_withAStepThatDoesNotFit_isMissing(String path) throws JsonProcessingException {
    JsonNode root = MAPPER.readTree("{\"list\": [1, 2], \"text\": \"abc\"}");

    assertTrue(new JsonPath(path).valueIn(root).isMissingNode());
  }

  @Test
  void valueIn_withLeadingZerosInAnIndex_readsThatIndex() throws JsonProcessingException {
    JsonNode root = MAPPER.readTree("{\"list\": [1, 2]}");

    assertEquals(2, new JsonPath("$.list[0001]").valueIn(root).intValue());
  }

  @Test
  void valueIn_withAQuotedDigitName_readsTheMemberNotAnIndex() throws JsonProcessingException {
    JsonNode object = MAPPER.readTree("{\"0\": \"member\"}");
    JsonNode array = MAPPER.readTree("[\"element\"]");

    assertEquals("member", new JsonPath("$['0']").valueIn(object).textValue());
    assertTrue(new JsonPath("$[0]").valueIn(object).isMissingNode());
    assertTrue(new JsonPath("$['0']").valueIn(array).isMissingNode());
    assertEquals("element", new JsonPath("$[0]").valueIn(array).textValue());
  }
}
