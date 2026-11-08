/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.superset;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for Superset integration tests. Uses the singleton container pattern so Postgres and
 * Superset start only once per JVM.
 *
 * <p>The fixed {@link #TEST_SCHEMA} / {@link #TEST_TABLE} are pre-created in Postgres
 * <em>before</em> the Superset database connection is registered. Superset validates table
 * existence against a live DB query when creating a physical dataset; schemas that did not exist
 * when the connection was first established may cause a 422 validation error. Per-test isolation is
 * achieved through unique {@code datasetId} UUIDs (used as the dashboard title) and by
 * {@code @AfterEach} deleting all Superset resources for that schema after each test.
 */
@SuppressWarnings("resource")
abstract class AbstractSupersetIntegrationTest {

  protected static final Network NETWORK = Network.newNetwork();
  protected static final String TEST_SCHEMA = "civitas_test";
  protected static final String TEST_TABLE = "test_table";
  protected static final String ADMIN_USERNAME = "admin";
  protected static final String ADMIN_PASSWORD = "admin";
  protected static final String TOPIC_CREATE = "de.civitascore.data.dataset.released";
  protected static final String TOPIC_DELETE = "de.civitascore.data.dataset.unreleased";

  protected static final GenericContainer<?> POSTGRES;
  protected static final GenericContainer<?> SUPERSET;
  protected static int testDatabaseId;
  protected static String supersetUrl;

  private static final String INIT_CMD =
      "superset db upgrade && "
          + "superset fab create-admin"
          + " --username admin"
          + " --firstname Admin"
          + " --lastname Admin"
          + " --email admin@civitas.local"
          + " --password admin && "
          + "superset init && "
          + "gunicorn"
          + " --bind 0.0.0.0:8088"
          + " --workers 2"
          + " --timeout 120"
          + " 'superset.app:create_app()'";

  static {
    POSTGRES =
        new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withNetwork(NETWORK)
            .withNetworkAliases("civitas-testdb")
            .withEnv("POSTGRES_DB", "testdb")
            .withEnv("POSTGRES_USER", "testuser")
            .withEnv("POSTGRES_PASSWORD", "testpass")
            .waitingFor(
                Wait.forLogMessage(".*database system is ready to accept connections.*", 2));
    POSTGRES.start();

    // Schema must exist before the Superset DB connection is registered so that Superset can
    // validate physical dataset creation immediately without a "table not found" 422.
    initTestSchema();

    SUPERSET =
        new GenericContainer<>(DockerImageName.parse("apache/superset:4.0.2"))
            .withNetwork(NETWORK)
            .withEnv("SUPERSET_SECRET_KEY", "test-secret-key-civitas-superset!")
            .withExposedPorts(8088)
            .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/sh"))
            .withCommand("-c", INIT_CMD)
            .waitingFor(
                Wait.forHttp("/health")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(5)));
    SUPERSET.start();

    supersetUrl = "http://" + SUPERSET.getHost() + ":" + SUPERSET.getMappedPort(8088);
    testDatabaseId = registerTestDatabase();
  }

  private static void initTestSchema() {
    try {
      var result =
          POSTGRES.execInContainer(
              "psql",
              "-U",
              "testuser",
              "-d",
              "testdb",
              "-c",
              "CREATE SCHEMA IF NOT EXISTS "
                  + TEST_SCHEMA
                  + "; CREATE TABLE IF NOT EXISTS "
                  + TEST_SCHEMA
                  + "."
                  + TEST_TABLE
                  + " (id SERIAL PRIMARY KEY, name TEXT NOT NULL);");
      if (result.getExitCode() != 0) {
        throw new RuntimeException(
            "initTestSchema failed (exit " + result.getExitCode() + "): " + result.getStderr());
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("initTestSchema failed", e);
    }
  }

  private static int registerTestDatabase() {
    Client client =
        ClientBuilder.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    try {
      SupersetSession session =
          new SupersetSession(client, supersetUrl, ADMIN_USERNAME, ADMIN_PASSWORD);
      session.authenticate();

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("database_name", "civitas-testdb");
      body.put("sqlalchemy_uri", "postgresql://testuser:testpass@civitas-testdb:5432/testdb");
      body.put("expose_in_sqllab", false);

      Invocation.Builder request =
          client
              .target(supersetUrl)
              .path("/api/v1/database/")
              .request(MediaType.APPLICATION_JSON)
              .header("Authorization", "Bearer " + session.getAccessToken())
              .header("X-CSRFToken", session.getCsrfToken());
      if (session.getSessionCookie() != null) {
        request = request.header("Cookie", session.getSessionCookie());
      }

      try (Response response = request.post(Entity.json(body))) {
        String responseBody = response.readEntity(String.class);
        if (response.getStatus() != 201) {
          throw new RuntimeException(
              "Failed to register test database, status="
                  + response.getStatus()
                  + ", body="
                  + responseBody);
        }
        return new ObjectMapper().readTree(responseBody).path("id").asInt();
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to register test database", e);
    } finally {
      client.close();
    }
  }
}
