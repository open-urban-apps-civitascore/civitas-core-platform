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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Obtains NiFi bearer tokens from an OpenID Connect provider (Keycloak) using the OAuth 2.0
 * client-credentials grant. NiFi 2.x accepts these provider-issued access tokens on its REST API
 * and validates them against the JSON Web Keys published by the same provider, so the
 * config-adapter authenticates as an OIDC service account rather than with NiFi's single-user
 * login.
 *
 * <p>The token is cached until shortly before it expires (a fixed {@link #REFRESH_SKEW} margin
 * absorbs clock drift and in-flight requests). All access goes through {@code synchronized}
 * methods: concurrent deploys share one client instance, and a race could otherwise fire redundant
 * token requests or publish a half-updated token.
 */
public class OidcClientCredentialsTokenProvider implements NifiTokenProvider {

  private static final Logger LOG =
      LoggerFactory.getLogger(OidcClientCredentialsTokenProvider.class);

  /** Renew this long before the provider's stated expiry, so an in-flight token never lapses. */
  private static final Duration REFRESH_SKEW = Duration.ofSeconds(30);

  private final String tokenUri;
  private final String clientId;
  private final String clientSecret;
  private final String scope;
  private final Client client;
  private final InstantSource clock;
  private final ObjectMapper mapper = new ObjectMapper();

  private String cachedToken;
  private Instant expiresAt = Instant.MIN;

  /**
   * Creates a provider.
   *
   * @param tokenUri the provider's token endpoint (e.g. {@code
   *     https://keycloak/realms/civitas/protocol/openid-connect/token})
   * @param clientId the OIDC client id of the config-adapter's service account
   * @param clientSecret the client secret
   * @param scope an optional space-delimited scope to request, or {@code null}/blank for none
   * @param client the JAX-RS client to use
   */
  public OidcClientCredentialsTokenProvider(
      String tokenUri, String clientId, String clientSecret, String scope, Client client) {
    this(tokenUri, clientId, clientSecret, scope, client, InstantSource.system());
  }

  /** Test seam: injects the clock that drives token-expiry decisions. */
  OidcClientCredentialsTokenProvider(
      String tokenUri,
      String clientId,
      String clientSecret,
      String scope,
      Client client,
      InstantSource clock) {
    this.tokenUri = tokenUri;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.scope = scope;
    this.client = client;
    this.clock = clock;
  }

  @Override
  public synchronized String getToken() throws FatalAdapterException, RetryableAdapterException {
    if (cachedToken != null && clock.instant().isBefore(expiresAt)) {
      return cachedToken;
    }
    return fetchAndCache();
  }

  @Override
  public synchronized String refreshToken()
      throws FatalAdapterException, RetryableAdapterException {
    return fetchAndCache();
  }

  private String fetchAndCache() throws FatalAdapterException, RetryableAdapterException {
    Form form =
        new Form()
            .param("grant_type", "client_credentials")
            .param("client_id", clientId)
            .param("client_secret", clientSecret);
    if (scope != null && !scope.isBlank()) {
      form.param("scope", scope);
    }
    try (Response response =
        client.target(tokenUri).request(MediaType.APPLICATION_JSON).post(Entity.form(form))) {
      TokenResponse parsed = parseTokenResponse(response);
      // Measure the lifetime from when the response was received, not when the request was sent.
      this.cachedToken = parsed.accessToken();
      this.expiresAt = clock.instant().plusSeconds(cacheableSeconds(parsed.expiresInSeconds()));
      LOG.debug("Obtained NiFi OIDC token from {}", Encode.forJava(tokenUri));
      return parsed.accessToken();
    } catch (ProcessingException e) {
      throw new RetryableAdapterException(
          AdapterErrorCode.NETWORK_ERROR, e, "nifi-oidc", "token request: " + e.getMessage());
    }
  }

  private TokenResponse parseTokenResponse(Response response)
      throws FatalAdapterException, RetryableAdapterException {
    int status = response.getStatus();
    String body = response.hasEntity() ? response.readEntity(String.class) : "";
    if (status < 200 || status >= 300) {
      // 5xx, 408 (request timeout) and 429 (rate limited) are transient; any other status — notably
      // 400 invalid_scope and 401 invalid_client — is a misconfiguration that retrying cannot fix.
      if (status >= 500 || status == 408 || status == 429) {
        throw new RetryableAdapterException(
            AdapterErrorCode.NIFI_ERROR, "OIDC token endpoint: HTTP " + status);
      }
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_AUTH_ERROR,
          "OIDC token endpoint rejected client-credentials request: HTTP " + status + " — " + body);
    }
    JsonNode json = parseJson(body);
    String token = json.path("access_token").asText(null);
    if (token == null || token.isBlank()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_AUTH_ERROR, "OIDC token endpoint returned no access_token");
    }
    // A missing expires_in defaults to 0, so the token is treated as already stale and refetched on
    // the next call rather than cached indefinitely with an unknown lifetime.
    return new TokenResponse(token, json.path("expires_in").asLong(0));
  }

  private JsonNode parseJson(String body) throws FatalAdapterException {
    try {
      JsonNode node = mapper.readTree(body);
      if (node == null || node.isMissingNode()) {
        // readTree yields null/MissingNode for an empty (200-but-no-body) response; surface it as a
        // fatal auth error rather than letting the later path(...) call NPE uncategorized.
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_AUTH_ERROR, "OIDC token endpoint returned an empty response");
      }
      return node;
    } catch (JsonProcessingException e) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_AUTH_ERROR, e, "parse OIDC token response");
    }
  }

  /**
   * Seconds the token may be cached: its stated lifetime minus the refresh skew, but the skew is
   * capped at half the lifetime so a very short-lived token (lifetime &le; {@link #REFRESH_SKEW})
   * is still cached briefly instead of forcing a blocking fetch on every request. A missing/zero
   * lifetime yields 0 (refetch next call).
   */
  private static long cacheableSeconds(long expiresInSeconds) {
    long skew = Math.min(REFRESH_SKEW.toSeconds(), expiresInSeconds / 2);
    return Math.max(0, expiresInSeconds - skew);
  }

  private record TokenResponse(String accessToken, long expiresInSeconds) {}
}
