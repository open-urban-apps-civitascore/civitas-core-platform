/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.rest;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.DeploymentPlan;
import de.civitascore.configadapter.nifi.flow.FlowDeploymentPlanner;
import de.civitascore.configadapter.nifi.flow.FlowDeploymentPlanner.PlatformSinkConfig;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest.SinkSpec;
import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.graph.GraphParser;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Real-NiFi integration test: deploys a curated MQTT→PostGIS flow — produced by the full
 * planner/binder from the engine-neutral graph — onto an actual Apache NiFi 2.9.0 instance via the
 * {@link NifiRestClient}. Validates that the client speaks the NiFi 2.9.0 REST API correctly, that
 * the curated snapshot is accepted by {@code /upload}, and that the controller-service /
 * process-group lifecycle works end to end.
 *
 * <p>Skipped automatically when Docker is unavailable (so {@code mvn verify} stays green on hosts
 * without Docker). Runs as a failsafe {@code *IT} test.
 */
class NifiDeploymentIT {

  private static final int HOST_PORT = 18443;
  private static final String USER = "admin";
  private static final String PASSWORD = "ctsNiFiTestPassword123";

  private static FixedHostPortGenericContainer<?> nifi;
  private static Client httpClient;
  private static NifiRestClient client;

  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeAll
  static void startNifi() {
    assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker not available — skipping NiFi IT");

    // In CI the Docker daemon is remote (DinD), so published ports are reachable on the resolved
    // Docker host, not localhost. Use that host both for the client URL and for NiFi's
    // proxy-host whitelist (Host-header check), so authenticate() does not get a connection
    // refused.
    String dockerHost = DockerClientFactory.instance().dockerHostIpAddress();

    nifi =
        new FixedHostPortGenericContainer<>("apache/nifi:2.9.0")
            .withFixedExposedPort(HOST_PORT, 8443)
            .withEnv("SINGLE_USER_CREDENTIALS_USERNAME", USER)
            .withEnv("SINGLE_USER_CREDENTIALS_PASSWORD", PASSWORD)
            .withEnv("NIFI_WEB_HTTPS_PORT", "8443")
            .withEnv(
                "NIFI_WEB_PROXY_HOST", dockerHost + ":" + HOST_PORT + ",localhost:" + HOST_PORT)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)));
    nifi.start();

    httpClient =
        ClientBuilder.newBuilder()
            .sslContext(trustAll())
            .hostnameVerifier((host, session) -> true)
            .build();
    client =
        new NifiRestClient("https://" + dockerHost + ":" + HOST_PORT, USER, PASSWORD, httpClient);

    // NiFi keeps initialising after the port opens; poll the REST API until it authenticates.
    await()
        .atMost(Duration.ofMinutes(3))
        .pollInterval(Duration.ofSeconds(5))
        .ignoreExceptions()
        .until(
            () -> {
              client.authenticate();
              return true;
            });
  }

  @AfterAll
  static void stopNifi() {
    if (httpClient != null) {
      httpClient.close();
    }
    if (nifi != null) {
      nifi.stop();
    }
  }

  private Map<String, Object> map(String json) throws Exception {
    return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
  }

  private DeploymentPlan buildPlan() throws Exception {
    Datasource source = new Datasource();
    source.setId("ds-it");
    source.setType("MQTT");
    source.handleUnknownProperty("urls", List.of("tcp://localhost:1883"));
    source.handleUnknownProperty("topics", List.of("sensors/+/temp"));

    SinkSpec sink = new SinkSpec(SinkType.POSTGIS, "sensor_observations");

    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "s", "type": "start", "data": {} },
                { "id": "m", "type": "mapping",
                  "data": { "mappingConfig": { "fields": {
                    "$.station_id": "$.station_id",
                    "$.temperature": "$.temperature" } } } },
                { "id": "e", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "m" },
                { "id": "e2", "source": "m", "target": "e" } ] }
            """);

    FlowDeploymentPlanner planner =
        new FlowDeploymentPlanner(
            new GraphParser(),
            new MappingConfigParser(),
            new RecordPathCompiler(),
            new NifiFlowBuilder(),
            new CredentialResolver(new byte[0]),
            new PlatformSinkConfig(
                "jdbc:postgresql://localhost:5432/civitas", "nifi", "nifi-db-secret"),
            "http://localhost:8080/FROST-Server/v1.1");

    return planner.plan(new PipelineDeploymentRequest("it-1", graph, source, sink));
  }

  @Test
  void deploysCuratedFlowOntoRealNifi() throws Exception {
    DeploymentPlan plan = buildPlan();
    String pgName = plan.processGroupName();

    String rootId = client.getRootProcessGroupId();
    assertNotNull(rootId);

    // Upload the curated snapshot — proves NiFi 2.9.0 accepts the format via /upload.
    String pgId = client.uploadSnapshot(rootId, pgName, plan.snapshotJson());
    assertNotNull(pgId);
    assertTrue(client.findProcessGroupByName(rootId, pgName).isPresent());

    // The uploaded snapshot must not contain the DB secret.
    assertFalse(plan.snapshotJson().contains("nifi-db-secret"));

    // Patching the sensitive DB password is part of the deploy REST contract (optimistic-locking
    // revision + sensitive controller-service property) and must be accepted by real NiFi.
    client.patchSensitiveProperties(pgId, plan.sensitivePropsByComponent());

    // Enabling controller services and starting the group additionally require the runtime
    // environment (PostgreSQL JDBC driver mounted into NiFi, a reachable MQTT broker and database)
    // which a bare NiFi container does not provide — the DBCP would stay INVALID. That full runtime
    // lifecycle is exercised by the dataset-saga Bruno lifecycles against the complete CI stack.
    // This
    // IT validates the deploy/upload REST contract (auth, root lookup, snapshot upload,
    // process-group
    // discovery, sensitive-property patch) against a real Apache NiFi 2.9.0 instance.

    // Clean up: stop & delete the uploaded process group (idempotent) and confirm it is gone.
    client.deleteFlowByName(pgName);
    await()
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(() -> client.findProcessGroupByName(rootId, pgName).isEmpty());
  }

  @Test
  void deploysFrostFlowFullLifecycleOntoRealNifi() throws Exception {
    Datasource source = new Datasource();
    source.setId("ds-frost");
    source.setType("MQTT");
    source.handleUnknownProperty("urls", List.of("tcp://localhost:1883"));
    source.handleUnknownProperty("topics", List.of("sensors/+/temp"));

    // FROST consumes the raw SensorThings envelope from the source (find-or-create); no mapping
    // node — the planner rejects a FROST sink with a mapping (see frostSinkWithMappingIsRejected).
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "s", "type": "start", "data": {} },
                { "id": "e", "type": "end", "data": {} } ],
              "edges": [ { "id": "e1", "source": "s", "target": "e" } ] }
            """);

    FlowDeploymentPlanner planner =
        new FlowDeploymentPlanner(
            new GraphParser(),
            new MappingConfigParser(),
            new RecordPathCompiler(),
            new NifiFlowBuilder(),
            new CredentialResolver(new byte[0]),
            null,
            "http://localhost:8080/FROST-Server/v1.1");
    DeploymentPlan plan =
        planner.plan(
            new PipelineDeploymentRequest(
                "frost-it", graph, source, new SinkSpec(SinkType.FROST, null)));

    // A FROST/HTTP sink has no DBCP/JDBC-driver dependency, so the FULL deploy lifecycle
    // (upload → enable controller services → start) must succeed on real NiFi — this is exactly
    // the MQTT→FROST saga deploy step that gates the dataset reaching AVAILABLE.
    String pgId = client.deployFlow(plan);
    assertNotNull(pgId);
    String rootId = client.getRootProcessGroupId();
    assertTrue(client.findProcessGroupByName(rootId, plan.processGroupName()).isPresent());

    client.deleteFlowByName(plan.processGroupName());
  }

  private static SSLContext trustAll() {
    try {
      TrustManager[] trust = {
        new X509TrustManager() {
          @Override
          public void checkClientTrusted(java.security.cert.X509Certificate[] c, String t) {}

          @Override
          public void checkServerTrusted(java.security.cert.X509Certificate[] c, String t) {}

          @Override
          public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[0];
          }
        }
      };
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trust, new SecureRandom());
      return context;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
