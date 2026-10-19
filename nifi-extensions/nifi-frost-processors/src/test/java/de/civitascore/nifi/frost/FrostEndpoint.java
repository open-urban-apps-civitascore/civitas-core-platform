/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A FROST batch endpoint on the loopback interface.
 *
 * <p>The tests drive the processor's real HTTP path against this, rather than against a double of
 * the web client service: the request that arrives here is the request a NiFi installation sends,
 * including the headers and the encoding of the document.
 */
final class FrostEndpoint implements AutoCloseable {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final HttpServer server;
  private final List<JsonNode> documents = new ArrayList<>();
  private final List<String> authorizations = new ArrayList<>();

  private FrostEndpoint(HttpServer server) {
    this.server = server;
  }

  /** Starts an endpoint that answers every batch with the document the function returns. */
  static FrostEndpoint answering(Function<JsonNode, String> answer, int status) {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      FrostEndpoint endpoint = new FrostEndpoint(server);
      server.createContext("/v1.1/$batch", exchange -> endpoint.handle(exchange, answer, status));
      server.start();
      return endpoint;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void handle(HttpExchange exchange, Function<JsonNode, String> answer, int status)
      throws IOException {
    JsonNode document = MAPPER.readTree(exchange.getRequestBody().readAllBytes());
    documents.add(document);
    authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));

    byte[] body = answer.apply(document).getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }

  String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1.1";
  }

  /** The batch documents that arrived, in the order they arrived. */
  List<JsonNode> documents() {
    return List.copyOf(documents);
  }

  /** The Authorization header of each request, null where there was none. */
  List<String> authorizations() {
    return new ArrayList<>(authorizations);
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
