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

import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.DeploymentPlan;
import de.civitascore.configadapter.nifi.flow.FlowDeploymentPlanner;
import de.civitascore.configadapter.nifi.flow.FlowDeploymentPlanner.PlatformSinkConfig;
import de.civitascore.configadapter.nifi.flow.NifiTestFixtures;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest.SinkSpec;
import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.flow.SqlSourceProbe;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
class NifiDeploymentIT extends AbstractNifiIT {

  private static final int HOST_PORT = 18443;

  @BeforeAll
  static void startStack() {
    assumeTrue(dockerAvailable(), "Docker not available — skipping NiFi IT");
    startNifi(HOST_PORT);
  }

  @AfterAll
  static void stopStack() {
    stopNifi();
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
        NifiTestFixtures.planner(
            new CredentialResolver(new byte[0]),
            SqlSourceProbe.NO_OP,
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
        NifiTestFixtures.planner(
            new CredentialResolver(new byte[0]),
            SqlSourceProbe.NO_OP,
            null,
            "http://localhost:8080/FROST-Server/v1.1");
    DeploymentPlan plan =
        planner.plan(
            new PipelineDeploymentRequest(
                "frost-it", graph, source, new SinkSpec(SinkType.FROST, null), "1"));

    // A FROST/HTTP sink has no DBCP/JDBC-driver dependency, so the FULL deploy lifecycle
    // (upload → enable controller services → start) must succeed on real NiFi — this is exactly
    // the MQTT→FROST saga deploy step that gates the dataset reaching AVAILABLE.
    String pgId = client.deployFlow(plan);
    assertNotNull(pgId);
    String rootId = client.getRootProcessGroupId();
    assertTrue(client.findProcessGroupByName(rootId, plan.processGroupName()).isPresent());

    client.deleteFlowByName(plan.processGroupName());
  }
}
