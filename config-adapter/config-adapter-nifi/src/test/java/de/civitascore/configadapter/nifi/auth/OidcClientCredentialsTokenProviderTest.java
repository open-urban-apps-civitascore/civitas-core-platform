/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.auth;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OidcClientCredentialsTokenProviderTest {

  private static final String TOKEN_PATH = "/realms/civitas/protocol/openid-connect/token";

  private WireMockServer server;
  private Client httpClient;
  private MutableClock clock;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    server.start();
    httpClient = ClientBuilder.newClient();
    clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
  }

  @AfterEach
  void tearDown() {
    httpClient.close();
    server.stop();
  }

  private OidcClientCredentialsTokenProvider provider(String scope) {
    return new OidcClientCredentialsTokenProvider(
        server.baseUrl() + TOKEN_PATH, "nifi", "top-secret", scope, httpClient, clock);
  }

  private void stubToken(String accessToken, int expiresIn) {
    server.stubFor(
        post(urlEqualTo(TOKEN_PATH))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"access_token\":\""
                            + accessToken
                            + "\",\"expires_in\":"
                            + expiresIn
                            + ",\"token_type\":\"Bearer\"}")));
  }

  @Test
  void getTokenPostsClientCredentialsGrantAndReturnsAccessToken() throws Exception {
    stubToken("tok-1", 300);

    assertEquals("tok-1", provider(null).getToken());

    server.verify(
        postRequestedFor(urlEqualTo(TOKEN_PATH))
            .withRequestBody(containing("grant_type=client_credentials"))
            .withRequestBody(containing("client_id=nifi"))
            .withRequestBody(containing("client_secret=top-secret")));
  }

  @Test
  void cachesTokenUntilExpiry() throws Exception {
    stubToken("tok-1", 300);
    OidcClientCredentialsTokenProvider provider = provider(null);

    provider.getToken();
    clock.advance(Duration.ofSeconds(100)); // still well within the 300s lifetime
    provider.getToken();

    server.verify(1, postRequestedFor(urlEqualTo(TOKEN_PATH)));
  }

  @Test
  void refetchesAfterTokenExpires() throws Exception {
    // 300s lifetime, skew 30s ⇒ cached ~270s. Advancing past that re-fetches.
    server.stubFor(
        post(urlEqualTo(TOKEN_PATH))
            .inScenario("expiry")
            .whenScenarioStateIs("Started")
            .willReturn(tokenResponse("tok-1", 300))
            .willSetStateTo("second"));
    server.stubFor(
        post(urlEqualTo(TOKEN_PATH))
            .inScenario("expiry")
            .whenScenarioStateIs("second")
            .willReturn(tokenResponse("tok-2", 300)));
    OidcClientCredentialsTokenProvider provider = provider(null);

    assertEquals("tok-1", provider.getToken());
    clock.advance(Duration.ofSeconds(280)); // past the ~270s cache window
    assertEquals("tok-2", provider.getToken());

    server.verify(2, postRequestedFor(urlEqualTo(TOKEN_PATH)));
  }

  @Test
  void refreshTokenForcesNewFetchEvenWhenCachedTokenStillValid() throws Exception {
    server.stubFor(
        post(urlEqualTo(TOKEN_PATH))
            .inScenario("refresh")
            .whenScenarioStateIs("Started")
            .willReturn(tokenResponse("tok-1", 300))
            .willSetStateTo("second"));
    server.stubFor(
        post(urlEqualTo(TOKEN_PATH))
            .inScenario("refresh")
            .whenScenarioStateIs("second")
            .willReturn(tokenResponse("tok-2", 300)));
    OidcClientCredentialsTokenProvider provider = provider(null);

    assertEquals("tok-1", provider.getToken());
    assertEquals("tok-2", provider.refreshToken());

    server.verify(2, postRequestedFor(urlEqualTo(TOKEN_PATH)));
  }

  @Test
  void serverErrorIsRetryable() {
    server.stubFor(
        post(urlEqualTo(TOKEN_PATH)).willReturn(aResponse().withStatus(503).withBody("overloaded")));

    assertThrows(RetryableAdapterException.class, () -> provider(null).getToken());
  }

  @Test
  void rejectedClientCredentialsAreFatal() {
    // 401 invalid_client is a configuration error (wrong secret) — retrying can never fix it.
    server.stubFor(
        post(urlEqualTo(TOKEN_PATH))
            .willReturn(aResponse().withStatus(401).withBody("{\"error\":\"invalid_client\"}")));

    assertThrows(FatalAdapterException.class, () -> provider(null).getToken());
  }

  @Test
  void responseWithoutAccessTokenIsFatal() {
    server.stubFor(
        post(urlEqualTo(TOKEN_PATH))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"token_type\":\"Bearer\"}")));

    assertThrows(FatalAdapterException.class, () -> provider(null).getToken());
  }

  @Test
  void includesScopeParameterOnlyWhenConfigured() throws Exception {
    stubToken("tok-1", 300);

    provider("nifi-api").getToken();
    server.verify(
        postRequestedFor(urlEqualTo(TOKEN_PATH)).withRequestBody(containing("scope=nifi-api")));

    server.resetRequests();
    provider(null).getToken();
    server.verify(
        postRequestedFor(urlEqualTo(TOKEN_PATH)).withRequestBody(notContaining("scope=")));
  }

  @Test
  void requestTimeoutIsRetryable() {
    // 408 Request Timeout (e.g. from a proxy in front of Keycloak) is transient, not a config error.
    server.stubFor(post(urlEqualTo(TOKEN_PATH)).willReturn(aResponse().withStatus(408)));

    assertThrows(RetryableAdapterException.class, () -> provider(null).getToken());
  }

  @Test
  void emptyOkBodyIsFatal() {
    // A 200 with no body must surface as a fatal auth error, not an uncategorised NullPointerException.
    server.stubFor(post(urlEqualTo(TOKEN_PATH)).willReturn(aResponse().withStatus(200)));

    assertThrows(FatalAdapterException.class, () -> provider(null).getToken());
  }

  @Test
  void shortLivedTokenIsStillCachedBriefly() throws Exception {
    // Lifetime 10s < 2×REFRESH_SKEW: the skew is capped at half the lifetime (5s) so the token is
    // cached ~5s rather than being treated as already-expired and refetched on every call.
    stubToken("tok-1", 10);
    OidcClientCredentialsTokenProvider provider = provider(null);

    provider.getToken();
    clock.advance(Duration.ofSeconds(4)); // still within the ~5s cache window
    provider.getToken();

    server.verify(1, postRequestedFor(urlEqualTo(TOKEN_PATH)));
  }

  private static ResponseDefinitionBuilder tokenResponse(String accessToken, int expiresIn) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(
            "{\"access_token\":\""
                + accessToken
                + "\",\"expires_in\":"
                + expiresIn
                + ",\"token_type\":\"Bearer\"}");
  }

  /** A hand-advanced {@link InstantSource} so token-expiry behaviour is deterministic in tests. */
  private static final class MutableClock implements InstantSource {
    private Instant now;

    MutableClock(Instant start) {
      this.now = start;
    }

    void advance(Duration by) {
      now = now.plus(by);
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
