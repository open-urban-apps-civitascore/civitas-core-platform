/**
 * Copyright (c) 2012 - 2025 Data In Motion and others. All rights reserved.
 *
 * <p>This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * <p>SPDX-License-Identifier: EPL-2.0
 *
 * <p>Contributors: Data In Motion - initial API and implementation
 */
package com.civitas.configadapter.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HealthCheckServerTest {

  private HttpClient httpClient;

  @BeforeEach
  void setUp() {
    httpClient = HttpClient.newHttpClient();
  }

  @Test
  void shouldReturnLivenessProbe() throws Exception {
    try (HealthCheckServer testServer = new HealthCheckServer(0, createMockConsumers(2))) {
      testServer.start();
      HttpResponse<String> response = sendGetRequest(testServer.getPort(), "/health/live");

      assertEquals(200, response.statusCode());
      assertTrue(response.body().contains("\"status\":\"UP\""));
    }
  }

  @Test
  void shouldReturnReadinessProbeNotReady() throws Exception {
    try (HealthCheckServer testServer = new HealthCheckServer(0, createMockConsumers(2))) {
      testServer.start();
      HttpResponse<String> response = sendGetRequest(testServer.getPort(), "/health/ready");

      assertEquals(503, response.statusCode());
      assertTrue(response.body().contains("\"status\":\"DOWN\""));
    }
  }

  @Test
  void shouldReturnReadinessProbeReady() throws Exception {
    try (HealthCheckServer testServer = new HealthCheckServer(0, createMockConsumers(1))) {
      testServer.start();
      testServer.markReady();
      HttpResponse<String> response = sendGetRequest(testServer.getPort(), "/health/ready");

      assertEquals(200, response.statusCode());
      assertTrue(response.body().contains("\"status\":\"UP\""));
    }
  }

  @Test
  void shouldReturnHealthEndpointWithDetails() throws Exception {
    try (HealthCheckServer testServer = new HealthCheckServer(0, createMockConsumers(2))) {
      testServer.start();
      testServer.markReady();
      HttpResponse<String> response = sendGetRequest(testServer.getPort(), "/health");

      assertEquals(200, response.statusCode());
      assertTrue(response.body().contains("\"status\":\"UP\""));
      assertTrue(response.body().contains("\"consumers\":2"));
      assertTrue(response.body().contains("\"ready\":true"));
    }
  }

  @Test
  void shouldReturn405ForNonGetRequests() throws Exception {
    try (HealthCheckServer testServer = new HealthCheckServer(0, createMockConsumers(2))) {
      testServer.start();
      HttpResponse<String> response = sendPostRequest(testServer.getPort(), "/health", "");

      assertEquals(405, response.statusCode());
      assertEquals("Method Not Allowed", response.body());
    }
  }

  private HttpResponse<String> sendGetRequest(int port, String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + path)).GET().build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> sendPostRequest(int port, String path, String body)
      throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private List<Object> createMockConsumers(int count) {
    return IntStream.range(0, count).mapToObj(i -> new Object()).toList();
  }
}
