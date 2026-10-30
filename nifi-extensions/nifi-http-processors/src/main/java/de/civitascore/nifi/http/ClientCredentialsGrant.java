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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.apache.nifi.web.client.api.HttpResponseEntity;
import org.apache.nifi.web.client.api.WebClientService;
import org.apache.nifi.web.client.api.WebClientServiceException;

/**
 * The token request of the OAuth 2.0 client credentials grant (RFC 6749, section 4.4), with the
 * client authenticated by HTTP Basic (section 2.3.1). It goes through the web client service of the
 * API requests, so it has their TLS, proxy and timeouts, and follows no redirect: the client secret
 * reaches no host other than the one of the token URL.
 */
final class ClientCredentialsGrant {

  /** The start of the token response that is read; a token response is far smaller. */
  static final int MAX_RESPONSE_BYTES = 64 * 1024;

  /** The error codes of RFC 6749, section 5.2, the only text of the response a cause repeats. */
  private static final Set<String> ERROR_CODES =
      Set.of(
          "invalid_request",
          "invalid_client",
          "invalid_grant",
          "unauthorized_client",
          "unsupported_grant_type",
          "invalid_scope");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final WebClientService webClient;
  private final URI tokenUri;
  private final String tokenEndpoint;
  private final String form;
  private final String basicCredential;
  private final Duration bodyReadTimeout;

  /**
   * @param tokenUri an https URL without user info, query and fragment
   * @param scope the requested scope, or blank or null for none
   */
  ClientCredentialsGrant(
      WebClientService webClient,
      URI tokenUri,
      String clientId,
      String clientSecret,
      String scope,
      Duration bodyReadTimeout) {
    this.webClient = webClient;
    this.tokenUri = tokenUri;
    this.tokenEndpoint =
        tokenUri.getHost()
            + (tokenUri.getPort() == -1 ? "" : ":" + tokenUri.getPort())
            + tokenUri.getRawPath();
    StringBuilder body = new StringBuilder("grant_type=client_credentials");
    if (scope != null && !scope.isBlank()) {
      body.append("&scope=");
      ApiCallClient.encode(scope, body);
    }
    this.form = body.toString();
    StringBuilder client = new StringBuilder();
    ApiCallClient.encode(clientId, client);
    client.append(':');
    ApiCallClient.encode(clientSecret, client);
    this.basicCredential =
        Base64.getEncoder().encodeToString(client.toString().getBytes(StandardCharsets.UTF_8));
    this.bodyReadTimeout = bodyReadTimeout;
  }

  /**
   * Requests an access token.
   *
   * @param apiCall the label of the API call that needs the token, for the cause of a failure
   */
  Grant request(String apiCall) {
    HttpResponseEntity response;
    try {
      response =
          webClient
              .post()
              .uri(tokenUri)
              .body(form)
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Accept", "application/json")
              .header("Authorization", "Basic " + basicCredential)
              .retrieve();
    } catch (WebClientServiceException e) {
      return new Grant.Refused(ApiCallOutcome.Failure.ofTokenTransport(apiCall, tokenEndpoint, e));
    }
    try {
      return grant(apiCall, response);
    } catch (IOException e) {
      return new Grant.Refused(ApiCallOutcome.Failure.ofTokenTransport(apiCall, tokenEndpoint, e));
    } finally {
      ApiCallClient.discard(response);
    }
  }

  private Grant grant(String apiCall, HttpResponseEntity response) throws IOException {
    int status = response.statusCode();
    Optional<ErrorKind> error = StatusClassification.errorKind(status);
    if (error.isPresent()) {
      Optional<String> errorCode =
          Optional.of(text(errorBody(response), "error")).filter(ERROR_CODES::contains);
      return new Grant.Refused(
          ApiCallOutcome.Failure.ofTokenStatus(
              apiCall, tokenEndpoint, status, error.get(), errorCode));
    }
    JsonNode json = parse(head(response));
    String accessToken = text(json, "access_token");
    if (accessToken.isBlank()) {
      return new Grant.Refused(
          ApiCallOutcome.Failure.ofMissingAccessToken(apiCall, tokenEndpoint, status));
    }
    JsonNode expiresIn = json.path("expires_in");
    return new Grant.Issued(
        accessToken,
        expiresIn.isIntegralNumber() && expiresIn.canConvertToLong()
            ? OptionalLong.of(expiresIn.longValue())
            : OptionalLong.empty());
  }

  /** The body of an error status, which only adds an error code to the status. */
  private JsonNode errorBody(HttpResponseEntity response) {
    try {
      return parse(head(response));
    } catch (IOException e) {
      return MAPPER.missingNode();
    }
  }

  /** The first {@link #MAX_RESPONSE_BYTES} of the body. */
  private byte[] head(HttpResponseEntity response) throws IOException {
    try (InputStream body = response.body();
        InputStream guarded =
            body == null
                ? InputStream.nullInputStream()
                : new IdleTimeoutInputStream(
                    body, bodyReadTimeout, () -> ApiCallClient.discard(response))) {
      return guarded.readNBytes(MAX_RESPONSE_BYTES);
    }
  }

  private static String text(JsonNode json, String member) {
    JsonNode value = json.path(member);
    return value.isTextual() ? value.textValue() : "";
  }

  /** The JSON of the body, or a missing node when it is not JSON or is cut off. */
  private static JsonNode parse(byte[] body) {
    try {
      JsonNode json = MAPPER.readTree(body);
      return json == null ? MAPPER.missingNode() : json;
    } catch (IOException e) {
      return MAPPER.missingNode();
    }
  }

  /** What a token request gave. */
  sealed interface Grant {

    /** An access token. A class, not a record, so that no generated {@code toString} names it. */
    final class Issued implements Grant {

      private final String accessToken;
      private final OptionalLong expiresIn;

      Issued(String accessToken, OptionalLong expiresIn) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
      }

      String accessToken() {
        return accessToken;
      }

      /** The {@code expires_in} of the response in seconds, when it is a JSON integral number. */
      OptionalLong expiresIn() {
        return expiresIn;
      }
    }

    record Refused(ApiCallOutcome.Failure failure) implements Grant {}
  }
}
