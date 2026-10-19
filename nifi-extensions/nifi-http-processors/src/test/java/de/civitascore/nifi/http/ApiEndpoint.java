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

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.net.ssl.SSLContext;

/**
 * An HTTP API on the loopback interface that records each request and answers with one scripted
 * response.
 *
 * <p>The tests drive the processor's real HTTP path against this, rather than against a double of
 * the web client service: the request that arrives here is the request a NiFi installation sends.
 */
final class ApiEndpoint implements AutoCloseable {

  /** The request as it arrived, with the path and the query still percent-encoded. */
  record Request(String method, String rawPath, String rawQuery, Headers headers) {}

  private static final Map<String, String> JSON = Map.of("Content-Type", "application/json");

  private final HttpServer server;
  private final String scheme;
  private final List<Request> requests = new CopyOnWriteArrayList<>();

  private ApiEndpoint(HttpServer server, String scheme) {
    this.server = server;
    this.scheme = scheme;
  }

  /** Answers every request with the status, the headers and the body; a null body sends none. */
  static ApiEndpoint answering(int status, Map<String, String> headers, String body) {
    try {
      return start(
          HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0),
          "http",
          status,
          headers,
          body);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static ApiEndpoint answering(int status, String body) {
    return answering(status, JSON, body);
  }

  /** As {@link #answering(int, String)}, over https with the certificate of the context. */
  static ApiEndpoint answeringOverTls(SSLContext tls, int status, String body) {
    try {
      HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.setHttpsConfigurator(new HttpsConfigurator(tls));
      return start(server, "https", status, JSON, body);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static ApiEndpoint start(
      HttpServer server, String scheme, int status, Map<String, String> headers, String body) {
    ApiEndpoint endpoint = new ApiEndpoint(server, scheme);
    server.createContext("/", exchange -> endpoint.handle(exchange, status, headers, body));
    server.start();
    return endpoint;
  }

  private void handle(HttpExchange exchange, int status, Map<String, String> headers, String body)
      throws IOException {
    requests.add(
        new Request(
            exchange.getRequestMethod(),
            exchange.getRequestURI().getRawPath(),
            exchange.getRequestURI().getRawQuery(),
            exchange.getRequestHeaders()));
    headers.forEach(exchange.getResponseHeaders()::add);
    if (body == null) {
      exchange.sendResponseHeaders(status, -1);
    } else {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
    }
    exchange.close();
  }

  String baseUrl() {
    return scheme + "://127.0.0.1:" + server.getAddress().getPort() + "/v1";
  }

  List<Request> requests() {
    return List.copyOf(requests);
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
