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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.ssl.SSLContextProvider;
import org.apache.nifi.util.LogMessage;
import org.apache.nifi.util.MockComponentLog;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.apache.nifi.web.client.provider.service.StandardWebClientServiceProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class GetHttpApiCallTest {

  private static final String WEB_CLIENT = "web-client";
  private static final String TLS = "tls";

  private static final String STATIONS = "{\"name\":\"Stations\",\"path\":\"/stations\"}";

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
    String body = "[{\"id\":1,\"name\":\"Münster\"},{\"id\":2}]";
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
  @ValueSource(ints = {400, 401, 403, 404, 301, 302})
  void onTrigger_whenTheStatusNeedsAChangeOfTheConfiguration_routesToStop(int status)
      throws InitializationException {
    try (ApiEndpoint endpoint =
        ApiEndpoint.answering(status, Map.of("Location", "/v1/elsewhere"), "{}")) {
      TestRunner runner = runner(endpoint.baseUrl());

      runner.run();

      String message = "API call \"Stations\": GET /stations failed with status " + status;
      assertRouted(runner, GetHttpApiCall.STOP, status, message);
      assertEquals(1, endpoint.requests().size());
      List<LogMessage> errors = runner.getLogger().getErrorMessages();
      assertEquals(1, errors.size());
      assertTrue(errors.get(0).getMsg().contains(message), errors.get(0).getMsg());
      assertTrue(runner.getLogger().getWarnMessages().isEmpty());
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

      List<String> visible = new ArrayList<>(logged(runner.getLogger()));
      for (MockFlowFile flowFile : runner.getFlowFilesForRelationship(GetHttpApiCall.SUCCESS)) {
        visible.addAll(flowFile.getAttributes().values());
      }
      for (MockFlowFile flowFile : runner.getFlowFilesForRelationship(GetHttpApiCall.FAILURE)) {
        visible.addAll(flowFile.getAttributes().values());
      }
      for (MockFlowFile flowFile : runner.getFlowFilesForRelationship(GetHttpApiCall.STOP)) {
        visible.addAll(flowFile.getAttributes().values());
      }
      assertFalse(visible.isEmpty());
      for (String text : visible) {
        assertFalse(text.contains(password), text);
        assertFalse(text.contains(queryValue), text);
        assertFalse(text.contains(username), text);
      }
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
        "{\"name\":\"n\",\"path\":\"/s\",\"pagination\":{\"type\":\"v-61b0\"}} | pagination",
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
  void validate_withAnApiCallThatBreaksSeveralRules_reportsEachOfThem()
      throws InitializationException {
    TestRunner runner = runner("http://localhost:8080");
    runner.setProperty(GetHttpApiCall.API_CALL, "{\"name\":\"\",\"path\":\"x\",\"pagination\":{}}");

    assertEquals(3, invalid(runner).size(), invalid(runner)::toString);
  }

  private static Collection<ValidationResult> invalid(TestRunner runner) {
    return runner.validate().stream().filter(result -> !result.isValid()).toList();
  }

  private static void assertRouted(
      TestRunner runner, Relationship relationship, int status, String message) {
    runner.assertAllFlowFilesTransferred(relationship, 1);
    MockFlowFile failed = runner.getFlowFilesForRelationship(relationship).get(0);
    failed.assertAttributeEquals(GetHttpApiCall.REQUEST_PATH, "/stations");
    failed.assertAttributeEquals(GetHttpApiCall.ERROR_STATUS, Integer.toString(status));
    failed.assertAttributeEquals(GetHttpApiCall.ERROR_MESSAGE, message);
    failed.assertContentEquals("");
  }

  /** Every log message with its arguments and exceptions, as a bulletin would show it. */
  private static List<String> logged(MockComponentLog log) {
    List<LogMessage> messages = new ArrayList<>();
    messages.addAll(log.getTraceMessages());
    messages.addAll(log.getDebugMessages());
    messages.addAll(log.getInfoMessages());
    messages.addAll(log.getWarnMessages());
    messages.addAll(log.getErrorMessages());
    List<String> texts = new ArrayList<>();
    for (LogMessage message : messages) {
      texts.add(message.toString());
      texts.add(String.valueOf(message.getThrowable()));
      for (Object argument : message.getArgs() == null ? new Object[0] : message.getArgs()) {
        texts.add(String.valueOf(argument));
      }
    }
    return texts;
  }

  private static TestRunner runner(String baseUrl) throws InitializationException {
    return runner(baseUrl, null);
  }

  /** A runner whose web client service trusts the certificate of {@code trusted}, if given. */
  private static TestRunner runner(String baseUrl, TestTls trusted) throws InitializationException {
    TestRunner runner = TestRunners.newTestRunner(GetHttpApiCall.class);
    StandardWebClientServiceProvider webClient = new StandardWebClientServiceProvider();
    runner.addControllerService(WEB_CLIENT, webClient);
    runner.setProperty(webClient, "Redirect Handling Strategy", "IGNORED");
    if (trusted != null) {
      SSLContextProvider provider = trusted.clientProvider();
      runner.addControllerService(TLS, provider);
      runner.enableControllerService(provider);
      runner.setProperty(webClient, "SSL Context Service", TLS);
    }
    runner.enableControllerService(webClient);

    runner.setProperty(GetHttpApiCall.WEB_CLIENT_SERVICE, WEB_CLIENT);
    runner.setProperty(GetHttpApiCall.BASE_URL, baseUrl);
    runner.setProperty(GetHttpApiCall.API_CALL, STATIONS);
    return runner;
  }
}
