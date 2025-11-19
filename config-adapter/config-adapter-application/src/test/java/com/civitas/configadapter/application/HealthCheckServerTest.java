package com.civitas.configadapter.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HealthCheckServerTest {

  private HttpClient httpClient;
  private static final int TEST_PORT = 9090;

  @BeforeEach
  void setUp() {
    httpClient = HttpClient.newHttpClient();
  }

  @Test
  void shouldReturnLivenessProbe() throws Exception {
    List<Object> mockConsumers = Arrays.asList(new Object(), new Object());

    try (HealthCheckServer testServer = new HealthCheckServer(TEST_PORT, mockConsumers)) {
      testServer.start();
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("http://localhost:" + TEST_PORT + "/health/live"))
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertTrue(response.body().contains("\"status\":\"UP\""));
    }
  }

  @Test
  void shouldReturnReadinessProbeNotReady() throws Exception {
    List<Object> mockConsumers = Arrays.asList(new Object(), new Object());

    try (HealthCheckServer testServer = new HealthCheckServer(TEST_PORT, mockConsumers)) {
      testServer.start();
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("http://localhost:" + TEST_PORT + "/health/ready"))
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      assertEquals(503, response.statusCode());
      assertTrue(response.body().contains("\"status\":\"DOWN\""));
    }
  }

  @Test
  void shouldReturnReadinessProbeReady() throws Exception {
    List<Object> mockConsumers = Arrays.asList(new Object());

    try (HealthCheckServer testServer = new HealthCheckServer(9091, mockConsumers)) {
      testServer.start();
      testServer.markReady();

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("http://localhost:9091/health/ready"))
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertTrue(response.body().contains("\"status\":\"UP\""));
    }
  }

  @Test
  void shouldReturnHealthEndpointWithDetails() throws Exception {
    List<Object> mockConsumers = Arrays.asList(new Object(), new Object());

    try (HealthCheckServer testServer = new HealthCheckServer(9092, mockConsumers)) {
      testServer.start();
      testServer.markReady();

      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create("http://localhost:9092/health")).GET().build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertTrue(response.body().contains("\"status\":\"UP\""));
      assertTrue(response.body().contains("\"consumers\":2"));
      assertTrue(response.body().contains("\"ready\":true"));
    }
  }

  @Test
  void shouldReturn405ForNonGetRequests() throws Exception {
    List<Object> mockConsumers = Arrays.asList(new Object(), new Object());

    try (HealthCheckServer testServer = new HealthCheckServer(TEST_PORT, mockConsumers)) {
      testServer.start();
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("http://localhost:" + TEST_PORT + "/health"))
              .POST(HttpRequest.BodyPublishers.ofString(""))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      assertEquals(405, response.statusCode());
      assertEquals("Method Not Allowed", response.body());
    }
  }
}
