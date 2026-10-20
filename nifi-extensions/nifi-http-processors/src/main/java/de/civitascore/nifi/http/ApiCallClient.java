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

import de.civitascore.nifi.http.ApiCall.Parameter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.apache.nifi.web.client.api.HttpRequestHeadersSpec;
import org.apache.nifi.web.client.api.HttpResponseEntity;
import org.apache.nifi.web.client.api.WebClientService;
import org.apache.nifi.web.client.api.WebClientServiceException;

/**
 * Sends one request of an API call, reads the body, checks it against the record schema and
 * classifies what came back. It knows no NiFi session, so that a page loop can send each page
 * through it as a request of its own.
 *
 * <p>The transport belongs to the web client service: TLS, the proxy, the timeouts and the redirect
 * handling are its properties. This class adds only the idle timeout of the body read.
 */
final class ApiCallClient {

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  private final WebClientService webClient;
  private final String baseUrl;
  private final Duration bodyReadTimeout;
  private final RecordSchema recordSchema;

  /**
   * @param baseUrl scheme, host, optional port and optional path; a trailing {@code /} is removed
   * @param bodyReadTimeout the longest wait of one read from the body
   */
  ApiCallClient(
      WebClientService webClient,
      String baseUrl,
      Duration bodyReadTimeout,
      RecordSchema recordSchema) {
    this.webClient = webClient;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.bodyReadTimeout = bodyReadTimeout;
    this.recordSchema = recordSchema;
  }

  /** Sends the request and reads the whole response. */
  ApiCallOutcome send(ApiCall call, Authentication authentication) {
    String request = call.describe();
    HttpResponseEntity response;
    try {
      HttpRequestHeadersSpec spec = webClient.method(call.method().requestMethod()).uri(uri(call));
      for (Parameter header : call.headers()) {
        spec = spec.header(header.name(), header.value());
      }
      response = authentication.authenticate(spec).retrieve();
    } catch (WebClientServiceException e) {
      return ApiCallOutcome.Failure.ofTransport(request, e);
    } catch (IllegalArgumentException e) {
      // The last guard: the JDK HTTP client puts the header value into the exception message.
      return ApiCallOutcome.Failure.ofRejectedRequest(request, e);
    }
    return classify(call, response);
  }

  private ApiCallOutcome classify(ApiCall call, HttpResponseEntity response) {
    int status = response.statusCode();
    Optional<ErrorKind> error = StatusClassification.errorKind(status);
    if (error.isPresent()) {
      discard(response);
      return ApiCallOutcome.Failure.ofStatus(call.describe(), status, error.get());
    }
    byte[] content;
    try {
      InputStream body = guarded(response);
      content = body == null ? new byte[0] : body.readAllBytes();
    } catch (IOException e) {
      return ApiCallOutcome.Failure.ofTransport(call.describe(), e);
    } finally {
      discard(response);
    }
    if (content.length == 0) {
      return new ApiCallOutcome.NoBody(status);
    }
    Optional<String> rejection = recordSchema.rejection(content);
    if (rejection.isPresent()) {
      return ApiCallOutcome.Failure.ofRejectedBody(call.label(), status, rejection.get());
    }
    return new ApiCallOutcome.Body(status, new ByteArrayInputStream(content));
  }

  private InputStream guarded(HttpResponseEntity response) {
    InputStream body = response.body();
    return body == null
        ? null
        : new IdleTimeoutInputStream(body, bodyReadTimeout, () -> discard(response));
  }

  static void discard(HttpResponseEntity response) {
    try {
      response.close();
    } catch (IOException ignored) {
      // The outcome is decided before the response closes; a failed close cannot change it.
    }
  }

  private URI uri(ApiCall call) {
    String query = query(call.queryParameters());
    return URI.create(baseUrl + call.path() + (query.isEmpty() ? "" : "?" + query));
  }

  /**
   * Joins the parameters in their order. {@code HttpUriBuilder.addQueryParameter} is not used: the
   * standard implementation does not encode {@code &}, {@code =} and {@code +} in a value.
   */
  private static String query(List<Parameter> parameters) {
    StringBuilder query = new StringBuilder();
    for (Parameter parameter : parameters) {
      if (!query.isEmpty()) {
        query.append('&');
      }
      encode(parameter.name(), query);
      query.append('=');
      encode(parameter.value(), query);
    }
    return query.toString();
  }

  /** Percent-encodes all but the unreserved characters of RFC 3986, so a space is {@code %20}. */
  private static void encode(String text, StringBuilder out) {
    for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
      int c = b & 0xFF;
      if (unreserved(c)) {
        out.append((char) c);
      } else {
        out.append('%').append(HEX[c >> 4]).append(HEX[c & 0xF]);
      }
    }
  }

  private static boolean unreserved(int c) {
    return (c >= 'A' && c <= 'Z')
        || (c >= 'a' && c <= 'z')
        || (c >= '0' && c <= '9')
        || c == '-'
        || c == '.'
        || c == '_'
        || c == '~';
  }
}
