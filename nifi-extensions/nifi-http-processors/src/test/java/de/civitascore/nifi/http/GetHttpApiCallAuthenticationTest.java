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
import static de.civitascore.nifi.http.GetHttpApiCallRunners.visible;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.nifi.http.ApiEndpoint.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.net.ssl.SSLHandshakeException;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class GetHttpApiCallAuthenticationTest {

  private static final String EMPTY = "[]";
  private static final String FULL_PAGE = "[{\"stationid\":\"a1\"},{\"stationid\":\"a2\"}]";
  private static final String SHORT_PAGE = "[{\"stationid\":\"a3\"}]";
  private static final String PAGINATED =
      "{\"name\": \"Stations\", \"path\": \"/stations\", \"queryParameters\": [{\"name\":"
          + " \"status\", \"value\": \"active\"}], \"pagination\": {\"type\": \"PAGE_NUMBER\","
          + " \"pageParameter\": \"page\", \"pageSizeParameter\": \"size\", \"pageSize\": 2}}";
  private static final String HTTPS_API = "https://api.example.org/v1";
  private static final String CLIENT_ID = "client id:ü";
  private static final String CLIENT_SECRET = "s3cret&=+ü";
  private static final Duration EXPIRES_IN = Duration.ofHours(1);

  @TempDir static Path keys;

  private static TestTls tls;

  private final MutableClock clock = new MutableClock();

  @BeforeAll
  static void createCertificates() throws IOException, GeneralSecurityException {
    tls = TestTls.create(keys);
  }

  @Test
  void sendsABearerToken() throws InitializationException {
    try (ApiEndpoint api = ApiEndpoint.answeringOverTls(tls.server(), 200, EMPTY)) {
      TestRunner runner = runner(api.baseUrl());
      runner.setProperty(GetHttpApiCall.AUTHENTICATION_TYPE, "BEARER_TOKEN");
      runner.setProperty(GetHttpApiCall.BEARER_TOKEN, "bt-4f2a");

      runner.run();

      runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, 1);
      assertEquals(List.of("Bearer bt-4f2a"), authorizations(api));
    }
  }

  @Test
  void sendsAnApiTokenAsAHeader() throws InitializationException {
    try (ApiEndpoint api = ApiEndpoint.answeringOverTls(tls.server(), 200, EMPTY)) {
      TestRunner runner = runner(api.baseUrl());
      apiToken(runner, "X-Api-Key", "at-91c0", ParameterLocation.HEADER);

      runner.run();

      runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, 1);
      ApiEndpoint.Request request = api.requests().get(0);
      assertEquals("at-91c0", request.headers().getFirst("X-Api-Key"));
      assertEquals(null, request.rawQuery());
    }
  }

  @Test
  void appendsAnApiTokenToTheQueryPercentEncoded() throws InitializationException {
    try (ApiEndpoint api = ApiEndpoint.answeringOverTls(tls.server(), 200, SHORT_PAGE)) {
      TestRunner runner = runner(api.baseUrl());
      runner.setProperty(GetHttpApiCall.API_CALL, PAGINATED);
      apiToken(runner, "api key", "a&b=c+d ü", ParameterLocation.QUERY);

      runner.run();

      runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, 1);
      ApiEndpoint.Request request = api.requests().get(0);
      assertEquals(
          "status=active&page=0&size=2&api%20key=a%26b%3Dc%2Bd%20%C3%BC", request.rawQuery());
      assertFalse(request.headers().containsKey("api key"));
    }
  }

  @Test
  void postsTheClientCredentialsGrantAndSendsTheAccessToken() throws InitializationException {
    try (ApiEndpoint token = tokenEndpoint(token("at-1", EXPIRES_IN));
        ApiEndpoint api = ApiEndpoint.answeringOverTls(tls.server(), 200, EMPTY)) {
      TestRunner runner = oauth2Runner(api, token);
      runner.setProperty(GetHttpApiCall.OAUTH2_SCOPE, "read write");

      runner.run();

      runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, 1);
      ApiEndpoint.Request grant = token.requests().get(0);
      assertEquals("POST", grant.method());
      assertEquals("/v1/token", grant.rawPath());
      assertEquals("application/x-www-form-urlencoded", grant.headers().getFirst("Content-Type"));
      assertEquals("application/json", grant.headers().getFirst("Accept"));
      assertEquals(
          "Basic " + base64("client%20id%3A%C3%BC:s3cret%26%3D%2B%C3%BC"),
          grant.headers().getFirst("Authorization"));
      assertEquals("grant_type=client_credentials&scope=read%20write", grant.body());
      assertEquals(List.of("Bearer at-1"), authorizations(api));
    }
  }

  @Test
  void requestsNoScopeWhenNoneIsSet() throws InitializationException {
    try (ApiEndpoint token = tokenEndpoint(token("at-1", EXPIRES_IN));
        ApiEndpoint api = ApiEndpoint.answeringOverTls(tls.server(), 200, EMPTY)) {
      TestRunner runner = oauth2Runner(api, token);

      runner.run();

      assertEquals("grant_type=client_credentials", token.requests().get(0).body());
    }
  }

  @Test
  void sendsTheScopeUntrimmedAsTheConnectionTestDoes() throws InitializationException {
    try (ApiEndpoint token = tokenEndpoint(token("at-1", EXPIRES_IN));
        ApiEndpoint api = ApiEndpoint.answeringOverTls(tls.server(), 200, EMPTY)) {
      TestRunner runner = oauth2Runner(api, token);
      runner.setProperty(GetHttpApiCall.OAUTH2_SCOPE, " read ");

      runner.run();

      assertEquals(
          "grant_type=client_credentials&scope=%20read%20", token.requests().get(0).body());
    }
  }

  @Test
  void presentsTheClientCertificateWithItsChain() throws InitializationException {
    try (ApiEndpoint api =
        ApiEndpoint.answeringOverMutualTls(tls.mutualServer(), "TLSv1.3", Response.json(EMPTY))) {
      TestRunner runner = clientCertificateRunner(api.baseUrl());

      runner.run();

      runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, 1);
      assertEquals(tls.clientChain(), api.requests().get(0).clientCertificates());
    }
  }

  @ParameterizedTest(name = "{0} {2}")
  @CsvSource({
    "BASIC, Basic Auth, 401",
    "BASIC, Basic Auth, 403",
    "BEARER_TOKEN, Bearer Token, 401",
    "BEARER_TOKEN, Bearer Token, 403",
    "API_TOKEN, API Token, 401",
    "API_TOKEN, API Token, 403",
    "OAUTH2, OAuth2, 401",
    "OAUTH2, OAuth2, 403",
    "CLIENT_CERTIFICATE, Client certificate, 401",
    "CLIENT_CERTIFICATE, Client certificate, 403"
  })
  void aRefusedCredentialFailsWithTheStatus(AuthenticationType type, String method, int status)
      throws InitializationException {
    Secrets secrets = Secrets.unique();
    try (ApiEndpoint token = tokenEndpoint(token(secrets.accessToken(), EXPIRES_IN));
        ApiEndpoint api = api(type, Response.status(status))) {
      TestRunner runner = authenticatedRunner(type, ParameterLocation.HEADER, api, token, secrets);

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          status,
          "API call \"Stations\": GET /stations failed with status "
              + status
              + " ("
              + method
              + ")");
      assertEquals(1, api.requests().size());
    }
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "{\"error\":\"invalid_client\",\"error_description\":\"client d-4c1f\"} | ' (invalid_client)'",
        "{\"error\":\"e-91aa\",\"error_description\":\"client d-4c1f\"}         | ''",
        "client d-4c1f e-91aa                                                   | ''"
      })
  void aRefusedTokenRequestIsASourceErrorWithTheOAuthErrorCodeOnly(String body, String errorCode)
      throws InitializationException {
    try (ApiEndpoint token = tokenEndpoint(Response.status(401, body));
        ApiEndpoint api = ApiEndpoint.answeringOverTls(tls.server(), 200, EMPTY)) {
      TestRunner runner = oauth2Runner(api, token);

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          401,
          "API call \"Stations\": OAuth2 token request to "
              + tokenEndpoint(token)
              + " failed with status 401"
              + errorCode);
      assertTrue(api.requests().isEmpty());
      for (String text : visible(runner)) {
        assertFalse(text.contains("d-4c1f"), text);
        assertFalse(text.contains("e-91aa"), text);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {500, 408, 429})
  void aTemporaryErrorOfTheTokenEndpointCallsNoApi(int status) throws InitializationException {
    try (ApiEndpoint token = tokenEndpoint(Response.status(status));
        ApiEndpoint api = ApiEndpoint.answeringOverTls(tls.server(), 200, EMPTY)) {
      TestRunner runner = oauth2Runner(api, token);

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.FAILURE,
          status,
          "API call \"Stations\": OAuth2 token request to "
              + tokenEndpoint(token)
              + " failed with status "
              + status);
      assertTrue(api.requests().isEmpty());
      assertEquals(1, runner.getLogger().getWarnMessages().size());
    }
  }

  @Test
  void anUnreachableTokenEndpointIsNamedLikeAnUnreachableApi() throws InitializationException {
    try (ApiEndpoint api = ApiEndpoint.answeringOverTls(tls.server(), 200, EMPTY)) {
      ApiEndpoint token = tokenEndpoint(token("at-1", EXPIRES_IN));
      token.close();
      TestRunner runner = oauth2Runner(api, token);

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.FAILURE,
          0,
          "API call \"Stations\": OAuth2 token request to "
              + tokenEndpoint(token)
              + " failed: ConnectException");
      assertTrue(api.requests().isEmpty());
    }
  }

  @Test
  void aRedirectOfTheTokenEndpointIsNotFollowed() throws InitializationException {
    try (ApiEndpoint token =
            tokenEndpoint(
                new Response(
                    302, Map.of("Location", "/v1/elsewhere"), "{\"access_token\":\"x\"}"));
        ApiEndpoint api = ApiEndpoint.answeringOverTls(tls.server(), 200, EMPTY)) {
      TestRunner runner = oauth2Runner(api, token);

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          302,
          "API call \"Stations\": OAuth2 token request to "
              + tokenEndpoint(token)
              + " failed with status 302");
      assertEquals(1, token.requests().size());
      assertTrue(api.requests().isEmpty());
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "[]",
        "[{\"access_token\":\"at-1\"}]",
        "not JSON",
        "",
        "  ",
        "{\"access_token\":\"  \"}",
        "{\"access_token\":42}"
      })
  void aTokenResponseWithoutAnAccessTokenIsASourceError(String body)
      throws InitializationException {
    try (ApiEndpoint token = tokenEndpoint(Response.json(body));
        ApiEndpoint api = ApiEndpoint.answeringOverTls(tls.server(), 200, EMPTY)) {
      TestRunner runner = oauth2Runner(api, token);

      runner.run();

      assertMissingAccessToken(runner, token);
      assertTrue(api.requests().isEmpty());
    }
  }

  @Test
  void readsAtMostTheFirst64KiBOfTheTokenResponse() throws InitializationException {
    String body =
        "{\"padding\":\""
            + "x".repeat(ClientCredentialsGrant.MAX_RESPONSE_BYTES)
            + "\",\"access_token\":\"at-1\"}";
    try (ApiEndpoint token = tokenEndpoint(Response.json(body));
        ApiEndpoint api = ApiEndpoint.answeringOverTls(tls.server(), 200, EMPTY)) {
      TestRunner runner = oauth2Runner(api, token);

      runner.run();

      assertMissingAccessToken(runner, token);
      assertTrue(api.requests().isEmpty());
    }
  }

  @Test
  void keepsTheAccessTokenUntilShortlyBeforeItExpires() throws InitializationException {
    try (ApiEndpoint token = tokenEndpoint(token("at-1", EXPIRES_IN), token("at-2", EXPIRES_IN));
        ApiEndpoint api = ApiEndpoint.answeringOverTls(tls.server(), 200, EMPTY)) {
      TestRunner runner = oauth2Runner(api, token);

      runner.run(1, false, true);
      clock.advance(EXPIRES_IN.minusSeconds(31));
      runner.run(1, true, false);

      runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, 2);
      assertEquals(1, token.requests().size());
      assertEquals(List.of("Bearer at-1", "Bearer at-1"), authorizations(api));
    }
  }

  @Test
  void getsANewAccessTokenAfterItExpires() throws InitializationException {
    try (ApiEndpoint token = tokenEndpoint(token("at-1", EXPIRES_IN), token("at-2", EXPIRES_IN));
        ApiEndpoint api = ApiEndpoint.answeringOverTls(tls.server(), 200, EMPTY)) {
      TestRunner runner = oauth2Runner(api, token);

      runner.run(1, false, true);
      clock.advance(EXPIRES_IN.minusSeconds(30));
      runner.run(1, true, false);

      runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, 2);
      assertEquals(2, token.requests().size());
      assertEquals(List.of("Bearer at-1", "Bearer at-2"), authorizations(api));
    }
  }

  @Test
  void getsANewAccessTokenForEachRunWithoutExpiresIn() throws InitializationException {
    try (ApiEndpoint token =
            tokenEndpoint(
                Response.json("{\"access_token\":\"at-1\"}"),
                Response.json("{\"access_token\":\"at-2\",\"expires_in\":30}"),
                Response.json("{\"access_token\":\"at-3\"}"));
        ApiEndpoint api =
            ApiEndpoint.answeringInTurnOverTls(
                tls.server(),
                Response.json(FULL_PAGE),
                Response.json(SHORT_PAGE),
                Response.json(SHORT_PAGE),
                Response.json(SHORT_PAGE))) {
      TestRunner runner = oauth2Runner(api, token);
      runner.setProperty(GetHttpApiCall.API_CALL, PAGINATED);

      runner.run(1, false, true);
      assertEquals(1, token.requests().size());
      runner.run(1, false, false);
      runner.run(1, true, false);

      assertEquals(3, token.requests().size());
      assertEquals(
          List.of("Bearer at-1", "Bearer at-1", "Bearer at-2", "Bearer at-3"), authorizations(api));
    }
  }

  @Test
  void a401WithAKeptTokenGetsOneNewTokenAndOneMoreAttempt() throws InitializationException {
    try (ApiEndpoint token = tokenEndpoint(token("at-1", EXPIRES_IN), token("at-2", EXPIRES_IN));
        ApiEndpoint api =
            ApiEndpoint.answeringInTurnOverTls(
                tls.server(), Response.json(EMPTY), Response.status(401), Response.json(EMPTY))) {
      TestRunner runner = oauth2Runner(api, token);

      runner.run(1, false, true);
      runner.run(1, true, false);

      runner.assertAllFlowFilesTransferred(GetHttpApiCall.SUCCESS, 2);
      assertEquals(2, token.requests().size());
      assertEquals(List.of("Bearer at-1", "Bearer at-1", "Bearer at-2"), authorizations(api));
      assertTrue(runner.getLogger().getErrorMessages().isEmpty());
    }
  }

  @Test
  void a401AfterTheNewTokenIsFatal() throws InitializationException {
    try (ApiEndpoint token =
            tokenEndpoint(
                token("at-1", EXPIRES_IN), token("at-2", EXPIRES_IN), token("at-3", EXPIRES_IN));
        ApiEndpoint api =
            ApiEndpoint.answeringInTurnOverTls(
                tls.server(),
                Response.json(EMPTY),
                Response.status(401),
                Response.status(401),
                Response.json(EMPTY))) {
      TestRunner runner = oauth2Runner(api, token);

      runner.run(1, false, true);
      runner.clearTransferState();
      runner.run(1, true, false);

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          401,
          "API call \"Stations\": GET /stations failed with status 401 (OAuth2)");
      assertEquals(2, token.requests().size());
      assertEquals(3, api.requests().size());
    }
  }

  @Test
  void aRefusedTokenRequestAfterA401IsASourceError() throws InitializationException {
    try (ApiEndpoint token =
            tokenEndpoint(
                token("at-1", EXPIRES_IN), Response.status(401, "{\"error\":\"invalid_client\"}"));
        ApiEndpoint api =
            ApiEndpoint.answeringInTurnOverTls(
                tls.server(), Response.json(EMPTY), Response.status(401))) {
      TestRunner runner = oauth2Runner(api, token);

      runner.run(1, false, true);
      runner.clearTransferState();
      runner.run(1, true, false);

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          401,
          "API call \"Stations\": OAuth2 token request to "
              + tokenEndpoint(token)
              + " failed with status 401 (invalid_client)");
      assertEquals(2, token.requests().size());
      assertEquals(2, api.requests().size());
    }
  }

  @Test
  void a401WithAFreshTokenIsFatalWithoutANewToken() throws InitializationException {
    try (ApiEndpoint token = tokenEndpoint(token("at-1", EXPIRES_IN), token("at-2", EXPIRES_IN));
        ApiEndpoint api =
            ApiEndpoint.answeringInTurnOverTls(
                tls.server(), Response.status(401), Response.json(EMPTY))) {
      TestRunner runner = oauth2Runner(api, token);

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          401,
          "API call \"Stations\": GET /stations failed with status 401 (OAuth2)");
      assertEquals(1, token.requests().size());
      assertEquals(1, api.requests().size());
    }
  }

  @Test
  void aRestartDropsTheKeptToken() throws InitializationException {
    try (ApiEndpoint token = tokenEndpoint(token("at-1", EXPIRES_IN), token("at-2", EXPIRES_IN));
        ApiEndpoint api = ApiEndpoint.answeringOverTls(tls.server(), 200, EMPTY)) {
      TestRunner runner = oauth2Runner(api, token);

      runner.run();
      runner.run();

      assertEquals(2, token.requests().size());
      assertEquals(List.of("Bearer at-1", "Bearer at-2"), authorizations(api));
    }
  }

  @Test
  void aRefusedClientCertificateInTheHandshakeIsFatal()
      throws IOException, InitializationException {
    try (ClosingTlsEndpoint api = ClosingTlsEndpoint.refusingClientCertificates(tls.server())) {
      TestRunner runner = clientCertificateRunner(api.baseUrl());

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.STOP,
          0,
          "API call \"Stations\": GET /stations failed: TLS handshake with Client certificate"
              + " failed (SSLHandshakeException)");
    }
  }

  @Test
  void anUntrustedServerCertificateStaysTransient() throws InitializationException {
    try (ApiEndpoint api = ApiEndpoint.answeringOverTls(tls.untrustedServer(), 200, EMPTY)) {
      TestRunner runner = clientCertificateRunner(api.baseUrl());

      runner.run();

      assertRouted(
          runner,
          GetHttpApiCall.FAILURE,
          0,
          "API call \"Stations\": GET /stations failed: SSLHandshakeException");
    }
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
    "Received fatal alert: handshake_failure, true",
    "Received fatal alert: bad_certificate, true",
    "Received fatal alert: unsupported_certificate, true",
    "Received fatal alert: certificate_revoked, true",
    "Received fatal alert: certificate_expired, true",
    "Received fatal alert: certificate_unknown, true",
    "Received fatal alert: unknown_ca, true",
    "Received fatal alert: access_denied, true",
    "Received fatal alert: certificate_required, true",
    "Received fatal alert: protocol_version, false",
    "PKIX path building failed, false"
  })
  void onlyAServerAlertThatRefusesTheCertificateIsFatal(String message, boolean fatal) {
    IOException cause = new IOException(new SSLHandshakeException(message));

    assertEquals(
        fatal,
        new ClientCertificateAuthentication().transportFailure("GET /stations", cause).isPresent());
  }

  @Test
  void aClosedConnectionAfterTheHandshakeStaysTransient()
      throws IOException, InitializationException {
    try (ClosingTlsEndpoint api = ClosingTlsEndpoint.start(tls.server())) {
      TestRunner runner = clientCertificateRunner(api.baseUrl());

      runner.run();

      runner.assertAllFlowFilesTransferred(GetHttpApiCall.FAILURE, 1);
      MockFlowFile failed = runner.getFlowFilesForRelationship(GetHttpApiCall.FAILURE).get(0);
      failed.assertAttributeEquals(GetHttpApiCall.ERROR_STATUS, "0");
      String message = failed.getAttribute(GetHttpApiCall.ERROR_MESSAGE);
      assertTrue(message.startsWith("API call \"Stations\": GET /stations failed: "), message);
      assertFalse(message.contains("TLS handshake"), message);
    }
  }

  @ParameterizedTest(name = "{0} {1}, API {2}, token endpoint {3}")
  @CsvSource({
    "BASIC, HEADER, 401, 200",
    "BEARER_TOKEN, HEADER, 401, 200",
    "API_TOKEN, HEADER, 401, 200",
    "API_TOKEN, QUERY, 401, 200",
    "OAUTH2, HEADER, 401, 200",
    "OAUTH2, HEADER, 200, 401"
  })
  void keepsEachSecretOutOfLogsBulletinsAttributesAndCauses(
      AuthenticationType type, ParameterLocation location, int apiStatus, int tokenStatus)
      throws InitializationException {
    Secrets secrets = Secrets.unique();
    Response tokenResponse =
        tokenStatus == 200
            ? token(secrets.accessToken(), EXPIRES_IN)
            : Response.status(
                tokenStatus,
                "{\"error\":\"invalid_client\",\"error_description\":\""
                    + secrets.clientSecret()
                    + secrets.accessToken()
                    + "\"}");
    try (ApiEndpoint token = tokenEndpoint(tokenResponse);
        ApiEndpoint api = api(type, Response.status(apiStatus, EMPTY))) {
      TestRunner runner = authenticatedRunner(type, location, api, token, secrets);
      runner.setProperty(
          GetHttpApiCall.API_CALL,
          "{\"name\":\"Stations\",\"path\":\"/stations\",\"queryParameters\":"
              + "[{\"name\":\"key\",\"value\":\""
              + secrets.queryValue()
              + "\"}]}");

      runner.run();

      runner.assertTransferCount(GetHttpApiCall.STOP, 1);
      for (String text : visible(runner)) {
        for (String secret : secrets.all()) {
          assertFalse(text.contains(secret), text);
        }
      }
    }
  }

  @ParameterizedTest(name = "validate_with{0}Without{1}_isInvalid")
  @CsvSource({
    "BEARER_TOKEN, Bearer Token",
    "API_TOKEN, API Token Name",
    "API_TOKEN, API Token Value",
    "API_TOKEN, API Token Location",
    "OAUTH2, OAuth2 Token URL",
    "OAUTH2, OAuth2 Client ID",
    "OAUTH2, OAuth2 Client Secret"
  })
  void validate_withAMethodWithoutARequiredField_isInvalid(AuthenticationType type, String field)
      throws InitializationException {
    TestRunner runner = runner(HTTPS_API);
    configureValid(runner, type);
    runner.assertValid();
    runner.removeProperty(field);

    Collection<ValidationResult> results = invalid(runner);

    assertTrue(results.stream().anyMatch(r -> field.equals(r.getSubject())), results::toString);
  }

  @ParameterizedTest
  @EnumSource(AuthenticationType.class)
  void validate_withEachMethodAndItsFields_isValid(AuthenticationType type)
      throws InitializationException {
    TestRunner runner = runner(HTTPS_API);
    configureValid(runner, type);

    runner.assertValid();
  }

  @Test
  void validate_withOAuth2WithoutAScope_isValid() throws InitializationException {
    TestRunner runner = runner(HTTPS_API);
    configureValid(runner, AuthenticationType.OAUTH2);
    runner.removeProperty(GetHttpApiCall.OAUTH2_SCOPE);

    runner.assertValid();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "http://auth.example.org/t-5a1e                 | t-5a1e",
        "ftp://auth.example.org/t-5a1e                  | t-5a1e",
        "https://u:pw-81c3@auth.example.org/token       | pw-81c3",
        "https://auth.example.org/token?k=q-2b7d        | q-2b7d",
        "https://auth.example.org/token#f-6e0a          | f-6e0a",
        "https:///t-9d4f                                | t-9d4f",
        "https://auth example.org/t-1f3b                | t-1f3b"
      })
  void validate_withATokenUrlThatBreaksARule_isInvalidWithoutNamingIt(String url, String secret)
      throws InitializationException {
    TestRunner runner = runner(HTTPS_API);
    configureValid(runner, AuthenticationType.OAUTH2);
    runner.setProperty(GetHttpApiCall.OAUTH2_TOKEN_URL, url);

    Collection<ValidationResult> results = invalid(runner);

    assertTrue(
        results.stream().anyMatch(r -> "OAuth2 Token URL".equals(r.getSubject())),
        results::toString);
    assertFalse(results.toString().contains(secret), results::toString);
  }

  @ParameterizedTest
  @CsvSource({"QUERY, key", "HEADER, x-api-key", "QUERY, page", "QUERY, size"})
  void validate_withAnApiTokenNameThatCollides_isInvalid(ParameterLocation location, String name)
      throws InitializationException {
    TestRunner runner = runner(HTTPS_API);
    runner.setProperty(GetHttpApiCall.API_CALL, collidingApiCall());
    apiToken(runner, name, "secret", location);

    Collection<ValidationResult> results = invalid(runner);

    assertEquals(1, results.size(), results::toString);
    assertEquals("API Token Name", results.iterator().next().getSubject());
  }

  @ParameterizedTest
  @CsvSource({"QUERY, Key", "QUERY, X-Api-Key", "HEADER, key", "HEADER, page"})
  void validate_withAnApiTokenNameThatDiffersInCaseOrPlace_isValid(
      ParameterLocation location, String name) throws InitializationException {
    TestRunner runner = runner(HTTPS_API);
    runner.setProperty(GetHttpApiCall.API_CALL, collidingApiCall());
    apiToken(runner, name, "secret", location);

    runner.assertValid();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Host", "content-length", "X Api Key"})
  void validate_withAnApiTokenHeaderTheClientSets_isInvalid(String name)
      throws InitializationException {
    TestRunner runner = runner(HTTPS_API);
    apiToken(runner, name, "secret", ParameterLocation.HEADER);

    Collection<ValidationResult> results = invalid(runner);

    assertTrue(
        results.stream().anyMatch(r -> "API Token Name".equals(r.getSubject())), results::toString);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Authorization", "Proxy-Authorization", "Cookie"})
  void validate_withAnApiTokenNamedAuthorization_isValid(String name)
      throws InitializationException {
    TestRunner runner = runner(HTTPS_API);
    apiToken(runner, name, "secret", ParameterLocation.HEADER);

    runner.assertValid();
  }

  @Test
  void validate_withALineBreakInABearerToken_isInvalidWithoutNamingIt()
      throws InitializationException {
    TestRunner runner = runner(HTTPS_API);
    runner.setProperty(GetHttpApiCall.AUTHENTICATION_TYPE, "BEARER_TOKEN");
    runner.setProperty(GetHttpApiCall.BEARER_TOKEN, "bt-7c1e\r\nX-Injected: 1");

    Collection<ValidationResult> results = invalid(runner);

    assertTrue(
        results.stream().anyMatch(r -> "Bearer Token".equals(r.getSubject())), results::toString);
    assertFalse(results.toString().contains("bt-7c1e"), results::toString);
  }

  @Test
  void validate_withALineBreakInAnApiTokenHeader_isInvalidWithoutNamingIt()
      throws InitializationException {
    TestRunner runner = runner(HTTPS_API);
    apiToken(runner, "X-Api-Key", "at-3e8b\r\nX-Injected: 1", ParameterLocation.HEADER);

    Collection<ValidationResult> results = invalid(runner);

    assertTrue(
        results.stream().anyMatch(r -> "API Token Value".equals(r.getSubject())),
        results::toString);
    assertFalse(results.toString().contains("at-3e8b"), results::toString);
  }

  @Test
  void validate_withALineBreakInAnApiTokenInTheQuery_isValid() throws InitializationException {
    TestRunner runner = runner(HTTPS_API);
    apiToken(runner, "key", "at-3e8b\r\n", ParameterLocation.QUERY);

    runner.assertValid();
  }

  @ParameterizedTest
  @EnumSource(
      value = AuthenticationType.class,
      names = {"BEARER_TOKEN", "API_TOKEN", "OAUTH2", "CLIENT_CERTIFICATE"})
  void validate_withAMethodOverHttp_isInvalid(AuthenticationType type)
      throws InitializationException {
    TestRunner runner = runner("http://api.example.org/v1");
    configureValid(runner, type);

    Collection<ValidationResult> results = invalid(runner);

    assertTrue(
        results.stream().anyMatch(r -> "Base URL".equals(r.getSubject())), results::toString);
  }

  @Test
  void secretProperties_areSensitive() {
    for (PropertyDescriptor secret :
        List.of(
            GetHttpApiCall.BASIC_AUTH_PASSWORD,
            GetHttpApiCall.BEARER_TOKEN,
            GetHttpApiCall.API_TOKEN_VALUE,
            GetHttpApiCall.OAUTH2_CLIENT_SECRET)) {
      assertTrue(secret.isSensitive(), secret.getName());
    }
  }

  private TestRunner runner(String baseUrl) throws InitializationException {
    return GetHttpApiCallRunners.runner(new GetHttpApiCall(clock), baseUrl, tls.clientProvider());
  }

  private TestRunner clientCertificateRunner(String baseUrl) throws InitializationException {
    TestRunner runner =
        GetHttpApiCallRunners.runner(
            new GetHttpApiCall(clock), baseUrl, tls.clientCertificateProvider());
    runner.setProperty(GetHttpApiCall.AUTHENTICATION_TYPE, "CLIENT_CERTIFICATE");
    return runner;
  }

  private TestRunner oauth2Runner(ApiEndpoint api, ApiEndpoint token)
      throws InitializationException {
    TestRunner runner = runner(api.baseUrl());
    oauth2(runner, tokenUrl(token), CLIENT_SECRET);
    return runner;
  }

  /**
   * A runner of the method with the secrets, against the API and, for OAuth2, the token endpoint.
   */
  private TestRunner authenticatedRunner(
      AuthenticationType type,
      ParameterLocation location,
      ApiEndpoint api,
      ApiEndpoint token,
      Secrets secrets)
      throws InitializationException {
    if (type == AuthenticationType.CLIENT_CERTIFICATE) {
      return clientCertificateRunner(api.baseUrl());
    }
    TestRunner runner = runner(api.baseUrl());
    runner.setProperty(GetHttpApiCall.AUTHENTICATION_TYPE, type.name());
    switch (type) {
      case BASIC -> {
        runner.setProperty(GetHttpApiCall.BASIC_AUTH_USERNAME, secrets.username());
        runner.setProperty(GetHttpApiCall.BASIC_AUTH_PASSWORD, secrets.password());
      }
      case BEARER_TOKEN -> runner.setProperty(GetHttpApiCall.BEARER_TOKEN, secrets.bearerToken());
      case API_TOKEN -> apiToken(runner, "X-Api-Key", secrets.apiToken(), location);
      case OAUTH2 -> oauth2(runner, tokenUrl(token), secrets.clientSecret());
      default -> throw new IllegalArgumentException(type.name());
    }
    return runner;
  }

  /** The API of the method: for a client certificate, one that needs it in a TLS 1.3 handshake. */
  private static ApiEndpoint api(AuthenticationType type, Response response) {
    return type == AuthenticationType.CLIENT_CERTIFICATE
        ? ApiEndpoint.answeringOverMutualTls(tls.mutualServer(), "TLSv1.3", response)
        : ApiEndpoint.answeringInTurnOverTls(tls.server(), response);
  }

  private static void configureValid(TestRunner runner, AuthenticationType type) {
    runner.setProperty(GetHttpApiCall.AUTHENTICATION_TYPE, type.name());
    switch (type) {
      case NONE, CLIENT_CERTIFICATE -> {}
      case BASIC -> {
        runner.setProperty(GetHttpApiCall.BASIC_AUTH_USERNAME, "weather");
        runner.setProperty(GetHttpApiCall.BASIC_AUTH_PASSWORD, "secret");
      }
      case BEARER_TOKEN -> runner.setProperty(GetHttpApiCall.BEARER_TOKEN, "secret");
      case API_TOKEN -> apiToken(runner, "X-Api-Key", "secret", ParameterLocation.HEADER);
      case OAUTH2 -> {
        oauth2(runner, "https://auth.example.org/realms/city/token", "secret");
        runner.setProperty(GetHttpApiCall.OAUTH2_SCOPE, "read");
      }
    }
  }

  private static void apiToken(
      TestRunner runner, String name, String value, ParameterLocation location) {
    runner.setProperty(GetHttpApiCall.AUTHENTICATION_TYPE, "API_TOKEN");
    runner.setProperty(GetHttpApiCall.API_TOKEN_NAME, name);
    runner.setProperty(GetHttpApiCall.API_TOKEN_VALUE, value);
    runner.setProperty(GetHttpApiCall.API_TOKEN_LOCATION, location.name());
  }

  private static void oauth2(TestRunner runner, String tokenUrl, String clientSecret) {
    runner.setProperty(GetHttpApiCall.AUTHENTICATION_TYPE, "OAUTH2");
    runner.setProperty(GetHttpApiCall.OAUTH2_TOKEN_URL, tokenUrl);
    runner.setProperty(GetHttpApiCall.OAUTH2_CLIENT_ID, CLIENT_ID);
    runner.setProperty(GetHttpApiCall.OAUTH2_CLIENT_SECRET, clientSecret);
  }

  /** An API call with a query parameter {@code key}, a header and a page-number pagination. */
  private static String collidingApiCall() {
    return "{\"name\": \"Stations\", \"path\": \"/stations\", \"queryParameters\": [{\"name\":"
        + " \"key\", \"value\": \"v\"}], \"headers\": [{\"name\": \"X-Api-Key\", \"value\":"
        + " \"v\"}], \"pagination\": {\"type\": \"PAGE_NUMBER\", \"pageParameter\": \"page\","
        + " \"pageSizeParameter\": \"size\", \"pageSize\": 2}}";
  }

  private static ApiEndpoint tokenEndpoint(Response... responses) {
    return ApiEndpoint.answeringInTurnOverTls(tls.server(), responses);
  }

  private static String tokenUrl(ApiEndpoint token) {
    return token.baseUrl() + "/token";
  }

  /** What a cause names of the token URL: the host, the port and the path. */
  private static String tokenEndpoint(ApiEndpoint token) {
    return tokenUrl(token).substring("https://".length());
  }

  private static Response token(String accessToken, Duration expiresIn) {
    return Response.json(
        "{\"access_token\":\""
            + accessToken
            + "\",\"token_type\":\"Bearer\",\"expires_in\":"
            + expiresIn.toSeconds()
            + "}");
  }

  private static void assertMissingAccessToken(TestRunner runner, ApiEndpoint token) {
    assertRouted(
        runner,
        GetHttpApiCall.STOP,
        200,
        "API call \"Stations\": OAuth2 token request to "
            + tokenEndpoint(token)
            + " failed: status 200 without an access token in a JSON object");
  }

  private static List<String> authorizations(ApiEndpoint api) {
    return api.requests().stream()
        .map(request -> request.headers().getFirst("Authorization"))
        .toList();
  }

  private static String base64(String text) {
    return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
  }

  /** Secrets that occur nowhere else, so that a test finds each of them wherever it shows. */
  private record Secrets(
      String username,
      String password,
      String bearerToken,
      String apiToken,
      String clientSecret,
      String accessToken,
      String queryValue) {

    static Secrets unique() {
      return new Secrets(
          unique("user"),
          unique("password"),
          unique("bearer"),
          unique("apitoken"),
          unique("clientsecret"),
          unique("accesstoken"),
          unique("query"));
    }

    private static String unique(String kind) {
      return kind + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    List<String> all() {
      return List.of(
          username, password, bearerToken, apiToken, clientSecret, accessToken, queryValue);
    }
  }

  private static final class MutableClock implements InstantSource {

    private Instant now = Instant.parse("2026-10-28T08:00:00Z");

    @Override
    public Instant instant() {
      return now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }
  }
}
