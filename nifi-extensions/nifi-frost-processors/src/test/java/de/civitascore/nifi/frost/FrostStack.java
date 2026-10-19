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
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * A real FROST-Server with its database, brought up for the integration tests and torn down with
 * them.
 *
 * <p>The stack is the test's own: it carries no endpoint, no data and no credential of an
 * installation, so a run changes nothing outside itself and the build needs no secret. Each test
 * class creates its own FROST project, which is the boundary a Dataset writes inside, so two
 * classes cannot see each other's entities even when they share a server.
 *
 * <p>The projects plugin is on and its default rules are off: that is the configuration the
 * platform deploys, and the Things collection of a project only exists with it.
 */
final class FrostStack implements AutoCloseable {

  /** The service root path of the official FROST HTTP image. */
  private static final String SERVICE_PATH = "/FROST-Server/v1.1";

  // renovate: datasource=docker
  private static final String FROST_IMAGE = "fraunhoferiosb/frost-server-http:2.7.3";

  // renovate: datasource=docker
  private static final String POSTGIS_IMAGE = "postgis/postgis:16-3.5-alpine";

  private final Network network = Network.newNetwork();
  private final GenericContainer<?> database;
  private final GenericContainer<?> frost;
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();

  @SuppressWarnings("resource")
  FrostStack() {
    database =
        new GenericContainer<>(DockerImageName.parse(POSTGIS_IMAGE))
            .withNetwork(network)
            .withNetworkAliases("database")
            .withEnv("POSTGRES_DB", "sensorthings")
            .withEnv("POSTGRES_USER", "sensorthings")
            .withEnv("POSTGRES_PASSWORD", "ChangeMe")
            .waitingFor(
                Wait.forLogMessage(".*database system is ready to accept connections.*", 2));
    database.start();

    frost =
        new GenericContainer<>(DockerImageName.parse(FROST_IMAGE))
            .withNetwork(network)
            .withExposedPorts(8080)
            .withEnv("serviceRootUrl", "http://frost:8080" + SERVICE_PATH + "/")
            .withEnv("plugins_projects_enable", "true")
            .withEnv("plugins_projects_enableDefaultRules", "false")
            .withEnv("plugins_modelLoader_enable", "true")
            .withEnv("plugins_multiDatastream_enable", "false")
            .withEnv("plugins_actuation_enable", "false")
            .withEnv("persistence_db_driver", "org.postgresql.Driver")
            .withEnv("persistence_db_url", "jdbc:postgresql://database:5432/sensorthings")
            .withEnv("persistence_db_username", "sensorthings")
            .withEnv("persistence_db_password", "ChangeMe")
            .withEnv("persistence_autoUpdateDatabase", "true")
            .waitingFor(
                Wait.forHttp(SERVICE_PATH + "/Things")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));
    frost.start();
  }

  /** The service root the processor is configured with. */
  String baseUrl() {
    return "http://" + frost.getHost() + ":" + frost.getMappedPort(8080) + SERVICE_PATH;
  }

  /**
   * Creates a project and answers its identifier. The Dataset's project is the write boundary of
   * the sink, so every test class takes its own.
   */
  String createProject(String name) throws IOException, InterruptedException {
    HttpResponse<String> response =
        post(
            "/Projects",
            "{\"name\":\"" + name + "\",\"description\":\"integration test project\"}");
    String location = response.headers().firstValue("Location").orElse("");
    int open = location.lastIndexOf('(');
    int close = location.lastIndexOf(')');
    if (open < 0 || close < open) {
      throw new IllegalStateException(
          "FROST did not answer the created project with a Location header (status "
              + response.statusCode()
              + "): "
              + response.body());
    }
    return location.substring(open + 1, close);
  }

  /** POSTs a body to a service-relative path, for the entities a test provisions itself. */
  HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 300) {
      throw new IllegalStateException(
          "FROST rejected the provisioning POST to "
              + path
              + " (status "
              + response.statusCode()
              + "): "
              + response.body());
    }
    return response;
  }

  /** The document a service-relative GET answers. */
  JsonNode get(String path) throws IOException, InterruptedException {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder().uri(URI.create(baseUrl() + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "FROST answered " + path + " with " + response.statusCode() + ": " + response.body());
    }
    return mapper.readTree(response.body());
  }

  /** The entities a service-relative query answers, as the {@code value} array. */
  JsonNode query(String path) throws IOException, InterruptedException {
    return get(path).path("value");
  }

  /** How many entities a query answers. */
  int count(String path) throws IOException, InterruptedException {
    return query(path).size();
  }

  /**
   * A {@code $filter} query string. The term is written as it reads and encoded here, so a test
   * asserts on the filter the port uses rather than on a hand-escaped copy of it.
   */
  static String filter(String term) {
    return "?$filter=" + URLEncoder.encode(term, StandardCharsets.UTF_8).replace("+", "%20");
  }

  @Override
  public void close() {
    http.close();
    frost.stop();
    database.stop();
    network.close();
  }
}
