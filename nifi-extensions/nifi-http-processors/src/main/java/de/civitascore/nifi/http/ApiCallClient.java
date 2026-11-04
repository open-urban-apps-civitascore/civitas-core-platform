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
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.nifi.web.client.api.HttpRequestHeadersSpec;
import org.apache.nifi.web.client.api.HttpResponseEntity;
import org.apache.nifi.web.client.api.WebClientService;
import org.apache.nifi.web.client.api.WebClientServiceException;

/**
 * Sends one request of an API call and classifies what came back. It knows no NiFi session, so that
 * a page loop can send each page through it as a request of its own.
 *
 * <p>The transport belongs to the web client service: TLS, the proxy, the timeouts and the redirect
 * handling are its properties. This class adds only the idle timeout of the body read.
 */
final class ApiCallClient {

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();
  private static final int UNAUTHORIZED = 401;
  private static final int FORBIDDEN = 403;

  private final WebClientService webClient;
  private final String baseUrl;
  private final Duration bodyReadTimeout;

  /**
   * @param baseUrl scheme, host, optional port and optional path; a trailing {@code /} is removed
   * @param bodyReadTimeout the longest wait of one read from the body
   */
  ApiCallClient(WebClientService webClient, String baseUrl, Duration bodyReadTimeout) {
    this.webClient = webClient;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.bodyReadTimeout = bodyReadTimeout;
  }

  /**
   * Sends the request of the page with the credentials of the authentication. After a 401 that the
   * authentication can renew, it sends the request once more.
   */
  ApiCallOutcome send(ApiCall call, Page page, Authentication authentication) {
    String request = call.describe(page);
    Attempt attempt = attempt(call, page, authentication, request);
    if (attempt.unauthorized() && authentication.renewAfterUnauthorized()) {
      attempt.discard();
      attempt = attempt(call, page, authentication, request);
    }
    return switch (attempt) {
      case Attempt.Failed(ApiCallOutcome.Failure failure) -> failure;
      case Attempt.Responded(HttpResponseEntity response) ->
          classify(request, response, authentication);
    };
  }

  private Attempt attempt(ApiCall call, Page page, Authentication authentication, String request) {
    Optional<ApiCallOutcome.Failure> notReady = authentication.prepare(call);
    if (notReady.isPresent()) {
      return new Attempt.Failed(notReady.get());
    }
    try {
      HttpRequestHeadersSpec spec =
          webClient.method(call.method().requestMethod()).uri(uri(call, page, authentication));
      for (Parameter header : call.headers()) {
        spec = spec.header(header.name(), header.value());
      }
      for (Parameter header : page.headers()) {
        spec = spec.header(header.name(), header.value());
      }
      return new Attempt.Responded(authentication.headers(spec).retrieve());
    } catch (WebClientServiceException e) {
      return new Attempt.Failed(
          authentication
              .transportFailure(request, e)
              .orElseGet(() -> ApiCallOutcome.Failure.ofTransport(request, e)));
    } catch (IllegalArgumentException e) {
      // The last guard: the JDK HTTP client puts the header value into the exception message.
      return new Attempt.Failed(ApiCallOutcome.Failure.ofRejectedRequest(request, e));
    }
  }

  private ApiCallOutcome classify(
      String request, HttpResponseEntity response, Authentication authentication) {
    int status = response.statusCode();
    Optional<ErrorKind> error = StatusClassification.errorKind(status);
    if (error.isPresent()) {
      discard(response);
      return status == UNAUTHORIZED || status == FORBIDDEN
          ? ApiCallOutcome.Failure.ofRefusedCredential(request, status, authentication.method())
          : ApiCallOutcome.Failure.ofStatus(request, status, error.get());
    }
    Optional<InputStream> content;
    try {
      content =
          nonEmpty(
              Optional.ofNullable(response.body())
                  .map(
                      body ->
                          new IdleTimeoutInputStream(
                              body, bodyReadTimeout, () -> discard(response)))
                  .orElse(null));
    } catch (IOException e) {
      discard(response);
      return ApiCallOutcome.Failure.ofTransport(request, e);
    }
    if (content.isEmpty()) {
      discard(response);
      return new ApiCallOutcome.NoBody(status);
    }
    return new ApiCallOutcome.Body(status, content.get(), response);
  }

  /** The body from its first byte, or empty when the first read gives the end of the stream. */
  private static Optional<InputStream> nonEmpty(InputStream body) throws IOException {
    if (body == null) {
      return Optional.empty();
    }
    PushbackInputStream content = new PushbackInputStream(body);
    int first = content.read();
    if (first == -1) {
      return Optional.empty();
    }
    content.unread(first);
    return Optional.of(content);
  }

  static void discard(HttpResponseEntity response) {
    try {
      response.close();
    } catch (IOException ignored) {
      // The outcome is decided before the response closes; a failed close cannot change it.
    }
  }

  URI target(ApiCall call, Page page) {
    String target;
    if (page.url().isPresent()) {
      target = page.url().get().toString();
    } else {
      List<Parameter> parameters = new ArrayList<>(call.queryParameters());
      parameters.addAll(page.queryParameters());
      String query = query(parameters);
      target = baseUrl + call.path() + (query.isEmpty() ? "" : "?" + query);
    }
    int fragment = target.indexOf('#');
    return URI.create(fragment < 0 ? target : target.substring(0, fragment));
  }

  private URI uri(ApiCall call, Page page, Authentication authentication) {
    URI target = target(call, page);
    String query = query(authentication.queryParameters());
    return URI.create(
        target + (query.isEmpty() ? "" : (target.getRawQuery() == null ? "?" : "&") + query));
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
  static void encode(String text, StringBuilder out) {
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

  /** One request: the response, or the failure that came instead. */
  private sealed interface Attempt {

    default boolean unauthorized() {
      return false;
    }

    default void discard() {}

    record Responded(HttpResponseEntity response) implements Attempt {

      @Override
      public boolean unauthorized() {
        return response.statusCode() == UNAUTHORIZED;
      }

      @Override
      public void discard() {
        ApiCallClient.discard(response);
      }
    }

    record Failed(ApiCallOutcome.Failure failure) implements Attempt {}
  }
}
