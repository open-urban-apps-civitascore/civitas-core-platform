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
import java.util.function.IntFunction;
import javax.net.ssl.SSLContext;

/**
 * An HTTP API on the loopback interface that records each request and answers each with a scripted
 * response.
 *
 * <p>The tests drive the processor's real HTTP path against this, rather than against a double of
 * the web client service: the request that arrives here is the request a NiFi installation sends.
 */
final class ApiEndpoint implements AutoCloseable {

  /** The request as it arrived, with the path and the query still percent-encoded. */
  record Request(String method, String rawPath, String rawQuery, Headers headers) {}

  /** A scripted response; a null body sends none. */
  record Response(int status, Map<String, String> headers, String body) {

    static Response json(String body) {
      return new Response(200, JSON, body);
    }

    static Response status(int status) {
      return new Response(status, JSON, "{}");
    }
  }

  private static final Map<String, String> JSON = Map.of("Content-Type", "application/json");

  /** Answers a request after the end of the script, so that a test sees the extra request. */
  private static final Response SCRIPT_EXHAUSTED = new Response(500, Map.of(), null);

  private final HttpServer server;
  private final String scheme;
  private final List<Request> requests = new CopyOnWriteArrayList<>();

  private ApiEndpoint(HttpServer server, String scheme) {
    this.server = server;
    this.scheme = scheme;
  }

  /** Answers every request with the status, the headers and the body; a null body sends none. */
  static ApiEndpoint answering(int status, Map<String, String> headers, String body) {
    Response response = new Response(status, headers, body);
    return startHttp(index -> response);
  }

  /** Answers the requests in order with the responses, and any request after them with a 500. */
  static ApiEndpoint answeringInTurn(Response... responses) {
    List<Response> script = List.of(responses);
    return startHttp(index -> index < script.size() ? script.get(index) : SCRIPT_EXHAUSTED);
  }

  static ApiEndpoint answering(int status, String body) {
    return answering(status, JSON, body);
  }

  /** As {@link #answering(int, String)}, over https with the certificate of the context. */
  static ApiEndpoint answeringOverTls(SSLContext tls, int status, String body) {
    try {
      HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.setHttpsConfigurator(new HttpsConfigurator(tls));
      Response response = new Response(status, JSON, body);
      return start(server, "https", index -> response);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static ApiEndpoint startHttp(IntFunction<Response> script) {
    try {
      return start(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0), "http", script);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The script gives the response of the request with the index, counted from 0. */
  private static ApiEndpoint start(HttpServer server, String scheme, IntFunction<Response> script) {
    ApiEndpoint endpoint = new ApiEndpoint(server, scheme);
    server.createContext("/", exchange -> endpoint.handle(exchange, script));
    server.start();
    return endpoint;
  }

  private synchronized void handle(HttpExchange exchange, IntFunction<Response> script)
      throws IOException {
    Response response = script.apply(requests.size());
    requests.add(
        new Request(
            exchange.getRequestMethod(),
            exchange.getRequestURI().getRawPath(),
            exchange.getRequestURI().getRawQuery(),
            exchange.getRequestHeaders()));
    response.headers().forEach(exchange.getResponseHeaders()::add);
    if (response.body() == null) {
      exchange.sendResponseHeaders(response.status(), -1);
    } else {
      byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(response.status(), bytes.length);
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
