/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.OptionalLong;
import org.apache.nifi.web.client.api.HttpResponseEntity;
import org.apache.nifi.web.client.api.WebClientService;
import org.apache.nifi.web.client.api.WebClientServiceException;

/**
 * Sends one batch document and reads the answer.
 *
 * <p>The transport belongs to the web client service: TLS with its trust material, the proxy and
 * the timeouts are its properties, and this class adds none of them. There is no switch here that
 * turns certificate validation off.
 *
 * <p>The credential is held for the lifetime of one invocation and reaches the request header
 * alone. It is in no message this class raises.
 */
public final class FrostBatchClient {

  private static final String BATCH_PATH = "/$batch";

  private final WebClientService webClient;
  private final ObjectMapper mapper;
  private final URI batchUri;
  private final String authorization;

  public FrostBatchClient(
      WebClientService webClient,
      ObjectMapper mapper,
      String baseUrl,
      String username,
      String password) {
    this.webClient = webClient;
    this.mapper = mapper;
    this.batchUri = URI.create(trimTrailingSlash(baseUrl) + BATCH_PATH);
    this.authorization = basicAuthorization(username, password);
  }

  /** The endpoint the batch goes to, for a message that must name it. */
  public URI batchUri() {
    return batchUri;
  }

  /**
   * Sends the document.
   *
   * @throws IOException when the exchange did not complete, which the caller answers with a retry
   */
  public BatchExchange send(byte[] document) throws IOException {
    InputStream body = new ByteArrayInputStream(document);
    var request =
        webClient
            .post()
            .uri(batchUri)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json");
    if (authorization != null) {
      request = request.header("Authorization", authorization);
    }
    try (HttpResponseEntity response =
        request.body(body, OptionalLong.of(document.length)).retrieve()) {
      return new BatchExchange(response.statusCode(), read(response));
    } catch (WebClientServiceException e) {
      // The web client service wraps a transport defect in its own unchecked exception. It is a
      // retry, not a rejection of the data, so it leaves this class as an IOException.
      throw new IOException("the FROST batch request did not complete", e);
    }
  }

  private JsonNode read(HttpResponseEntity response) throws IOException {
    try (InputStream body = response.body()) {
      if (body == null) {
        return null;
      }
      byte[] raw = body.readAllBytes();
      return raw.length == 0 ? null : mapper.readTree(raw);
    } catch (IOException e) {
      // A body that is not JSON is the answer of something that is not a FROST batch endpoint, for
      // example a proxy error page. The status decides what happens next, so the caller sees the
      // exchange rather than an exception.
      return null;
    }
  }

  private static String basicAuthorization(String username, String password) {
    if (username == null || username.isBlank()) {
      return null;
    }
    String credential = username + ":" + (password == null ? "" : password);
    return "Basic "
        + Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
  }

  private static String trimTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  /**
   * The answer to a batch document.
   *
   * @param status the status of the batch request itself, not of a sub-request
   * @param body the parsed answer, or null when there was none to parse
   */
  public record BatchExchange(int status, JsonNode body) {

    public boolean successful() {
      return status >= 200 && status < 300;
    }

    /** Whether the status invites another attempt with the same data. */
    public boolean retryable() {
      return status >= 500 || status == 408 || status == 429;
    }
  }
}
