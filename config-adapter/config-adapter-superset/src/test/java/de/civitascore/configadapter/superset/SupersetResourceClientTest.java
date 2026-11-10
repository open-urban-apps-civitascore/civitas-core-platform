/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.superset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SupersetResourceClientTest {

  @Test
  void createDashboard_withCoreUuid_setsStableSupersetUuid() throws Exception {
    String coreId = "94da2a04-f1c9-4cdf-903f-9458f3507917";
    AtomicReference<String> createdBody = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1/dashboard/",
        exchange -> {
          byte[] body;
          if ("POST".equals(exchange.getRequestMethod())) {
            createdBody.set(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            body = "{\"id\":42}".getBytes(StandardCharsets.UTF_8);
          } else {
            body = "{\"result\":[]}".getBytes(StandardCharsets.UTF_8);
          }
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(body);
          }
        });
    server.start();
    try (Client client = ClientBuilder.newClient()) {
      SupersetSession session = mock(SupersetSession.class);
      when(session.getAccessToken()).thenReturn("test-token");
      SupersetResourceClient resources =
          new SupersetResourceClient(
              client, session, "http://127.0.0.1:" + server.getAddress().getPort(), 1);

      assertEquals(
          42,
          resources.createDashboard(coreId, "Dashboard", null, List.of(), List.of(), false, false));
      assertEquals(coreId, new ObjectMapper().readTree(createdBody.get()).path("uuid").asText());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void createDashboard_withCoreUuid_reusesExistingDashboard() throws Exception {
    AtomicInteger posts = new AtomicInteger();
    AtomicInteger updates = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1/dashboard/",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          byte[] body;
          if (path.endsWith("/42") && "GET".equals(exchange.getRequestMethod())) {
            body = "{\"result\":{\"id\":42}}".getBytes(StandardCharsets.UTF_8);
          } else if (path.endsWith("/42") && "PUT".equals(exchange.getRequestMethod())) {
            updates.incrementAndGet();
            body = "{\"result\":{\"id\":42}}".getBytes(StandardCharsets.UTF_8);
          } else if ("POST".equals(exchange.getRequestMethod())) {
            posts.incrementAndGet();
            body = "{\"id\":43}".getBytes(StandardCharsets.UTF_8);
          } else {
            body = "{\"result\":[{\"id\":42}]}".getBytes(StandardCharsets.UTF_8);
          }
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(body);
          }
        });
    server.start();
    try (Client client = ClientBuilder.newClient()) {
      SupersetSession session = mock(SupersetSession.class);
      when(session.getAccessToken()).thenReturn("test-token");
      SupersetResourceClient resources =
          new SupersetResourceClient(
              client, session, "http://127.0.0.1:" + server.getAddress().getPort(), 1);

      assertEquals(
          42,
          resources.createDashboard(
              "94da2a04-f1c9-4cdf-903f-9458f3507917",
              "Dashboard",
              null,
              List.of(),
              List.of(),
              false,
              false));
      assertEquals(1, updates.get());
      assertEquals(0, posts.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void findPostgisDatasetsByCivitasId_withMultiplePages_findsLaterDataset() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    AtomicInteger requests = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1/dataset/",
        exchange -> {
          String query =
              URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
          int page = query.contains("page:1") ? 1 : 0;
          requests.incrementAndGet();
          String datasetId = page == 0 ? "other" : "target";
          byte[] body =
              mapper.writeValueAsBytes(
                  Map.of(
                      "count",
                      2,
                      "result",
                      List.of(
                          Map.of(
                              "id",
                              page + 1,
                              "table_name",
                              "table_" + page,
                              "extra",
                              mapper.writeValueAsString(Map.of("civitasDatasetId", datasetId))))));
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(body);
          }
        });
    server.start();
    try (Client client = ClientBuilder.newClient()) {
      SupersetSession session = mock(SupersetSession.class);
      when(session.getAccessToken()).thenReturn("test-token");
      SupersetResourceClient resources =
          new SupersetResourceClient(
              client, session, "http://127.0.0.1:" + server.getAddress().getPort(), 1);

      assertEquals(Map.of("table_1", 2), resources.findPostgisDatasetsByCivitasId("target"));
      assertEquals(2, requests.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void exportDashboardZip_asksForTheOneDashboardAndReturnsTheZip() throws Exception {
    byte[] zip = {0x50, 0x4b, 0x03, 0x04, 1, 2, 3};
    AtomicReference<String> askedFor = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1/dashboard/export/",
        exchange -> {
          askedFor.set(
              URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
          exchange.getResponseHeaders().set("Content-Type", "application/zip");
          exchange.sendResponseHeaders(200, zip.length);
          try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(zip);
          }
        });
    server.start();
    try (Client client = ClientBuilder.newClient()) {
      SupersetSession session = mock(SupersetSession.class);
      when(session.getAccessToken()).thenReturn("test-token");
      SupersetResourceClient resources =
          new SupersetResourceClient(
              client, session, "http://127.0.0.1:" + server.getAddress().getPort(), 1);

      assertArrayEquals(zip, resources.exportDashboardZip(7));
      assertEquals("q=!(7)", askedFor.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void exportDashboardZip_returnsNullForADashboardSupersetDoesNotKnow() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1/dashboard/export/",
        exchange -> {
          exchange.sendResponseHeaders(404, -1);
          exchange.close();
        });
    server.start();
    try (Client client = ClientBuilder.newClient()) {
      SupersetSession session = mock(SupersetSession.class);
      when(session.getAccessToken()).thenReturn("test-token");
      SupersetResourceClient resources =
          new SupersetResourceClient(
              client, session, "http://127.0.0.1:" + server.getAddress().getPort(), 1);

      assertNull(resources.exportDashboardZip(7));
    } finally {
      server.stop(0);
    }
  }
}
