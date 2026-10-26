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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

/**
 * The token request and response of the OAuth 2.0 client credentials grant (RFC 6749 §4.4), with
 * the client authenticated by HTTP Basic (§2.3.1).
 */
final class ClientCredentialsGrant {

  /** The start of the token response that is read; a token response is far smaller. */
  static final int MAX_RESPONSE_BYTES = 64 * 1024;

  /** The error codes of RFC 6749 §5.2, the only text of the token endpoint a message repeats. */
  private static final Set<String> ERROR_CODES =
      Set.of(
          "invalid_request",
          "invalid_client",
          "invalid_grant",
          "unauthorized_client",
          "unsupported_grant_type",
          "invalid_scope");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ClientCredentialsGrant() {}

  /**
   * The token URL.
   *
   * @throws ConnectionTestFailure if it is not an https URL with a host and without credentials
   */
  static URI tokenUri(String tokenUrl) throws ConnectionTestFailure {
    URI uri;
    try {
      uri = URI.create(tokenUrl);
    } catch (IllegalArgumentException e) {
      throw ConnectionTestFailure.invalid("The token URL is not a valid URL.", e);
    }
    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
      throw ConnectionTestFailure.invalid("The token URL must be an https URL with a host.");
    }
    if (uri.getRawUserInfo() != null) {
      throw ConnectionTestFailure.invalid("The token URL must not contain credentials.");
    }
    return uri;
  }

  /**
   * The token request.
   *
   * @param scope the requested scope, or blank for none
   */
  static HttpRequest request(
      URI tokenUri, String clientId, String clientSecret, String scope, Duration timeout) {
    StringBuilder form = new StringBuilder("grant_type=client_credentials");
    if (!scope.isBlank()) {
      form.append("&scope=");
      HttpProber.encode(scope, form);
    }
    StringBuilder client = new StringBuilder();
    HttpProber.encode(clientId, client);
    client.append(':');
    HttpProber.encode(clientSecret, client);
    String basic =
        Base64.getEncoder().encodeToString(client.toString().getBytes(StandardCharsets.UTF_8));
    return HttpRequest.newBuilder(tokenUri)
        .POST(HttpRequest.BodyPublishers.ofString(form.toString(), StandardCharsets.UTF_8))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("Accept", "application/json")
        .header("Authorization", "Basic " + basic)
        .timeout(timeout)
        .build();
  }

  /**
   * The access token of a token response.
   *
   * @param body the start of the response body
   * @throws ConnectionTestFailure if the token endpoint refused the request or sent no token
   */
  static String accessToken(int status, byte[] body) throws ConnectionTestFailure {
    JsonNode json = parse(body);
    if (status < 200 || status >= 300) {
      String error = text(json, "error");
      throw new ConnectionTestFailure(
          "The token endpoint answered with HTTP "
              + status
              + (ERROR_CODES.contains(error) ? " (" + error + ")" : "")
              + ", so the API was not called.");
    }
    String token = text(json, "access_token");
    if (token.isBlank()) {
      throw new ConnectionTestFailure(
          "The token endpoint answered with HTTP "
              + status
              + ", but without an access token in a JSON object.");
    }
    return token;
  }

  private static String text(JsonNode json, String member) {
    JsonNode value = json.path(member);
    return value.isTextual() ? value.textValue() : "";
  }

  private static JsonNode parse(byte[] body) {
    try {
      JsonNode json = MAPPER.readTree(body);
      return json == null ? MAPPER.missingNode() : json;
    } catch (IOException e) {
      return MAPPER.missingNode();
    }
  }
}
