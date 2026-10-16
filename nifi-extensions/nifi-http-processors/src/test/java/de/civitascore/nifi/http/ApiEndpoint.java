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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

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

  private final HttpServer server;
  private final List<Request> requests = new CopyOnWriteArrayList<>();

  private ApiEndpoint(HttpServer server) {
    this.server = server;
  }

  /** Answers every request with the status, the headers and the body; a null body sends none. */
  static ApiEndpoint answering(int status, Map<String, String> headers, String body) {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      ApiEndpoint endpoint = new ApiEndpoint(server);
      server.createContext("/", exchange -> endpoint.handle(exchange, status, headers, body));
      server.start();
      return endpoint;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static ApiEndpoint answering(int status, String body) {
    return answering(status, Map.of("Content-Type", "application/json"), body);
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
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
  }

  List<Request> requests() {
    return List.copyOf(requests);
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
