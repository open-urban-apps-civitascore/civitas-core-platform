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

import static de.civitascore.nifi.http.GetHttpApiCallRunners.assertRouted;
import static de.civitascore.nifi.http.GetHttpApiCallRunners.invalid;
import static de.civitascore.nifi.http.GetHttpApiCallRunners.logged;
import static de.civitascore.nifi.http.GetHttpApiCallRunners.visible;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.nifi.http.ApiEndpoint.Response;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.LogMessage;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class GetHttpApiCallTest {

  private static final String FULL_PAGE = "[{\"stationid\":\"a1\"},{\"stationid\":\"a2\"}]";

  private static final String SHORT_PAGE = "[{\"stationid\":\"a3\"}]";

  /**
   * A response of a cursor pagination, or of a limit/offset pagination with a count path, is one
   * record; it holds the records under {@code data}.
   */
  private static final String ONE_RECORD_SCHEMA =
      """
      {"$schema": "https://json-schema.org/draft/2020-12/schema",
       "type": "object",
       "required": ["data"],
       "properties": {"data": {"type": "array",
                               "items": {"type": "object", "required": ["stationid"]}}}}""";

  private static final String CURSOR_IN_QUERY =
      "\"cursorParameter\": \"cursor\", \"cursorIn\": \"QUERY\"";

  private static final String CURSOR_IN_HEADER =
      "\"cursorParameter\": \"X-Cursor\", \"cursorIn\": \"HEADER\"";

  private static final String LIMIT_2 = ", \"limitParameter\": \"limit\", \"limit\": 2";

  @TempDir static Path keys;

  private static TestTls tls;

  @BeforeAll
  static void createCertificate() throws IOException, GeneralSecurityException {
    tls = TestTls.create(keys);
  }

  @Test
  void onTrigger_withQueryParameters_sendsAGetToTheBasePathAndThePathWithTheQueryInOrder()
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, "[]")) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(
          GetHttpApiCall.API_CALL,
          """
          {"name": "Stations", "method": "GET", "path": "/stations",
           "queryParameters": [{"name": "status", "value": "active"},
                               {"name": "tag", "value": "a"},
                               {"name": "tag", "value": "b"},
                               {"name": "empty", "value": null}],
           "headers": null, "pagination": null}""");

      runner.run();

      ApiEndpoint.Request request = endpoint.requests().get(0);
      assertEquals("GET", request.method());
      assertEquals("/v1/stations", request.rawPath());
      assertEquals("status=active&tag=a&tag=b&empty=", request.rawQuery());
    }
  }

  @Test
  void onTrigger_withATrailingSlashOnTheBaseUrl_joinsThePathWithOneSlash()
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, "[]")) {
      TestRunner runner = runner(endpoint.baseUrl() + "/");

      runner.run();

      assertEquals("/v1/stations", endpoint.requests().get(0).rawPath());
      assertNull(endpoint.requests().get(0).rawQuery());
    }
  }

  @Test
  void onTrigger_withReservedCharactersInAValue_deliversTheConfiguredValue()
      throws InitializationException {
    String value = "a&b=c+d e ü";
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, "[]")) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(
          GetHttpApiCall.API_CALL,
          "{\"name\":\"Stations\",\"path\":\"/stations\",\"queryParameters\":["
              + "{\"name\":\"q 1\",\"value\":\""
              + value
              + "\"},{\"name\":\"next\",\"value\":\"x\"}]}");

      runner.run();

      String rawQuery = endpoint.requests().get(0).rawQuery();
      assertEquals("q%201=a%26b%3Dc%2Bd%20e%20%C3%BC&next=x", rawQuery);
      String[] first = rawQuery.split("&")[0].split("=");
      assertEquals("q 1", URLDecoder.decode(first[0], StandardCharsets.UTF_8));
      assertEquals(value, URLDecoder.decode(first[1], StandardCharsets.UTF_8));
    }
  }

  @Test
  void onTrigger_withHeaders_sendsThem() throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, "[]")) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(
          GetHttpApiCall.API_CALL,
          """
          {"name": "Stations", "path": "/stations",
           "headers": [{"name": "Accept", "value": "application/json"},
                       {"name": "X-Api-Version", "value": "2"}]}""");

      runner.run();

      ApiEndpoint.Request request = endpoint.requests().get(0);
      assertEquals("application/json", request.headers().getFirst("Accept"));
      assertEquals("2", request.headers().getFirst("X-Api-Version"));
    }
  }

  @Test
  void onTrigger_withBasicAuthentication_sendsTheAuthorizationHeader()
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answeringOverTls(tls.server(), 200, "[]")) {
      TestRunner runner = runner(endpoint.baseUrl(), tls);
      runner.setProperty(GetHttpApiCall.AUTHENTICATION_TYPE, "BASIC");
      runner.setProperty(GetHttpApiCall.BASIC_AUTH_USERNAME, "weather");
      runner.setProperty(GetHttpApiCall.BASIC_AUTH_PASSWORD, "s3cret:ü");

      runner.run();

      String expected =
          "Basic "
              + Base64.getEncoder()
                  .encodeToString("weather:s3cret:ü".getBytes(StandardCharsets.UTF_8));
      assertEquals(expected, endpoint.requests().get(0).headers().getFirst("Authorization"));
    }
  }

  @Test
  void onTrigger_withoutAuthentication_sendsNoAuthorizationHeader() throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, "[]")) {
      TestRunner runner = runner(endpoint.baseUrl());

      runner.run();

      assertFalse(endpoint.requests().get(0).headers().containsKey("Authorization"));
    }
  }

  @Test
  void onTrigger_whenTheResponseHasABody_routesItUnchangedToSuccess()
      throws InitializationException {
    String body = "[{\"stationid\":\"a1\",\"name\":\"Münster\"},{\"stationid\":\"a2\"}]";
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, body)) {
      TestRunner runner = runner(endpoint.baseUrl());

      runner.run();

      runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, 1);
      MockFlowFile flowFile = runner.getFlowFilesForRelationship(GetHttpApiCall.SUCCESS).get(0);
      flowFile.assertContentEquals(body, StandardCharsets.UTF_8);
      flowFile.assertAttributeEquals("mime.type", "application/json");
      flowFile.assertAttributeEquals(GetHttpApiCall.STATUS_CODE, "200");
      flowFile.assertAttributeEquals(GetHttpApiCall.REQUEST_PATH, "/stations");
    }
  }

  @Test
  void onTrigger_whenTheResponseIsAnEmptyArray_routesItToSuccess() throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, "[]")) {
      TestRunner runner = runner(endpoint.baseUrl());

      runner.run();

      runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, 1);
      runner.getFlowFilesForRelationship(GetHttpApiCall.SUCCESS).get(0).assertContentEquals("[]");
    }
  }

  @Test
  void onTrigger_whenTheResponseIsAnObjectThatAgrees_routesItUnchangedToSuccess()
      throws InitializationException {
    String body = "{\"stationid\":\"a1\",\"lat\":51.96}";
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, body)) {
      TestRunner runner = runner(endpoint.baseUrl());

      runner.run();

      runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, 1);
      runner.getFlowFilesForRelationship(GetHttpApiCall.SUCCESS).get(0).assertContentEquals(body);
    }
  }

  @Test
  void onTrigger_whenOneRecordViolatesTheRecordSchema_routesTheWholeResponseToStop()
      throws InitializationException {
    String body =
        "[{\"stationid\":\"a1\",\"lat\":1},{\"stationid\":\"a2\",\"lat\":\"x\"},{\"lat\":3}]";
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, body)) {
      TestRunner runner = runner(endpoint.baseUrl());

      runner.run();

      String message =
          "API call \"Stations\": 2 of 3 records violate the Data structure:"
              + " record 1: /lat type; record 2: required stationid";
      assertRouted(runner, GetHttpApiCall.STOP, 200, message);
      List<LogMessage> errors = runner.getLogger().getErrorMessages();
      assertEquals(1, errors.size());
      assertTrue(errors.get(0).getMsg().contains(message), errors.get(0).getMsg());
    }
  }

  @Test
  void onTrigger_whenManyRecordsViolate_namesTheFirstThree() throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, "[{},{},{},{},{\"lat\":\"x\"}]")) {
      TestRunner runner = runner(endpoint.baseUrl());

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": 5 of 5 records violate the Data structure:"
              + " record 0: required stationid; record 1: required stationid;"
              + " record 2: required stationid");
    }
  }

  @Test
  void onTrigger_whenAnArrayHoldsAScalar_routesToStop() throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, "[{\"stationid\":\"a1\"},7]")) {
      TestRunner runner = runner(endpoint.baseUrl());

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": 1 of 2 records violate the Data structure: record 1: type");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"7", "\"val-1f3e\"", "true", "null", "[]", "[{\"stationid\":\"a0\"}]"})
  void onTrigger_whenAnArrayHoldsANonObjectAndTheSchemaAllowsIt_routesToStop(String element)
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answering(200, "[" + element + ",{\"stationid\":\"a1\"}]")) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(GetHttpApiCall.RECORD_SCHEMA, "{\"required\": [\"stationid\"]}");

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": 1 of 2 records violate the Data structure: record 0: type");
      assertFalse(logged(runner.getLogger()).toString().contains("val-1f3e"));
    }
  }

  @Test
  void onTrigger_whenARecordViolates_keepsItsValuesOutOfLogsAndAttributes()
      throws InitializationException {
    String secret = "val-4e2a9c";
    String body =
        "[{\"stationid\":\""
            + secret
            + "\",\"name\":\"long-"
            + secret
            + "-long-long\",\"lat\":\""
            + secret
            + "\"}]";
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, body)) {
      TestRunner runner = runner(endpoint.baseUrl());

      runner.run();

      runner.assertAllFlowFilesTransferred(GetHttpApiCall.STOP, 1);
      MockFlowFile stopped = runner.getFlowFilesForRelationship(GetHttpApiCall.STOP).get(0);
      stopped.assertContentEquals("");
      List<String> visible = new ArrayList<>(logged(runner.getLogger()));
      visible.addAll(stopped.getAttributes().values());
      for (String text : visible) {
        assertFalse(text.contains(secret), text);
      }
    }
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '\'',
      value = {
        "'\"val-1f3e\"' | string",
        "42             | number",
        "true           | boolean",
        "null           | null"
      })
  void onTrigger_whenTheResponseIsAJsonScalar_routesToStop(String body, String kind)
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, body)) {
      TestRunner runner = runner(endpoint.baseUrl());

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": the response is a JSON " + kind + ", not an object or an array");
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "<html>val-1f3e</html>",
        "{\"stationid\":",
        "[] [\"val-1f3e\"]",
        "[{\"stationid\":\"a1\"},",
        "[{\"lat\":\"x\"}] <html>val-1f3e</html>",
        // The start of an MP4 file, which Jackson takes for UTF-32 and cannot decode.
        "\0\0\0\030ftypisom"
      })
  void onTrigger_whenTheResponseIsNotJson_routesToStop(String body) throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, body)) {
      TestRunner runner = runner(endpoint.baseUrl());

      runner.run();

      assertRouted(
          runner, GetHttpApiCall.STOP, 200, "API call \"Stations\": the response is not JSON");
      assertFalse(logged(runner.getLogger()).toString().contains("val-1f3e"));
    }
  }

  @Test
  void onTrigger_whenTheResponseIsAnObjectThatViolates_routesToStopAsRecordZero()
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, "{\"lat\":\"x\"}")) {
      TestRunner runner = runner(endpoint.baseUrl());

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": 1 of 1 records violate the Data structure:"
              + " record 0: /lat type; record 0: required stationid");
    }
  }

  @Test
  void onTrigger_whenAPropertyNameHoldsATildeAndASlash_escapesItInThePointer()
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, "[{\"a/b~c\":\"x\"}]")) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(
          GetHttpApiCall.RECORD_SCHEMA, "{\"properties\": {\"a/b~c\": {\"type\": \"number\"}}}");

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": 1 of 1 records violate the Data structure:"
              + " record 0: /a~1b~0c type");
    }
  }

  @Test
  void onTrigger_whenAViolationIsBelowAPropertyTheSchemaDoesNotDeclare_namesThatPropertyStar()
      throws InitializationException {
    String schema =
        """
        {"type": "object",
         "properties": {"sensors": {"type": "object", "additionalProperties": {
           "properties": {"readings": {"items": {"properties": {"value": {"type": "number"}}}}}}}},
         "patternProperties": {"^x-": {"type": "number"}}}""";
    String body =
        "[{\"sensors\":{\"k-91aa\\nFORGED\":{\"readings\":[{\"value\":1},{\"value\":\"v\"}]}},"
            + "\"x-k-91aa\":\"v\"}]";
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, body)) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(GetHttpApiCall.RECORD_SCHEMA, schema);

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": 1 of 1 records violate the Data structure:"
              + " record 0: /sensors/*/readings/1/value type; record 0: /* type");
      for (String text : logged(runner.getLogger())) {
        assertFalse(text.contains("k-91aa"), text);
      }
    }
  }

  @Test
  void onTrigger_withALineBreakInARequiredPropertyOfTheRecordSchema_escapesItInLogsAndAttributes()
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, "[{}]")) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(GetHttpApiCall.RECORD_SCHEMA, "{\"required\": [\"sta\\r\\ntionid\"]}");

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": 1 of 1 records violate the Data structure:"
              + " record 0: required sta\\r\\ntionid");
      for (String text : logged(runner.getLogger())) {
        assertFalse(text.matches("(?s).*[\\r\\n].*"), text);
      }
    }
  }

  @Test
  void onTrigger_withAGeoJsonGeometryInTheRecordSchema_validatesTheGeometry()
      throws InitializationException {
    String schema =
        """
        {"type": "object",
         "properties": {"geom": {"$ref": "https://geojson.org/schema/Point.json",
                                 "crs": "EPSG:25832"}}}""";
    try (ApiEndpoint valid =
            ApiEndpoint.answering(
                200, "[{\"geom\":{\"type\":\"Point\",\"coordinates\":[7.6,51.9]}}]");
        ApiEndpoint invalid =
            ApiEndpoint.answering(200, "[{\"geom\":{\"type\":\"Point\",\"coordinates\":\"x\"}}]")) {
      TestRunner agreeing = runner(valid.baseUrl());
      agreeing.setProperty(GetHttpApiCall.RECORD_SCHEMA, schema);
      TestRunner violating = runner(invalid.baseUrl());
      violating.setProperty(GetHttpApiCall.RECORD_SCHEMA, schema);

      agreeing.run();
      violating.run();

      agreeing.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, 1);
      assertRouted(
          violating,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": 1 of 1 records violate the Data structure:"
              + " record 0: /geom/coordinates type");
    }
  }

  @Test
  void onTrigger_withALocalDefinitionInTheRecordSchema_validatesAgainstIt()
      throws InitializationException {
    String schema =
        """
        {"type": "object",
         "properties": {"address": {"$ref": "#/$defs/Address"}},
         "$defs": {"Address": {"type": "object", "required": ["street"],
                               "properties": {"street": {"type": "string"}}}}}""";
    try (ApiEndpoint endpoint =
        ApiEndpoint.answering(200, "[{\"address\":{\"street\":\"Hauptstr.\"}},{\"address\":{}}]")) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(GetHttpApiCall.RECORD_SCHEMA, schema);

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": 1 of 2 records violate the Data structure:"
              + " record 1: /address required street");
    }
  }

  /** The form Model Forge gives a Data structure that imports one with its own $defs. */
  @Test
  void onTrigger_withAnInlinedImportThatHasItsOwnDefinitions_validatesAgainstThem()
      throws InitializationException {
    String schema =
        """
        {"$schema": "https://json-schema.org/draft/2020-12/schema",
         "$id": "urn:core:platform:civitas:element:common:2f1c7a0e-0d1b-4c51-9a37-5d0e1c2b3a41:1.0.0",
         "type": "object",
         "required": ["station"],
         "properties": {
           "station": {
             "$schema": "https://json-schema.org/draft/2020-12/schema",
             "$id": "urn:core:platform:civitas:element:common:7d3e9b2a-6c4f-4e18-8b05-9a1f2c3d4e5f:1.0.0",
             "type": "object",
             "properties": {"position": {"$ref": "#/$defs/Position"}},
             "$defs": {"Position": {"type": "object", "required": ["lat"],
                                    "properties": {"lat": {"type": "number"}}}}}}}""";
    try (ApiEndpoint endpoint =
        ApiEndpoint.answering(
            200, "[{\"station\":{\"position\":{\"lat\":51.9}}},{\"station\":{\"position\":{}}}]")) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(GetHttpApiCall.RECORD_SCHEMA, schema);

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": 1 of 2 records violate the Data structure:"
              + " record 1: /station/position required lat");
    }
  }

  @Test
  void onTrigger_withThePlatformKeywordsInTheRecordSchema_validatesNothingWithThem()
      throws InitializationException {
    String schema =
        """
        {"type": "object",
         "x-core-primaryKey": ["stationid"],
         "properties": {
           "stationid": {"type": "string", "x-core-primaryKey": true},
           "owner": {"type": "string",
                     "x-core-ref": {"type": "urn:core:platform:civitas:element:common:x:1.0.0"}},
           "geom": {"$ref": "https://geojson.org/schema/Point.json", "crs": "EPSG:4326"}}}""";
    try (ApiEndpoint endpoint =
        ApiEndpoint.answering(
            200,
            "[{\"stationid\":\"a1\",\"owner\":\"o\","
                + "\"geom\":{\"type\":\"Point\",\"coordinates\":[7.6,51.9]}}]")) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(GetHttpApiCall.RECORD_SCHEMA, schema);

      runner.run();

      runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, 1);
    }
  }

  @Test
  void start_withARemoteReferenceOtherThanAGeoJsonGeometry_failsAndFetchesNothing()
      throws InitializationException {
    try (ApiEndpoint schemaHost = ApiEndpoint.answering(200, "{\"type\":\"object\"}");
        ApiEndpoint endpoint = ApiEndpoint.answering(200, "[]")) {
      String schema =
          "{\"type\":\"object\",\"properties\":{\"a\":{\"$ref\":\""
              + schemaHost.baseUrl()
              + "/schema.json\"}}}";

      assertStartFails(endpoint, schema);
      assertTrue(schemaHost.requests().isEmpty());
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://geojson.org/schema/Feature.json",
        "https://geojson.org/schema/../schema/Point.json",
        "urn:core:platform:civitas:element:common:x:1.0.0"
      })
  void start_withAReferenceThatIsNotVendored_fails(String reference)
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, "[]")) {
      assertStartFails(
          endpoint,
          "{\"type\":\"object\",\"properties\":{\"a\":{\"$ref\":\"" + reference + "\"}}}");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"type\":", "{\"type\": \"object\"} trailing"})
  void start_withARecordSchemaThatDoesNotCompile_fails(String schema)
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, "[]")) {
      assertStartFails(endpoint, schema);
    }
  }

  @Test
  void validate_withoutARecordSchema_isInvalid() throws InitializationException {
    TestRunner runner = runner("http://localhost:8080");
    runner.removeProperty(GetHttpApiCall.RECORD_SCHEMA);

    runner.assertNotValid();
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 204})
  void onTrigger_whenTheResponseHasNoBody_createsNoFlowFile(int status)
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(status, Map.of(), null)) {
      TestRunner runner = runner(endpoint.baseUrl());

      runner.run();

      runner.assertTransferCount(GetHttpApiCall.SUCCESS, 0);
      runner.assertTransferCount(GetHttpApiCall.FAILURE, 0);
      assertTrue(runner.getLogger().getErrorMessages().isEmpty());
      assertTrue(runner.getLogger().getWarnMessages().isEmpty());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {" ", "\r\n\t "})
  void onTrigger_whenTheBodyHoldsOnlyWhitespace_createsNoFlowFile(String body)
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, body)) {
      TestRunner runner = runner(endpoint.baseUrl());

      runner.run();

      runner.assertTransferCount(GetHttpApiCall.SUCCESS, 0);
      runner.assertTransferCount(GetHttpApiCall.STOP, 0);
      runner.assertTransferCount(GetHttpApiCall.FAILURE, 0);
      assertTrue(runner.getLogger().getErrorMessages().isEmpty());
      assertTrue(runner.getLogger().getWarnMessages().isEmpty());
    }
  }

  @ParameterizedTest
  @CsvSource({
    "400, ''",
    "401, ' (no authentication)'",
    "403, ' (no authentication)'",
    "404, ''",
    "301, ''",
    "302, ''"
  })
  void onTrigger_whenTheStatusNeedsAChangeOfTheConfiguration_routesToStop(int status, String method)
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answering(status, Map.of("Location", "/v1/elsewhere"), "{}")) {
      TestRunner runner = runner(endpoint.baseUrl());

      runner.run();

      String message = "API call \"Stations\": GET /stations failed with status " + status + method;
      assertRouted(runner, GetHttpApiCall.STOP, status, message);
      assertEquals(1, endpoint.requests().size());
      List<LogMessage> errors = runner.getLogger().getErrorMessages();
      assertEquals(1, errors.size());
      assertTrue(errors.get(0).getMsg().contains(message), errors.get(0).getMsg());
      assertTrue(runner.getLogger().getWarnMessages().isEmpty());
    }
  }

  @Test
  void onTrigger_withALineBreakInTheApiCallName_escapesItInLogsAndAttributes()
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(404, "{}")) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(
          GetHttpApiCall.API_CALL,
          "{\"name\":\"Sta\\r\\n\\u2028\\u0085tions\",\"path\":\"/stations\"}");

      runner.run();

      String message =
          "API call \"Sta\\r\\n\\u2028\\u0085tions\": GET /stations failed with status 404";
      assertRouted(runner, GetHttpApiCall.STOP, 404, message);
      for (String text : logged(runner.getLogger())) {
        assertFalse(text.matches("(?s).*[\\r\\n\\u2028\\u0085].*"), text);
      }
    }
  }

  @Test
  void onTrigger_withAnUmlautAndQuotesInTheApiCallName_keepsThemInTheMessage()
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(404, "{}")) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(
          GetHttpApiCall.API_CALL,
          "{\"name\":\"Messwerte \\\"Köln\\\" ✓\",\"path\":\"/stations\"}");

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          404,
          "API call \"Messwerte \"Köln\" ✓\": GET /stations failed with status 404");
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {408, 429, 500, 503})
  void onTrigger_whenTheStatusIsTemporary_routesToFailure(int status)
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(status, "{}")) {
      TestRunner runner = runner(endpoint.baseUrl());

      runner.run();

      String message = "API call \"Stations\": GET /stations failed with status " + status;
      assertRouted(runner, GetHttpApiCall.FAILURE, status, message);
      List<LogMessage> warnings = runner.getLogger().getWarnMessages();
      assertEquals(1, warnings.size());
      assertTrue(warnings.get(0).getMsg().contains(message), warnings.get(0).getMsg());
      assertTrue(runner.getLogger().getErrorMessages().isEmpty());
    }
  }

  @Test
  void onTrigger_whenNoServerListens_routesToFailureWithStatusZero()
      throws InitializationException {
    String baseUrl;
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, "[]")) {
      baseUrl = endpoint.baseUrl();
    }
    TestRunner runner = runner(baseUrl);

    runner.run();

    assertRouted(
        runner,
        GetHttpApiCall.FAILURE,
        0,
        "API call \"Stations\": GET /stations failed: ConnectException");
    assertEquals(1, runner.getLogger().getWarnMessages().size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "[{\"id\":1"})
  void onTrigger_whenTheBodyBreaksOff_routesToFailureAndNoPartialFlowFile(String part)
      throws IOException, InitializationException {
    try (BreakingEndpoint endpoint = BreakingEndpoint.sending(part)) {
      TestRunner runner = runner(endpoint.baseUrl());

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.FAILURE,
          0,
          "API call \"Stations\": GET /stations failed: IOException");
      assertEquals(1, runner.getLogger().getWarnMessages().size());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "[{\"id\":1"})
  @Timeout(10)
  void onTrigger_whenTheBodyStalls_routesToFailureAfterTheBodyReadTimeout(String part)
      throws IOException, InitializationException {
    try (BreakingEndpoint endpoint = BreakingEndpoint.stalling(part)) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(GetHttpApiCall.BODY_READ_TIMEOUT, "200 millis");

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.FAILURE,
          0,
          "API call \"Stations\": GET /stations failed: HttpTimeoutException");
      assertEquals(1, runner.getLogger().getWarnMessages().size());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 404, 503, 0})
  void onTrigger_withACredentialAndAQuery_keepsThemOutOfLogsAndAttributes(int status)
      throws InitializationException {
    String password = "pw-5b0e7c41d9";
    String queryValue = "qv-93f2a6d8e1";
    String username = "user-1c84f0aa";
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringOverTls(tls.server(), status == 0 ? 200 : status, "[]")) {
      String baseUrl = endpoint.baseUrl();
      if (status == 0) {
        endpoint.close();
      }
      TestRunner runner = runner(baseUrl, tls);
      runner.setProperty(GetHttpApiCall.AUTHENTICATION_TYPE, "BASIC");
      runner.setProperty(GetHttpApiCall.BASIC_AUTH_USERNAME, username);
      runner.setProperty(GetHttpApiCall.BASIC_AUTH_PASSWORD, password);
      runner.setProperty(
          GetHttpApiCall.API_CALL,
          "{\"name\":\"Stations\",\"path\":\"/stations\",\"queryParameters\":"
              + "[{\"name\":\"key\",\"value\":\""
              + queryValue
              + "\"}]}");

      runner.run();

      for (String text : visible(runner)) {
        assertFalse(text.contains(password), text);
        assertFalse(text.contains(queryValue), text);
        assertFalse(text.contains(username), text);
      }
    }
  }

  @Test
  void onTrigger_withPagination_sendsThePageAndThePageSizeAfterTheQueryParametersFromTheStartPage()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(Response.json(FULL_PAGE), Response.json(SHORT_PAGE))) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(
          GetHttpApiCall.API_CALL,
          """
          {"name": "Stations", "path": "/stations",
           "queryParameters": [{"name": "status", "value": "active"}],
           "pagination": {"type": "PAGE_NUMBER", "pageParameter": "page",
                          "pageSizeParameter": "size", "pageSize": 2, "startPage": 5}}""");

      runner.run();

      assertEquals(
          List.of("status=active&page=5&size=2", "status=active&page=6&size=2"),
          rawQueries(endpoint));
      assertPages(runner, "5", "6");
    }
  }

  @Test
  void onTrigger_withPaginationWithoutAStartPage_requestsPage0First()
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, SHORT_PAGE)) {
      TestRunner runner = paginatedRunner(endpoint, "");

      runner.run();

      assertEquals(List.of("page=0&size=2"), rawQueries(endpoint));
    }
  }

  @Test
  void onTrigger_withAnEndPage_readsTheEndPageAndStopsAfterIt() throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(fullPage(0)), Response.json(fullPage(1)), Response.json(fullPage(2)))) {
      TestRunner runner = paginatedRunner(endpoint, ", \"startPage\": 0, \"endPage\": 2");

      runner.run();

      assertEquals(3, endpoint.requests().size());
      assertPages(runner, "0", "1", "2");
    }
  }

  @Test
  void onTrigger_whenAPageHasFewerRecordsThanThePageSize_stopsAfterIt()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(Response.json(FULL_PAGE), Response.json(SHORT_PAGE))) {
      TestRunner runner = paginatedRunner(endpoint, "");

      runner.run();

      assertEquals(2, endpoint.requests().size());
      assertPages(runner, "0", "1");
      runner
          .getFlowFilesForRelationship(GetHttpApiCall.SUCCESS)
          .get(1)
          .assertContentEquals(SHORT_PAGE);
    }
  }

  static List<Response> pagesWithoutRecords() {
    return List.of(
        Response.json("[]"), new Response(200, Map.of(), null), new Response(204, Map.of(), null));
  }

  @ParameterizedTest
  @MethodSource("pagesWithoutRecords")
  void onTrigger_whenAPageHasNoRecords_stopsThereWithoutAFlowFileForIt(Response empty)
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answeringInTurn(Response.json(FULL_PAGE), empty)) {
      TestRunner runner = paginatedRunner(endpoint, "");

      runner.run();

      assertEquals(2, endpoint.requests().size());
      assertPages(runner, "0");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"stationid\":\"a1\"}", "{\"lat\":1}"})
  void onTrigger_whenAPageIsAJsonObject_routesTheRunToStop(String object)
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(Response.json(FULL_PAGE), Response.json(object))) {
      TestRunner runner = paginatedRunner(endpoint, "");

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": page 1 is a JSON object, not an array");
      assertEquals(1, runner.getLogger().getErrorMessages().size());
    }
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '\'',
      value = {"'\"val-1f3e\"' | string", "42             | number"})
  void onTrigger_whenAPageIsAJsonScalar_routesTheRunToStop(String body, String kind)
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(Response.json(FULL_PAGE), Response.json(body))) {
      TestRunner runner = paginatedRunner(endpoint, "");

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": page 1 is a JSON " + kind + ", not an array");
    }
  }

  @Test
  void onTrigger_whenAPageIsNotJson_routesTheRunToStop() throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(FULL_PAGE), Response.json("<html>val-1f3e</html>"))) {
      TestRunner runner = paginatedRunner(endpoint, "");

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": page 1: the response is not JSON");
    }
  }

  @Test
  void onTrigger_whenPage2NeedsAChangeOfTheConfiguration_routesTheRunToStopWithoutPage1()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(Response.json(FULL_PAGE), Response.status(403))) {
      TestRunner runner = paginatedRunner(endpoint, ", \"startPage\": 1");

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          403,
          "API call \"Stations\": GET /stations page 2 failed with status 403 (no authentication)");
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 404})
  void onTrigger_whenAPageAfterTheFirstIsPastTheLastPage_routesTheEarlierPagesToSuccess(int status)
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(fullPage(0)), Response.json(fullPage(1)), Response.status(status))) {
      TestRunner runner = paginatedRunner(endpoint, "");

      runner.run();

      assertEquals(3, endpoint.requests().size());
      assertPages(runner, "0", "1");
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 404})
  void onTrigger_whenTheFirstPageAnswers400Or404_routesTheRunToStop(int status)
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(status, "{}")) {
      TestRunner runner = paginatedRunner(endpoint, "");

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          status,
          "API call \"Stations\": GET /stations page 0 failed with status " + status);
    }
  }

  @Test
  void onTrigger_whenPage2IsTemporarilyUnavailable_routesTheRunToFailureWithoutPage1()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(Response.json(FULL_PAGE), Response.status(503))) {
      TestRunner runner = paginatedRunner(endpoint, ", \"startPage\": 1");

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.FAILURE,
          503,
          "API call \"Stations\": GET /stations page 2 failed with status 503");
      assertEquals(1, runner.getLogger().getWarnMessages().size());
    }
  }

  @Test
  void onTrigger_whenARecordOfPage2Violates_routesTheRunToStopWithoutPage1()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(FULL_PAGE), Response.json("[{\"stationid\":\"a3\"},{\"lat\":1}]"))) {
      TestRunner runner = paginatedRunner(endpoint, ", \"startPage\": 1");

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": page 2: 1 of 2 records violate the Data structure:"
              + " record 1: required stationid");
    }
  }

  @Test
  void onTrigger_afterARunThatFailed_startsAgainAtTheStartPage() throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(FULL_PAGE),
            Response.status(503),
            Response.json(FULL_PAGE),
            Response.json(SHORT_PAGE))) {
      TestRunner runner = paginatedRunner(endpoint, ", \"startPage\": 1");

      runner.run(2);

      assertEquals(
          List.of("page=1&size=2", "page=2&size=2", "page=1&size=2", "page=2&size=2"),
          rawQueries(endpoint));
      runner.assertTransferCount(GetHttpApiCall.FAILURE, 1);
      runner.assertTransferCount(GetHttpApiCall.SUCCESS, 2);
    }
  }

  @Test
  void onTrigger_whenNiFiStopsTheProcessorDuringARun_sendsNoFurtherPageAndRoutesTheRunToFailure()
      throws InitializationException {
    AtomicReference<TestRunner> runner = new AtomicReference<>();
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            index -> ((GetHttpApiCall) runner.get().getProcessor()).updateScheduledFalse(),
            Response.json(FULL_PAGE),
            Response.json(FULL_PAGE))) {
      runner.set(paginatedRunner(endpoint, ""));

      runner.get().run();

      assertEquals(1, endpoint.requests().size());
      assertRouted(
          runner.get(),
          GetHttpApiCall.FAILURE,
          0,
          "API call \"Stations\": GET /stations page 1 not sent, because the processor stopped");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", ", \"endPage\": 1"})
  void onTrigger_whenAPageRepeatsThePageBefore_routesTheRunToStopAtTheSecondPage(String endPage)
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, FULL_PAGE)) {
      TestRunner runner = paginatedRunner(endpoint, endPage);

      runner.run();

      assertRouted(
          runner, GetHttpApiCall.STOP, 200, "API call \"Stations\": page 1 repeats page 0");
      assertEquals(2, endpoint.requests().size());
    }
  }

  @Test
  void onTrigger_whenAPageRepeatsAnEarlierPage_routesTheRunToStop() throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(fullPage(0)), Response.json(fullPage(1)), Response.json(fullPage(0)))) {
      TestRunner runner = paginatedRunner(endpoint, "");

      runner.run();

      assertRouted(
          runner, GetHttpApiCall.STOP, 200, "API call \"Stations\": page 2 repeats page 0");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", ", \"endPage\": 10"})
  void onTrigger_whenMaxPagesPagesMeetNoStopCondition_routesTheRunToStop(String endPage)
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(fullPage(0)), Response.json(fullPage(1)), Response.json(fullPage(2)))) {
      TestRunner runner = paginatedRunner(endpoint, endPage);
      runner.setProperty(GetHttpApiCall.MAX_PAGES, "3");

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": no stop condition after 3 pages (Max Pages)");
      assertEquals(3, endpoint.requests().size());
    }
  }

  @Test
  void onTrigger_whenThePageAtMaxPagesMeetsAStopCondition_routesAllPagesToSuccess()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(Response.json(FULL_PAGE), Response.json(SHORT_PAGE))) {
      TestRunner runner = paginatedRunner(endpoint, "");
      runner.setProperty(GetHttpApiCall.MAX_PAGES, "2");

      runner.run();

      assertPages(runner, "0", "1");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "x"})
  void validate_withMaxPagesThatIsNotAPositiveInteger_isInvalid(String maxPages)
      throws InitializationException {
    TestRunner runner = runner("http://localhost:8080");
    runner.setProperty(GetHttpApiCall.MAX_PAGES, maxPages);

    runner.assertNotValid();
  }

  @Test
  void onTrigger_whenAPageHasMoreRecordsThanThePageSize_readsTheNextPage()
      throws InitializationException {
    String overfull = "[{\"stationid\":\"a1\"},{\"stationid\":\"a2\"},{\"stationid\":\"a3\"}]";
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(Response.json(overfull), Response.json(SHORT_PAGE))) {
      TestRunner runner = paginatedRunner(endpoint, "");

      runner.run();

      assertPages(runner, "0", "1");
    }
  }

  @Test
  void onTrigger_withTheLargestStartPage_countsPastTheIntRange() throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(Response.json(FULL_PAGE), Response.json(SHORT_PAGE))) {
      TestRunner runner = paginatedRunner(endpoint, ", \"startPage\": 2147483647");

      runner.run();

      assertEquals(
          List.of("page=2147483647&size=2", "page=2147483648&size=2"), rawQueries(endpoint));
    }
  }

  @Test
  void onTrigger_withoutPagination_setsNoPageAttribute() throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, FULL_PAGE)) {
      TestRunner runner = runner(endpoint.baseUrl());

      runner.run();

      runner
          .getFlowFilesForRelationship(GetHttpApiCall.SUCCESS)
          .get(0)
          .assertAttributeNotExists(GetHttpApiCall.PAGE);
    }
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '\'',
      value = {
        "$.links.next | {\"links\":{\"next\":\"?page=2\"}} | false",
        "$.links.next | {\"links\":{\"next\":\"?page=2\"}} | true",
        "$.links.next | {\"links\":{\"next\":{\"href\":\"?page=2\"}}} | false",
        "$.links.next | {\"links\":{\"next\":{\"href\":\"?page=2\"}}} | true",
        "$._links.next.href | {\"_links\":{\"next\":{\"href\":\"?page=2\"}}} | false",
        "$._links.next.href | {\"_links\":{\"next\":{\"href\":\"?page=2\"}}} | true",
        "$['@odata.nextLink'] | {\"@odata.nextLink\":\"?page=2\"} | false",
        "$['@odata.nextLink'] | {\"@odata.nextLink\":\"?page=2\"} | true",
        "$['@iot.nextLink'] | {\"@iot.nextLink\":\"?page=2\"} | false",
        "$['@iot.nextLink'] | {\"@iot.nextLink\":\"?page=2\"} | true",
      })
  void onTrigger_withEachNextLinkFormat_readsTheLastResponse(
      String path, String first, boolean absolute) throws Exception {
    AtomicReference<String> apiUrl = new AtomicReference<>();
    try (ApiEndpoint endpoint =
        ApiEndpoint.startHttp(
            index ->
                Response.json(
                    index == 0
                        ? first.replace(
                            "?page=2", absolute ? apiUrl.get() + "/stations?page=2" : "?page=2")
                        : "{}"))) {
      apiUrl.set(endpoint.baseUrl());
      TestRunner runner = nextLinkRunner(endpoint, path, "");
      runner.run();
      runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, 2);
      assertEquals("page=2", rawQueries(endpoint).get(1));
      assertEquals("/v1/stations", endpoint.requests().get(1).rawPath());
      assertEquals(
          "request 2",
          runner
              .getFlowFilesForRelationship(GetHttpApiCall.SUCCESS)
              .get(1)
              .getAttribute(GetHttpApiCall.PAGE));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"absent", "null", "\"\"", "{\"href\":\"\"}"})
  void onTrigger_withoutANextLink_deliversTheLastResponse(String value) throws Exception {
    String last = "absent".equals(value) ? "{}" : "{\"next\":" + value + "}";
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(Response.json("{\"next\":\"?page=2\"}"), Response.json(last))) {
      TestRunner runner = nextLinkRunner(endpoint, "$.next", "");
      runner.run();
      runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, 2);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "true",
        "42",
        "[]",
        "{}",
        "{\"href\":null}",
        "{\"href\":42}",
        "\"http://bad link/token-4b90\"",
        "\"https://another.example/token-4b90\"",
        "\"http://localhost:1/token-4b90\""
      })
  void onTrigger_withAnInvalidNextLink_stopsWithoutLeakingTheLink(String value) throws Exception {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, "{\"next\":" + value + "}")) {
      TestRunner runner = nextLinkRunner(endpoint, "$.next", "");
      runner.run();
      runner.assertAllFlowFilesTransferred(GetHttpApiCall.STOP, 1);
      assertEquals(1, endpoint.requests().size());
      for (String text : visible(runner)) {
        assertFalse(text.contains("token-4b90"), text);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"[]", "1", "true", "not JSON"})
  void onTrigger_withANextLinkResponseThatIsNotAnObject_discardsEarlierPages(String last)
      throws Exception {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json("{\"next\":\"?token=token-4b90\"}"), Response.json(last))) {
      TestRunner runner = nextLinkRunner(endpoint, "$.next", "");
      runner.run();
      runner.assertAllFlowFilesTransferred(GetHttpApiCall.STOP, 1);
      for (String text : visible(runner)) {
        assertFalse(text.contains("token-4b90"), text);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 404, 503})
  void onTrigger_whenAFollowedLinkFails_discardsEarlierPages(int status) throws Exception {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json("{\"next\":\"?token=token-4b90\"}"), Response.status(status))) {
      TestRunner runner = nextLinkRunner(endpoint, "$.next", "");
      runner.run();
      runner.assertAllFlowFilesTransferred(
          status == 503 ? GetHttpApiCall.FAILURE : GetHttpApiCall.STOP, 1);
      for (String text : visible(runner)) {
        assertFalse(text.contains("token-4b90"), text);
      }
    }
  }

  @Test
  void onTrigger_withARepeatedNextLink_stopsBeforeSendingItAgain() throws Exception {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json("{\"next\":\"?token=token-4b90\",\"id\":1}"),
            Response.json("{\"next\":\"?token=token-4b90\",\"id\":2}"))) {
      TestRunner runner = nextLinkRunner(endpoint, "$.next", "");
      runner.run();
      runner.assertAllFlowFilesTransferred(GetHttpApiCall.STOP, 1);
      assertEquals(2, endpoint.requests().size());
      for (String text : visible(runner)) {
        assertFalse(text.contains("token-4b90"), text);
      }
    }
  }

  @Test
  void onTrigger_withMaxLinks_deliversAtMostMaxLinksPlusOneResponses() throws Exception {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json("{\"next\":\"?page=2\"}"), Response.json("{\"next\":\"?page=3\"}"))) {
      TestRunner runner = nextLinkRunner(endpoint, "$.next", ",\"maxLinks\":1");
      runner.run();
      runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, 2);
      assertEquals(2, endpoint.requests().size());
    }
  }

  @Test
  void onTrigger_withNextLinksAndMaxPages_stopsWithoutEarlierPages() throws Exception {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, "{\"next\":\"?page=2\"}")) {
      TestRunner runner = nextLinkRunner(endpoint, "$.next", ",\"maxLinks\":10");
      runner.setProperty(GetHttpApiCall.MAX_PAGES, "1");
      runner.run();
      runner.assertAllFlowFilesTransferred(GetHttpApiCall.STOP, 1);
    }
  }

  @Test
  void onTrigger_withNextLinksAndNoBody_endsWithEarlierPages() throws Exception {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json("{\"next\":\"?page=2\"}"), Response.status(204))) {
      TestRunner runner = nextLinkRunner(endpoint, "$.next", "");
      runner.run();
      runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, 1);
    }
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        " | pagination.nextLinkPath",
        "\"nextLinkPath\":\"$..secret\" | pagination.nextLinkPath",
        "\"nextLinkPath\":\"$.next\",\"maxLinks\":0 | pagination.maxLinks",
        "\"nextLinkPath\":\"$.next\",\"maxLinks\":\"secret\" | pagination.maxLinks"
      })
  void validate_withAnInvalidNextLinkPagination_namesTheField(String fields, String key)
      throws Exception {
    TestRunner runner = runner("http://localhost:8080");
    runner.setProperty(
        GetHttpApiCall.API_CALL,
        "{\"name\":\"Stations\",\"path\":\"/stations\",\"pagination\":{\"type\":\"NEXT_LINK\""
            + (fields == null ? "" : "," + fields)
            + "}}");
    runner.assertNotValid();
    assertTrue(invalid(runner).stream().anyMatch(result -> result.getExplanation().contains(key)));
  }

  private static TestRunner nextLinkRunner(ApiEndpoint endpoint, String path, String fields)
      throws Exception {
    TestRunner runner = runner(endpoint.baseUrl());
    runner.setProperty(GetHttpApiCall.RECORD_SCHEMA, "{\"type\":\"object\"}");
    runner.setProperty(
        GetHttpApiCall.API_CALL,
        "{\"name\":\"Stations\",\"path\":\"/stations\",\"pagination\":{\"type\":\"NEXT_LINK\",\"nextLinkPath\":\""
            + path
            + "\""
            + fields
            + "}}");
    return runner;
  }

  @Test
  void onTrigger_withCursorInTheQuery_readsEachPageUntilAResponseWithoutACursor()
      throws InitializationException {
    String first = cursorPage("a1", "\"abc123\"");
    String second = cursorPage("a2", "\"def456\"");
    String last = cursorPage("a3", null);
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(first), Response.json(second), Response.json(last))) {
      TestRunner runner =
          cursorRunner(
              endpoint,
              "\"cursorParameter\": \"cursor\", \"cursorIn\": \"QUERY\","
                  + " \"limitParameter\": \"limit\", \"limit\": 20");

      runner.run();

      assertEquals(
          List.of("limit=20", "limit=20&cursor=abc123", "limit=20&cursor=def456"),
          rawQueries(endpoint));
      assertPages(runner, "1", "2", "3");
      List<MockFlowFile> pages = runner.getFlowFilesForRelationship(GetHttpApiCall.SUCCESS);
      pages.get(0).assertContentEquals(first);
      pages.get(2).assertContentEquals(last);
    }
  }

  @Test
  void onTrigger_withCursorInAHeader_sendsTheCursorAsAHeaderAndNotInTheQuery()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(cursorPage("a1", "\"abc123\"")),
            Response.json(cursorPage("a2", "\"def456\"")),
            Response.json(cursorPage("a3", null)))) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(GetHttpApiCall.RECORD_SCHEMA, ONE_RECORD_SCHEMA);
      runner.setProperty(
          GetHttpApiCall.API_CALL,
          """
          {"name": "Stations", "path": "/stations",
           "queryParameters": [{"name": "status", "value": "active"}],
           "headers": [{"name": "Accept", "value": "application/json"}],
           "pagination": {"type": "CURSOR", "cursorPath": "$.next_cursor",
                          "cursorParameter": "X-Cursor", "cursorIn": "HEADER",
                          "limitParameter": "limit", "limit": 2}}""");

      runner.run();

      assertEquals(
          List.of("status=active&limit=2", "status=active&limit=2", "status=active&limit=2"),
          rawQueries(endpoint));
      List<String> cursors =
          endpoint.requests().stream()
              .map(request -> String.valueOf(request.headers().getFirst("X-Cursor")))
              .toList();
      assertEquals(List.of("null", "abc123", "def456"), cursors);
      assertTrue(
          endpoint.requests().stream()
              .allMatch(
                  request -> "application/json".equals(request.headers().getFirst("Accept"))));
      assertPages(runner, "1", "2", "3");
    }
  }

  @Test
  void onTrigger_withCursorPaginationWithoutALimit_sendsNoLimitParameter()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(cursorPage("a1", "\"abc123\"")), Response.json(cursorPage("a2", null)))) {
      TestRunner runner = cursorRunner(endpoint, CURSOR_IN_QUERY);

      runner.run();

      assertEquals(Arrays.asList(null, "cursor=abc123"), rawQueries(endpoint));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"absent", "null", "\"\""})
  void onTrigger_whenTheCursorIsAbsentNullOrEmpty_routesThatResponseToSuccessAndEndsTheRun(
      String cursor) throws InitializationException {
    String last = cursorPage("a2", "absent".equals(cursor) ? null : cursor);
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(cursorPage("a1", "\"abc123\"")), Response.json(last))) {
      TestRunner runner = cursorRunner(endpoint, CURSOR_IN_QUERY);

      runner.run();

      assertEquals(2, endpoint.requests().size());
      assertPages(runner, "1", "2");
      runner.getFlowFilesForRelationship(GetHttpApiCall.SUCCESS).get(1).assertContentEquals(last);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"12345678901234567890", "1730000000.25", "1.50", "1e2", "1E-7", "-0"})
  void onTrigger_withANumberCursor_sendsItsJsonText(String number) throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(cursorPage("a1", number)), Response.json(cursorPage("a2", null)))) {
      TestRunner runner = cursorRunner(endpoint, CURSOR_IN_QUERY);

      runner.run();

      assertEquals("cursor=" + number, rawQueries(endpoint).get(1));
    }
  }

  @Test
  void onTrigger_withReservedCharactersInACursor_percentEncodesThem()
      throws InitializationException {
    String cursor = "a b&c=d+e/f?é";
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(cursorPage("a1", "\"" + cursor + "\"")),
            Response.json(cursorPage("a2", null)))) {
      TestRunner runner = cursorRunner(endpoint, CURSOR_IN_QUERY);

      runner.run();

      String query = rawQueries(endpoint).get(1);
      assertEquals("cursor=a%20b%26c%3Dd%2Be%2Ff%3F%C3%A9", query);
      assertEquals(
          cursor, URLDecoder.decode(query.substring("cursor=".length()), StandardCharsets.UTF_8));
    }
  }

  @Test
  void onTrigger_whenACursorRepeats_routesTheRunToStopWithoutItsPages()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(cursorPage("a1", "\"c1\"")),
            Response.json(cursorPage("a2", "\"c2\"")),
            Response.json(cursorPage("a3", "\"c1\"")))) {
      TestRunner runner = cursorRunner(endpoint, CURSOR_IN_QUERY);

      runner.run();

      assertEquals(3, endpoint.requests().size());
      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": request 4 would send the same request as request 2");
    }
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '\'',
      value = {"'{\"c\": \"v-61b0\"}' | object", "'[\"v-61b0\"]' | array", "true | boolean"})
  void onTrigger_whenTheCursorIsNotAStringOrANumber_routesTheRunToStop(String cursor, String type)
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, cursorPage("a1", cursor))) {
      TestRunner runner = cursorRunner(endpoint, CURSOR_IN_QUERY);

      runner.run();

      assertEquals(1, endpoint.requests().size());
      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": request 1: the value at $.next_cursor is a JSON "
              + type
              + ", not a string or a number");
    }
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '\'',
      value = {
        "'[{\"data\": [], \"next_cursor\": \"c\"}]' | request 2 is a JSON array, not an object",
        "42                                          | request 2 is a JSON number, not an object",
        "'<html>val-1f3e</html>'                     | request 2: the response is not JSON"
      })
  void onTrigger_whenACursorResponseIsNotAJsonObject_routesTheRunToStop(String body, String cause)
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(cursorPage("a1", "\"abc123\"")), Response.json(body))) {
      TestRunner runner = cursorRunner(endpoint, CURSOR_IN_QUERY);

      runner.run();

      assertRouted(runner, GetHttpApiCall.STOP, 200, "API call \"Stations\": " + cause);
    }
  }

  @Test
  void onTrigger_whenAHeaderCursorHoldsALineBreak_routesTheRunToStopWithoutTheCursor()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answering(200, cursorPage("a1", "\"cur-9d2e\\r\\nX-Injected: cur-9d2e\""))) {
      TestRunner runner = cursorRunner(endpoint, CURSOR_IN_HEADER);

      runner.run();

      assertEquals(1, endpoint.requests().size());
      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": request 1: the value at $.next_cursor holds a character that a"
              + " header value must not hold");
      assertFalse(logged(runner.getLogger()).toString().contains("cur-9d2e"));
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 404})
  void onTrigger_whenACursorRequestAfterTheFirstAnswers400Or404_routesTheRunToStop(int status)
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(cursorPage("a1", "\"abc123\"")), Response.status(status))) {
      TestRunner runner = cursorRunner(endpoint, CURSOR_IN_QUERY);

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          status,
          "API call \"Stations\": GET /stations request 2 failed with status " + status);
    }
  }

  static List<Response> responsesWithoutABody() {
    return List.of(new Response(200, Map.of(), null), new Response(204, Map.of(), null));
  }

  @ParameterizedTest
  @MethodSource("responsesWithoutABody")
  void onTrigger_whenACursorResponseHasNoBody_endsTheRunWithTheEarlierPages(Response empty)
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(Response.json(cursorPage("a1", "\"abc123\"")), empty)) {
      TestRunner runner = cursorRunner(endpoint, CURSOR_IN_QUERY);

      runner.run();

      assertEquals(2, endpoint.requests().size());
      assertPages(runner, "1");
    }
  }

  @Test
  void onTrigger_whenACursorRequestIsTemporarilyUnavailable_routesTheRunToFailureWithoutItsPages()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(cursorPage("a1", "\"abc123\"")), Response.status(503))) {
      TestRunner runner = cursorRunner(endpoint, CURSOR_IN_QUERY);

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.FAILURE,
          503,
          "API call \"Stations\": GET /stations request 2 failed with status 503");
    }
  }

  @Test
  void onTrigger_whenACursorResponseViolatesTheRecordSchema_routesTheRunToStop()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(cursorPage("a1", "\"abc123\"")),
            Response.json("{\"next_cursor\": \"def456\"}"))) {
      TestRunner runner = cursorRunner(endpoint, CURSOR_IN_QUERY);

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": request 2: 1 of 1 records violate the Data structure:"
              + " record 0: required data");
    }
  }

  static Stream<Arguments> runsWithAUniqueCursor() {
    String cursor = "\"cur-7c1d\"";
    return Stream.of(
        Arguments.of(
            CURSOR_IN_QUERY,
            List.of(
                Response.json(cursorPage("a1", cursor)), Response.json(cursorPage("a2", null)))),
        Arguments.of(
            CURSOR_IN_HEADER,
            List.of(Response.json(cursorPage("a1", cursor)), Response.status(404))),
        Arguments.of(
            CURSOR_IN_QUERY,
            List.of(
                Response.json(cursorPage("a1", cursor)),
                Response.json(cursorPage("a2", "\"x\"")),
                Response.json(cursorPage("a3", cursor)))),
        Arguments.of(
            CURSOR_IN_HEADER,
            List.of(
                Response.json(cursorPage("a1", cursor)),
                Response.json(cursorPage("a2", "\"cur-7c1d\\n\"")))));
  }

  @ParameterizedTest
  @MethodSource("runsWithAUniqueCursor")
  void onTrigger_withCursorPagination_keepsTheCursorOutOfLogsAttributesAndCauses(
      String cursorFields, List<Response> responses) throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answeringInTurn(responses.toArray(Response[]::new))) {
      TestRunner runner = cursorRunner(endpoint, cursorFields);

      runner.run();

      for (String text : visible(runner)) {
        assertFalse(text.contains("cur-7c1d"), text);
      }
    }
  }

  @Test
  void
      onTrigger_withLimitOffset_sendsTheLimitAndTheOffsetAfterTheQueryParametersFromTheStartOffset()
          throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(fullPage(0)), Response.json(fullPage(1)), Response.json(SHORT_PAGE))) {
      TestRunner runner = runner(endpoint.baseUrl());
      runner.setProperty(
          GetHttpApiCall.API_CALL,
          """
          {"name": "Stations", "path": "/stations",
           "queryParameters": [{"name": "status", "value": "active"}],
           "pagination": {"type": "LIMIT_OFFSET", "limitParameter": "limit", "limit": 2,
                          "offsetParameter": "offset", "offset": 80}}""");

      runner.run();

      assertEquals(
          List.of(
              "status=active&limit=2&offset=80",
              "status=active&limit=2&offset=82",
              "status=active&limit=2&offset=84"),
          rawQueries(endpoint));
      assertPages(runner, "80", "82", "84");
      runner
          .getFlowFilesForRelationship(GetHttpApiCall.SUCCESS)
          .get(2)
          .assertContentEquals(SHORT_PAGE);
    }
  }

  @Test
  void onTrigger_withLimitOffsetWithoutALimit_advancesByTheRecordCountAndEndsAtAnEmptyResponse()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(fullPage(0)), Response.json(SHORT_PAGE), Response.json("[]"))) {
      TestRunner runner = limitOffsetRunner(endpoint, "");

      runner.run();

      assertEquals(List.of("offset=0", "offset=2", "offset=3"), rawQueries(endpoint));
      assertPages(runner, "0", "2");
    }
  }

  static Stream<Arguments> countsInAResponseObject() {
    return Stream.of(
        Arguments.of("$.meta.count", countPage("1", "a1"), countPage("1", "a2"), countPage("0")),
        Arguments.of("$.data", countPage(null, "a1"), countPage(null, "a2"), countPage(null)));
  }

  @ParameterizedTest
  @MethodSource("countsInAResponseObject")
  void onTrigger_withACountPathWithoutALimit_advancesByTheCountAndEndsAtACountOf0(
      String countPath, String first, String second, String empty) throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(first), Response.json(second), Response.json(empty))) {
      TestRunner runner = limitOffsetRunner(endpoint, ", \"countPath\": \"" + countPath + "\"");
      runner.setProperty(GetHttpApiCall.RECORD_SCHEMA, ONE_RECORD_SCHEMA);

      runner.run();

      assertEquals(List.of("offset=0", "offset=1", "offset=2"), rawQueries(endpoint));
      assertPages(runner, "0", "1");
      runner.getFlowFilesForRelationship(GetHttpApiCall.SUCCESS).get(0).assertContentEquals(first);
    }
  }

  @Test
  void onTrigger_withACountPathAndALimit_endsAtACountBelowTheLimit()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(countPage("2", "a1", "a2")), Response.json(countPage("1", "a3")))) {
      TestRunner runner = countPathRunner(endpoint, LIMIT_2);

      runner.run();

      assertEquals(List.of("limit=2&offset=0", "limit=2&offset=2"), rawQueries(endpoint));
      assertPages(runner, "0", "2");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"-7", "1.5", "\"v-61b0\"", "{\"n\": 1}", "null"})
  void onTrigger_whenTheValueAtTheCountPathIsNotACount_routesTheRunToStopWithoutTheValue(
      String count) throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(countPage("2", "a1", "a2")), Response.json(countPage(count, "a3")))) {
      TestRunner runner = countPathRunner(endpoint, LIMIT_2);

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": offset 2: the value at $.meta.count is not a record count");
    }
  }

  @Test
  void onTrigger_whenTheCountPathIsAbsentFromAResponse_routesTheRunToStop()
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, countPage(null, "a1"))) {
      TestRunner runner = countPathRunner(endpoint, LIMIT_2);

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": offset 0: the value at $.meta.count is not a record count");
    }
  }

  @Test
  void onTrigger_whenALimitOffsetResponseWithoutACountPathIsAJsonObject_routesTheRunToStop()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(FULL_PAGE), Response.json("{\"stationid\": \"a3\"}"))) {
      TestRunner runner = limitOffsetRunner(endpoint, LIMIT_2);

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": offset 2 is a JSON object, not an array");
    }
  }

  @Test
  void onTrigger_whenALimitOffsetResponseWithACountPathIsAJsonArray_routesTheRunToStop()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(countPage("2", "a1", "a2")), Response.json(SHORT_PAGE))) {
      TestRunner runner = countPathRunner(endpoint, LIMIT_2);

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": offset 2 is a JSON array, not an object");
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 404})
  void onTrigger_whenALimitOffsetRequestAfterTheFirstAnswers400Or404_routesTheEarlierPagesToSuccess(
      int status) throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(fullPage(0)), Response.json(fullPage(1)), Response.status(status))) {
      TestRunner runner = limitOffsetRunner(endpoint, LIMIT_2);

      runner.run();

      assertEquals(3, endpoint.requests().size());
      assertPages(runner, "0", "2");
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 404})
  void onTrigger_whenTheFirstLimitOffsetRequestAnswers400Or404_routesTheRunToStop(int status)
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(status, "{}")) {
      TestRunner runner = limitOffsetRunner(endpoint, LIMIT_2 + ", \"offset\": 80");

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          status,
          "API call \"Stations\": GET /stations offset 80 failed with status " + status);
    }
  }

  @ParameterizedTest
  @MethodSource("responsesWithoutABody")
  void onTrigger_whenALimitOffsetResponseWithACountPathHasNoBody_endsTheRunWithTheEarlierPages(
      Response empty) throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(Response.json(countPage("2", "a1", "a2")), empty)) {
      TestRunner runner = countPathRunner(endpoint, LIMIT_2);

      runner.run();

      assertEquals(2, endpoint.requests().size());
      assertPages(runner, "0");
    }
  }

  @Test
  void onTrigger_whenAnApiIgnoresTheOffset_routesTheRunToStopAtTheSecondRequest()
      throws InitializationException {
    try (ApiEndpoint endpoint = ApiEndpoint.answering(200, FULL_PAGE)) {
      TestRunner runner = limitOffsetRunner(endpoint, LIMIT_2);

      runner.run();

      assertEquals(2, endpoint.requests().size());
      assertRouted(
          runner, GetHttpApiCall.STOP, 200, "API call \"Stations\": offset 2 repeats offset 0");
    }
  }

  @Test
  void onTrigger_whenMaxPagesLimitOffsetRequestsMeetNoStopCondition_routesTheRunToStop()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(fullPage(0)), Response.json(fullPage(1)), Response.json(fullPage(2)))) {
      TestRunner runner = limitOffsetRunner(endpoint, LIMIT_2);
      runner.setProperty(GetHttpApiCall.MAX_PAGES, "3");

      runner.run();

      assertEquals(3, endpoint.requests().size());
      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          200,
          "API call \"Stations\": no stop condition after 3 pages (Max Pages)");
    }
  }

  @Test
  void
      onTrigger_whenALimitOffsetRequestIsTemporarilyUnavailable_routesTheRunToFailureWithoutItsPages()
          throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(Response.json(FULL_PAGE), Response.status(503))) {
      TestRunner runner = limitOffsetRunner(endpoint, LIMIT_2);

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.FAILURE,
          503,
          "API call \"Stations\": GET /stations offset 2 failed with status 503");
    }
  }

  @Test
  void onTrigger_afterALimitOffsetRunThatFailed_startsAgainAtTheStartOffset()
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(
            Response.json(FULL_PAGE),
            Response.status(503),
            Response.json(FULL_PAGE),
            Response.json(SHORT_PAGE))) {
      TestRunner runner = limitOffsetRunner(endpoint, LIMIT_2 + ", \"offset\": 80");

      runner.run(2);

      assertEquals(
          List.of(
              "limit=2&offset=80", "limit=2&offset=82", "limit=2&offset=80", "limit=2&offset=82"),
          rawQueries(endpoint));
      runner.assertTransferCount(GetHttpApiCall.FAILURE, 1);
      runner.assertTransferCount(GetHttpApiCall.SUCCESS, 2);
    }
  }

  @Test
  void onTrigger_withTheLargestStartOffset_countsPastTheIntRange() throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answeringInTurn(Response.json(FULL_PAGE), Response.json(SHORT_PAGE))) {
      TestRunner runner = limitOffsetRunner(endpoint, LIMIT_2 + ", \"offset\": 2147483647");

      runner.run();

      assertEquals(
          List.of("limit=2&offset=2147483647", "limit=2&offset=2147483649"), rawQueries(endpoint));
    }
  }

  @Test
  void basicAuthPassword_isSensitive() {
    assertTrue(GetHttpApiCall.BASIC_AUTH_PASSWORD.isSensitive());
  }

  @Test
  void validate_withoutAuthenticationAndWithoutCredentials_isValid()
      throws InitializationException {
    runner("http://localhost:8080").assertValid();
  }

  @Test
  void validate_withBasicAuthenticationAndBothCredentials_isValid() throws InitializationException {
    TestRunner runner = runner("https://api.example.org:8443/v1/");
    runner.setProperty(GetHttpApiCall.AUTHENTICATION_TYPE, "BASIC");
    runner.setProperty(GetHttpApiCall.BASIC_AUTH_USERNAME, "weather");
    runner.setProperty(GetHttpApiCall.BASIC_AUTH_PASSWORD, "secret");

    runner.assertValid();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "                                  | missing",
        "ftp://api.example.org             | x",
        "api.example.org/v1                | x",
        "http://user:pw-77ac@example.org   | pw-77ac",
        "http:///v1                        | x",
        "http://example.org/v1?key=k-91aa  | k-91aa",
        "http://example.org/v1#f-3c2e      | f-3c2e",
        "http://exa mple.org               | x"
      })
  void validate_withAnInvalidBaseUrl_isInvalidWithoutNamingTheValue(String baseUrl, String secret)
      throws InitializationException {
    TestRunner runner = runner("http://localhost:8080");
    if (baseUrl == null) {
      runner.removeProperty(GetHttpApiCall.BASE_URL);
    } else {
      runner.setProperty(GetHttpApiCall.BASE_URL, baseUrl);
    }

    Collection<ValidationResult> results = invalid(runner);

    assertTrue(
        results.stream().anyMatch(r -> "Base URL".equals(r.getSubject())), results::toString);
    assertFalse(results.toString().contains(secret), results::toString);
  }

  @Test
  void validate_withBasicAuthenticationOverHttp_isInvalid() throws InitializationException {
    TestRunner runner = runner("http://api.example.org/v1");
    runner.setProperty(GetHttpApiCall.AUTHENTICATION_TYPE, "BASIC");
    runner.setProperty(GetHttpApiCall.BASIC_AUTH_USERNAME, "weather");
    runner.setProperty(GetHttpApiCall.BASIC_AUTH_PASSWORD, "secret");

    Collection<ValidationResult> results = invalid(runner);

    assertTrue(
        results.stream().anyMatch(r -> "Base URL".equals(r.getSubject())), results::toString);
  }

  @Test
  void validate_withBasicAuthenticationWithoutAUsername_isInvalid() throws InitializationException {
    TestRunner runner = runner("http://localhost:8080");
    runner.setProperty(GetHttpApiCall.AUTHENTICATION_TYPE, "BASIC");
    runner.setProperty(GetHttpApiCall.BASIC_AUTH_PASSWORD, "secret");

    runner.assertNotValid();
  }

  @Test
  void validate_withBasicAuthenticationWithoutAPassword_isInvalid() throws InitializationException {
    TestRunner runner = runner("http://localhost:8080");
    runner.setProperty(GetHttpApiCall.AUTHENTICATION_TYPE, "BASIC");
    runner.setProperty(GetHttpApiCall.BASIC_AUTH_USERNAME, "weather");

    runner.assertNotValid();
  }

  @Test
  void validate_withAColonInTheUsername_isInvalidWithoutNamingIt() throws InitializationException {
    TestRunner runner = runner("http://localhost:8080");
    runner.setProperty(GetHttpApiCall.AUTHENTICATION_TYPE, "BASIC");
    runner.setProperty(GetHttpApiCall.BASIC_AUTH_USERNAME, "u-4d1e:x");
    runner.setProperty(GetHttpApiCall.BASIC_AUTH_PASSWORD, "secret");

    Collection<ValidationResult> results = invalid(runner);

    assertTrue(
        results.stream().anyMatch(r -> "Basic Auth Username".equals(r.getSubject())),
        results::toString);
    assertFalse(results.toString().contains("u-4d1e"), results::toString);
  }

  @Test
  void validate_withAnUnknownAuthenticationType_isInvalid() throws InitializationException {
    TestRunner runner = runner("http://localhost:8080");
    runner.setProperty(GetHttpApiCall.AUTHENTICATION_TYPE, "DIGEST");

    runner.assertNotValid();
  }

  @Test
  void validate_withoutAWebClientService_isInvalid() throws InitializationException {
    TestRunner runner = runner("http://localhost:8080");
    runner.removeProperty(GetHttpApiCall.WEB_CLIENT_SERVICE);

    runner.assertNotValid();
  }

  @Test
  void validate_withoutAnApiCall_isInvalid() throws InitializationException {
    TestRunner runner = runner("http://localhost:8080");
    runner.removeProperty(GetHttpApiCall.API_CALL);

    runner.assertNotValid();
  }

  @ParameterizedTest(name = "{1}")
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '\'',
      value = {
        "{\"path\":\"/s\"}                                                  | name",
        "{\"name\":\" \",\"path\":\"/s\"}                                   | name",
        "{\"name\":\"n\",\"method\":\"POST\",\"path\":\"/s\"}               | method",
        "{\"name\":\"n\"}                                                   | path",
        "{\"name\":\"n\",\"path\":\"stations\"}                             | path",
        "{\"name\":\"n\",\"path\":\"/s?key=v-61b0\"}                        | path",
        "{\"name\":\"n\",\"path\":\"/a b\"}                                 | path",
        "{\"name\":\"n\",\"path\":\"/s\",\"queryParameters\":\"v-61b0\"}    | queryParameters",
        "{\"name\":\"n\",\"path\":\"/s\",\"queryParameters\":[null]}        | queryParameters[0]",
        "{\"name\":\"n\",\"path\":\"/s\",\"queryParameters\":[{\"value\":\"v-61b0\"}]} | queryParameters[0].name",
        "{\"name\":\"n\",\"path\":\"/s\",\"headers\":[null]}                | headers[0]",
        "{\"name\":\"n\",\"path\":\"/s\",\"headers\":[{\"name\":\"\"}]}     | headers[0].name",
        "{\"name\":\"n\",\"path\":\"/s\",\"headers\":[{\"name\":\"A\",\"value\":\"b\"},{\"name\":\"X Y\",\"value\":\"v-61b0\"}]} | headers[1].name",
        "{\"name\":\"n\",\"path\":\"/s\",\"headers\":[{\"name\":\"Authorization\",\"value\":\"v-61b0\"}]} | headers[0].name",
        "{\"name\":\"n\",\"path\":\"/s\",\"headers\":[{\"name\":\"proxy-authorization\",\"value\":\"v-61b0\"}]} | headers[0].name",
        "{\"name\":\"n\",\"path\":\"/s\",\"headers\":[{\"name\":\"COOKIE\",\"value\":\"v-61b0\"}]} | headers[0].name",
        "{\"name\":\"n\",\"path\":\"/s\",\"headers\":[{\"name\":\"X\",\"value\":\"v-61b0\\r\\nY: z\"}]} | headers[0].value",
        "{\"name\":\"n\",\"path\":\"/s\",\"headers\":[{\"name\":\"X\",\"value\":\"v-61b0\\n\"}]} | headers[0].value",
        "{\"name\":\"n\",\"path\":\"/s\",\"headers\":[{\"name\":\"Host\",\"value\":\"v-61b0\"}]} | headers[0].name",
        "{\"name\":\"n\",\"path\":\"/s\",\"headers\":[{\"name\":\"content-length\",\"value\":\"v-61b0\"}]} | headers[0].name",
        "{\"name\":\"n\",\"path\":\"/s\",\"headers\":[{\"name\":\"X\",\"value\":\"v-61b0-€\"}]} | headers[0].value",
        "{\"name\":\"n\",\"path\":\"/s\",\"headers\":[{\"name\":\"X\",\"value\":\"v-61b0\\u0000\"}]} | headers[0].value",
        "{\"name\":\"n\",\"path\":\"/s\",\"pagination\":{\"type\":\"v-61b0\"}} | pagination.type",
        "{\"name\":\"n\",\"path\":\"/s\",\"queryParameters\":[{\"name\":\"v-61b0\",\"value\":\"x\"}],\"pagination\":{\"type\":\"PAGE_NUMBER\",\"pageSizeParameter\":\"size\",\"pageSize\":2}} | pagination.pageParameter",
        "{\"name\":\"n\",\"path\":\"/s\",\"queryParameters\":[{\"name\":\"v-61b0\",\"value\":\"x\"}],\"pagination\":{\"type\":\"PAGE_NUMBER\",\"pageParameter\":\" \",\"pageSizeParameter\":\"size\",\"pageSize\":2}} | pagination.pageParameter",
        "{\"name\":\"n\",\"path\":\"/s\",\"queryParameters\":[{\"name\":\"v-61b0\",\"value\":\"x\"}],\"pagination\":{\"type\":\"PAGE_NUMBER\",\"pageParameter\":\"page\",\"pageSize\":2}} | pagination.pageSizeParameter",
        "{\"name\":\"n\",\"path\":\"/s\",\"queryParameters\":[{\"name\":\"v-61b0\",\"value\":\"x\"}],\"pagination\":{\"type\":\"PAGE_NUMBER\",\"pageParameter\":\"page\",\"pageSizeParameter\":\"size\"}} | pagination.pageSize",
        "{\"name\":\"n\",\"path\":\"/s\",\"queryParameters\":[{\"name\":\"v-61b0\",\"value\":\"x\"}],\"pagination\":{\"type\":\"PAGE_NUMBER\",\"pageParameter\":\"page\",\"pageSizeParameter\":\"size\",\"pageSize\":0}} | pagination.pageSize",
        "{\"name\":\"n\",\"path\":\"/s\",\"queryParameters\":[{\"name\":\"v-61b0\",\"value\":\"x\"}],\"pagination\":{\"type\":\"PAGE_NUMBER\",\"pageParameter\":\"page\",\"pageSizeParameter\":\"size\",\"pageSize\":2,\"startPage\":-1}} | pagination.startPage",
        "{\"name\":\"n\",\"path\":\"/s\",\"queryParameters\":[{\"name\":\"v-61b0\",\"value\":\"x\"}],\"pagination\":{\"type\":\"PAGE_NUMBER\",\"pageParameter\":\"page\",\"pageSizeParameter\":\"size\",\"pageSize\":2,\"startPage\":3,\"endPage\":2}} | pagination.endPage",
        "{\"name\":\"n\",\"path\":\"/s\",\"queryParameters\":[{\"name\":\"v-61b0\",\"value\":\"x\"}],\"pagination\":{\"type\":\"PAGE_NUMBER\",\"pageParameter\":\"v-61b0\",\"pageSizeParameter\":\"size\",\"pageSize\":2}} | pagination.pageParameter",
        "{\"name\":\"n\",\"path\":\"/s\",\"queryParameters\":[{\"name\":\"v-61b0\",\"value\":\"x\"}],\"pagination\":{\"type\":\"PAGE_NUMBER\",\"pageParameter\":\"page\",\"pageSizeParameter\":\"v-61b0\",\"pageSize\":2}} | pagination.pageSizeParameter",
        "{\"name\":\"n\",\"path\":\"/s\",\"queryParameters\":[{\"name\":\"v-61b0\",\"value\":\"x\"}],\"pagination\":{\"type\":\"PAGE_NUMBER\",\"pageParameter\":\"page\",\"pageSizeParameter\":\"page\",\"pageSize\":2}} | pagination.pageSizeParameter",
        "{\"name\":\"n\",\"path\":\"/s\",\"queryParameters\":[{\"name\":\"v-61b0\",\"value\":\"x\"}],\"pagination\":{\"type\":\"PAGE_NUMBER\",\"pageParameter\":\"page\",\"pageSizeParameter\":\"size\",\"pageSize\":2,\"extra\":\"v-61b0\"}} | pagination.extra",
        "{\"name\":\"n\",\"path\":\"/s\",\"queryParameters\":[{\"name\":\"v-61b0\",\"value\":\"x\"}],\"pagination\":{\"type\":\"PAGE_NUMBER\",\"pageParameter\":\"page\",\"pageSizeParameter\":\"size\",\"pageSize\":\"v-61b0\"}} | pagination.pageSize",
        "{\"name\":\"n\",\"path\":\"/s\",\"extra\":\"v-61b0\"}              | extra",
        "{\"name\":\"n\",\"path\":\"/s\",\"headers\":[{\"name\":\"A\",\"value\":\"b\"},{\"name\":\"X\",\"extra\":\"v-61b0\"}]} | headers[1].extra",
        "{\"name\":\"n\",\"path\":\"/s\"} {\"v-61b0\":1}                    | API Call",
        "{\"name\":\"n\",\"path\":\"v-61b0                                  | API Call",
        "[\"v-61b0\"]                                                       | API Call"
      })
  void validate_withAnApiCallThatBreaksARule_namesTheKeyPathAndNoValue(
      String apiCall, String keyPath) throws InitializationException {
    TestRunner runner = runner("http://localhost:8080");
    runner.setProperty(GetHttpApiCall.API_CALL, apiCall);

    Collection<ValidationResult> results = invalid(runner);

    assertTrue(
        results.stream()
            .anyMatch(
                r ->
                    "API Call".equals(r.getSubject())
                        && r.getExplanation().startsWith(keyPath + " ")),
        results::toString);
    assertFalse(results.toString().contains("v-61b0"), results::toString);
  }

  @Test
  void validate_withAPaginationInTheFormOfThePortal_isValid() throws InitializationException {
    TestRunner runner = runner("http://localhost:8080");
    runner.setProperty(
        GetHttpApiCall.API_CALL,
        """
        {"name": "Stations", "method": "GET", "path": "/stations",
         "queryParameters": [{"name": "Page", "value": "x"}], "headers": [],
         "pagination": {"type": "PAGE_NUMBER", "pageParameter": "page",
                        "pageSizeParameter": "size", "pageSize": 100,
                        "startPage": 0, "endPage": null}}""");

    runner.assertValid();
  }

  @Test
  void validate_withAnApiCallThatBreaksSeveralRules_reportsEachOfThem()
      throws InitializationException {
    TestRunner runner = runner("http://localhost:8080");
    runner.setProperty(GetHttpApiCall.API_CALL, "{\"name\":\"\",\"path\":\"x\",\"pagination\":{}}");

    assertEquals(3, invalid(runner).size(), invalid(runner)::toString);
  }

  @ParameterizedTest(name = "{1}: {0}")
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '\'',
      value = {
        "\"cursorParameter\":\"c\",\"cursorIn\":\"QUERY\"                                   | pagination.cursorPath",
        "\"cursorPath\":\" \",\"cursorParameter\":\"c\",\"cursorIn\":\"QUERY\"              | pagination.cursorPath",
        "\"cursorPath\":\"$..v-61b0\",\"cursorParameter\":\"c\",\"cursorIn\":\"QUERY\"      | pagination.cursorPath",
        "\"cursorPath\":\"$.items[?(@.v-61b0)]\",\"cursorParameter\":\"c\",\"cursorIn\":\"QUERY\" | pagination.cursorPath",
        "\"cursorPath\":\"$.c\",\"cursorIn\":\"QUERY\"                                      | pagination.cursorParameter",
        "\"cursorPath\":\"$.c\",\"cursorParameter\":\" \",\"cursorIn\":\"QUERY\"            | pagination.cursorParameter",
        "\"cursorPath\":\"$.c\",\"cursorParameter\":\"c\"                                   | pagination.cursorIn",
        "\"cursorPath\":\"$.c\",\"cursorParameter\":\"c\",\"cursorIn\":\"v-61b0\"           | pagination.cursorIn",
        "\"cursorPath\":\"$.c\",\"cursorParameter\":\"Authorization\",\"cursorIn\":\"HEADER\" | pagination.cursorParameter",
        "\"cursorPath\":\"$.c\",\"cursorParameter\":\"host\",\"cursorIn\":\"HEADER\"        | pagination.cursorParameter",
        "\"cursorPath\":\"$.c\",\"cursorParameter\":\"X v-61b0\",\"cursorIn\":\"HEADER\"    | pagination.cursorParameter",
        "\"cursorPath\":\"$.c\",\"cursorParameter\":\"x-v-61b0\",\"cursorIn\":\"HEADER\"    | pagination.cursorParameter",
        "\"cursorPath\":\"$.c\",\"cursorParameter\":\"v-61b0\",\"cursorIn\":\"QUERY\"       | pagination.cursorParameter",
        "\"cursorPath\":\"$.c\",\"cursorParameter\":\"c\",\"cursorIn\":\"QUERY\",\"limitParameter\":\"limit\" | pagination.limit",
        "\"cursorPath\":\"$.c\",\"cursorParameter\":\"c\",\"cursorIn\":\"QUERY\",\"limit\":20 | pagination.limitParameter",
        "\"cursorPath\":\"$.c\",\"cursorParameter\":\"c\",\"cursorIn\":\"QUERY\",\"limitParameter\":\"limit\",\"limit\":0 | pagination.limit",
        "\"cursorPath\":\"$.c\",\"cursorParameter\":\"c\",\"cursorIn\":\"QUERY\",\"limitParameter\":\"v-61b0\",\"limit\":20 | pagination.limitParameter",
        "\"cursorPath\":\"$.c\",\"cursorParameter\":\"c\",\"cursorIn\":\"QUERY\",\"limitParameter\":\"c\",\"limit\":20 | pagination.limitParameter",
        "\"cursorPath\":\"$.c\",\"cursorParameter\":\"c\",\"cursorIn\":\"QUERY\",\"limit\":\"v-61b0\" | pagination.limit"
      })
  void validate_withACursorPaginationThatBreaksARule_namesTheKeyPathAndNoValue(
      String paginationFields, String keyPath) throws InitializationException {
    TestRunner runner = runner("http://localhost:8080");
    runner.setProperty(
        GetHttpApiCall.API_CALL,
        "{\"name\":\"n\",\"path\":\"/s\","
            + "\"queryParameters\":[{\"name\":\"v-61b0\",\"value\":\"x\"}],"
            + "\"headers\":[{\"name\":\"X-V-61B0\",\"value\":\"x\"}],"
            + "\"pagination\":{\"type\":\"CURSOR\","
            + paginationFields
            + "}}");

    Collection<ValidationResult> results = invalid(runner);

    assertTrue(
        results.stream()
            .anyMatch(
                r ->
                    "API Call".equals(r.getSubject())
                        && r.getExplanation().startsWith(keyPath + " ")),
        results::toString);
    assertFalse(results.toString().contains("v-61b0"), results::toString);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\"cursorParameter\": \"cursor\", \"cursorIn\": \"QUERY\", \"limitParameter\": null,"
            + " \"limit\": null",
        "\"cursorParameter\": \"X-Cursor\", \"cursorIn\": \"HEADER\", \"limitParameter\": \"limit\","
            + " \"limit\": 100",
        "\"cursorParameter\": \"cursor\", \"cursorIn\": \"QUERY\", \"limitParameter\": \" \""
      })
  void validate_withACursorPaginationInTheFormOfThePortal_isValid(String fields)
      throws InitializationException {
    TestRunner runner = runner("http://localhost:8080");
    runner.setProperty(
        GetHttpApiCall.API_CALL,
        "{\"name\": \"Stations\", \"method\": \"GET\", \"path\": \"/stations\","
            + " \"queryParameters\": [{\"name\": \"Cursor\", \"value\": \"x\"}], \"headers\": [],"
            + " \"pagination\": {\"type\": \"CURSOR\", \"cursorPath\": \"$['next cursor']\", "
            + fields
            + "}}");

    runner.assertValid();
  }

  @Test
  void validate_withACursorHeaderNamedLikeAQueryParameter_isValid() throws InitializationException {
    TestRunner runner = runner("http://localhost:8080");
    runner.setProperty(
        GetHttpApiCall.API_CALL,
        """
        {"name": "Stations", "path": "/stations",
         "queryParameters": [{"name": "cursor", "value": "x"}],
         "pagination": {"type": "CURSOR", "cursorPath": "$.next_cursor",
                        "cursorParameter": "cursor", "cursorIn": "HEADER",
                        "limitParameter": "limit", "limit": 5}}""");

    runner.assertValid();
  }

  @ParameterizedTest(name = "{1}: {0}")
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '\'',
      value = {
        "\"limitParameter\":\"limit\",\"limit\":20                                     | pagination.offsetParameter",
        "\"offsetParameter\":\" \"                                                     | pagination.offsetParameter",
        "\"offsetParameter\":\"v-61b0\"                                                | pagination.offsetParameter",
        "\"offsetParameter\":\"o\",\"limitParameter\":\"limit\"                        | pagination.limit",
        "\"offsetParameter\":\"o\",\"limit\":20                                        | pagination.limitParameter",
        "\"offsetParameter\":\"o\",\"limitParameter\":\"limit\",\"limit\":0            | pagination.limit",
        "\"offsetParameter\":\"o\",\"limitParameter\":\"v-61b0\",\"limit\":20          | pagination.limitParameter",
        "\"offsetParameter\":\"o\",\"limitParameter\":\"o\",\"limit\":20               | pagination.offsetParameter",
        "\"offsetParameter\":\"o\",\"offset\":-1                                       | pagination.offset",
        "\"offsetParameter\":\"o\",\"offset\":\"v-61b0\"                               | pagination.offset",
        "\"offsetParameter\":\"o\",\"countPath\":\"v-61b0\"                            | pagination.countPath",
        "\"offsetParameter\":\"o\",\"countPath\":\"$..v-61b0\"                         | pagination.countPath",
        "\"offsetParameter\":\"o\",\"countPath\":\"$.items[?(@.v-61b0)]\"              | pagination.countPath"
      })
  void validate_withALimitOffsetPaginationThatBreaksARule_namesTheKeyPathAndNoValue(
      String paginationFields, String keyPath) throws InitializationException {
    TestRunner runner = runner("http://localhost:8080");
    runner.setProperty(
        GetHttpApiCall.API_CALL,
        "{\"name\":\"n\",\"path\":\"/s\","
            + "\"queryParameters\":[{\"name\":\"v-61b0\",\"value\":\"x\"}],"
            + "\"pagination\":{\"type\":\"LIMIT_OFFSET\","
            + paginationFields
            + "}}");

    Collection<ValidationResult> results = invalid(runner);

    assertTrue(
        results.stream()
            .anyMatch(
                r ->
                    "API Call".equals(r.getSubject())
                        && r.getExplanation().startsWith(keyPath + " ")),
        results::toString);
    assertFalse(results.toString().contains("v-61b0"), results::toString);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\"limitParameter\": \"limit\", \"limit\": 100, \"offset\": 0, \"countPath\": null",
        "\"limitParameter\": null, \"limit\": null, \"offset\": null,"
            + " \"countPath\": \"$['meta']['count']\"",
        "\"limitParameter\": \" \", \"countPath\": \" \""
      })
  void validate_withALimitOffsetPaginationInTheFormOfThePortal_isValid(String fields)
      throws InitializationException {
    TestRunner runner = runner("http://localhost:8080");
    runner.setProperty(
        GetHttpApiCall.API_CALL,
        "{\"name\": \"Stations\", \"method\": \"GET\", \"path\": \"/stations\","
            + " \"queryParameters\": [{\"name\": \"Offset\", \"value\": \"x\"}], \"headers\": [],"
            + " \"pagination\": {\"type\": \"LIMIT_OFFSET\", \"offsetParameter\": \"offset\", "
            + fields
            + "}}");

    runner.assertValid();
  }

  private static void assertStartFails(ApiEndpoint endpoint, String schema)
      throws InitializationException {
    TestRunner runner = runner(endpoint.baseUrl());
    runner.setProperty(GetHttpApiCall.RECORD_SCHEMA, schema);
    runner.assertValid();

    AssertionError failure = assertThrows(AssertionError.class, runner::run);

    assertTrue(
        causes(failure).anyMatch(InvalidRecordSchemaException.class::isInstance),
        failure::toString);
    assertTrue(endpoint.requests().isEmpty());
  }

  private static Stream<Throwable> causes(Throwable throwable) {
    return Stream.iterate(throwable, Objects::nonNull, Throwable::getCause);
  }

  /** A runner for a page-number pagination with page size 2 and the further fields. */
  private static TestRunner paginatedRunner(ApiEndpoint endpoint, String furtherFields)
      throws InitializationException {
    TestRunner runner = runner(endpoint.baseUrl());
    runner.setProperty(
        GetHttpApiCall.API_CALL,
        "{\"name\": \"Stations\", \"path\": \"/stations\", \"pagination\": {\"type\":"
            + " \"PAGE_NUMBER\", \"pageParameter\": \"page\", \"pageSizeParameter\": \"size\","
            + " \"pageSize\": 2"
            + furtherFields
            + "}}");
    return runner;
  }

  /** A runner for a cursor pagination at {@code $.next_cursor} with the further fields. */
  private static TestRunner cursorRunner(ApiEndpoint endpoint, String furtherFields)
      throws InitializationException {
    TestRunner runner = runner(endpoint.baseUrl());
    runner.setProperty(GetHttpApiCall.RECORD_SCHEMA, ONE_RECORD_SCHEMA);
    runner.setProperty(
        GetHttpApiCall.API_CALL,
        "{\"name\": \"Stations\", \"path\": \"/stations\", \"pagination\": {\"type\":"
            + " \"CURSOR\", \"cursorPath\": \"$.next_cursor\", "
            + furtherFields
            + "}}");
    return runner;
  }

  /** A runner for a limit/offset pagination with the offset parameter and the further fields. */
  private static TestRunner limitOffsetRunner(ApiEndpoint endpoint, String furtherFields)
      throws InitializationException {
    TestRunner runner = runner(endpoint.baseUrl());
    runner.setProperty(
        GetHttpApiCall.API_CALL,
        "{\"name\": \"Stations\", \"path\": \"/stations\", \"pagination\": {\"type\":"
            + " \"LIMIT_OFFSET\", \"offsetParameter\": \"offset\""
            + furtherFields
            + "}}");
    return runner;
  }

  /** A runner for a limit/offset pagination with the count at {@code $.meta.count}. */
  private static TestRunner countPathRunner(ApiEndpoint endpoint, String furtherFields)
      throws InitializationException {
    TestRunner runner =
        limitOffsetRunner(endpoint, ", \"countPath\": \"$.meta.count\"" + furtherFields);
    runner.setProperty(GetHttpApiCall.RECORD_SCHEMA, ONE_RECORD_SCHEMA);
    return runner;
  }

  /**
   * A response of a limit/offset pagination with a count path.
   *
   * @param count the JSON at {@code $.meta.count}, or null for a response without one
   */
  private static String countPage(String count, String... stationIds) {
    String records =
        Arrays.stream(stationIds)
            .map(id -> "{\"stationid\": \"" + id + "\"}")
            .collect(Collectors.joining(", "));
    return "{\"data\": ["
        + records
        + "]"
        + (count == null ? "" : ", \"meta\": {\"count\": " + count + "}")
        + "}";
  }

  /**
   * A response of a cursor pagination with one record.
   *
   * @param cursor the JSON of the cursor, or null for a response without one
   */
  private static String cursorPage(String stationId, String cursor) {
    return "{\"data\": [{\"stationid\": \""
        + stationId
        + "\"}]"
        + (cursor == null ? "" : ", \"next_cursor\": " + cursor)
        + "}";
  }

  /** A page of page size 2 with records of its own, so that no two pages have the same body. */
  private static String fullPage(int page) {
    return "[{\"stationid\":\"p" + page + "a\"},{\"stationid\":\"p" + page + "b\"}]";
  }

  private static void assertPages(TestRunner runner, String... pages) {
    runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, pages.length);
    List<String> actual =
        runner.getFlowFilesForRelationship(GetHttpApiCall.SUCCESS).stream()
            .map(flowFile -> flowFile.getAttribute(GetHttpApiCall.PAGE))
            .toList();
    assertEquals(List.of(pages), actual);
  }

  private static List<String> rawQueries(ApiEndpoint endpoint) {
    return endpoint.requests().stream().map(ApiEndpoint.Request::rawQuery).toList();
  }

  private static TestRunner runner(String baseUrl) throws InitializationException {
    return runner(baseUrl, null);
  }

  /** A runner whose web client service trusts the certificate of {@code trusted}, if given. */
  private static TestRunner runner(String baseUrl, TestTls trusted) throws InitializationException {
    return GetHttpApiCallRunners.runner(
        new GetHttpApiCall(), baseUrl, trusted == null ? null : trusted.clientProvider());
  }
}
