/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow;

import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.frostSink;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.graphWithCron;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.graphWithGeoPoint;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.graphWithMapping;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.graphWithoutMapping;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.mixedMapping;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.mqttSource;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.mqttToPostgis;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.planner;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.postgisSink;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.postgisSinkWithPk;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.sqlSource;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.sqlToPostgis;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.stretchedKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder.FlowBuildSpec;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest.SinkSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Byte-level characterization of the emitted flow snapshots. The golden files pin the exact
 * serialized form — component ids, array order, property order — because deterministic ids are the
 * redeploy-idempotency key against a live NiFi, not just an aesthetic property.
 *
 * <p>Comparison is raw string equality on purpose: parsing the JSON first would hide exactly the
 * ordering regressions this test exists to catch.
 *
 * <p>To regenerate after an intentional flow-shape change, run with {@code -Dgolden.update=true}
 * and commit the diff.
 */
class FlowSnapshotGoldenTest {

  private static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "golden");
  private static final boolean UPDATE = Boolean.getBoolean("golden.update");

  // ── builder level: FlowBuildSpec → snapshot ──

  @Test
  void builderMqttToPostgisWithMapping() throws Exception {
    verifyBuilder("builder-mqtt-postgis-mapping", mqttToPostgis(mixedMapping()));
  }

  @Test
  void builderMqttToPostgisWithoutMapping() throws Exception {
    verifyBuilder("builder-mqtt-postgis-nomapping", mqttToPostgis(java.util.List.of()));
  }

  @Test
  void builderSqlToPostgisWithCron() throws Exception {
    verifyBuilder("builder-sql-postgis-cron", sqlToPostgis("0 0 6 * * ?"));
  }

  @Test
  void builderSqlToPostgisWithoutCron() throws Exception {
    verifyBuilder("builder-sql-postgis-nocron", sqlToPostgis());
  }

  @Test
  void builderMqttToFrost() throws Exception {
    verifyBuilder("builder-mqtt-frost", frostSink());
  }

  @Test
  void builderMqttToFrostWithMapping() throws Exception {
    verifyBuilder("builder-mqtt-frost-mapping", NifiTestFixtures.frostSinkWithMapping());
  }

  // ── planner level: PipelineDeploymentRequest → plan (snapshot + sensitive map) ──

  @Test
  void plannerMqttToPostgisWithMapping() throws Exception {
    byte[] key = stretchedKey();
    try (CredentialResolver resolver = new CredentialResolver(key)) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "golden-mqtt-map", graphWithMapping(), mqttSource(enc(key)), postgisSink()));
      verify("planner-mqtt-postgis-mapping", plan.snapshotJson());
      assertEquals(
          Map.of(
              "ConsumeMQTT", Map.of("Password", SECRET),
              "PostGISConnectionPool", Map.of("Password", "db-secret")),
          plan.sensitivePropsByComponent());
    }
  }

  @Test
  void plannerMqttToPostgisWithoutMapping() throws Exception {
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "golden-mqtt-nomap", graphWithoutMapping(), mqttSource(null), postgisSink()));
      verify("planner-mqtt-postgis-nomapping", plan.snapshotJson());
      assertEquals(
          Map.of("PostGISConnectionPool", Map.of("Password", "db-secret")),
          plan.sensitivePropsByComponent());
    }
  }

  @Test
  void plannerSqlToPostgisWithCronAndPk() throws Exception {
    byte[] key = stretchedKey();
    try (CredentialResolver resolver = new CredentialResolver(key)) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "golden-sql-cron",
                      graphWithCron("0 0 6 * * ?"),
                      sqlSource(enc(key)),
                      postgisSinkWithPk()));
      verify("planner-sql-postgis-cron-pk", plan.snapshotJson());
      assertEquals(
          Map.of(
              "SourceConnectionPool", Map.of("Password", SECRET),
              "PostGISConnectionPool", Map.of("Password", "db-secret")),
          plan.sensitivePropsByComponent());
    }
  }

  @Test
  void plannerSqlToPostgisWithoutCron() throws Exception {
    byte[] key = stretchedKey();
    try (CredentialResolver resolver = new CredentialResolver(key)) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "golden-sql-nocron",
                      graphWithMapping(),
                      sqlSource(enc(key)),
                      postgisSinkWithPk()));
      verify("planner-sql-postgis-nocron-pk", plan.snapshotJson());
      assertEquals(
          Map.of(
              "SourceConnectionPool", Map.of("Password", SECRET),
              "PostGISConnectionPool", Map.of("Password", "db-secret")),
          plan.sensitivePropsByComponent());
    }
  }

  @Test
  void plannerMqttToFrost() throws Exception {
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "golden-frost",
                      graphWithoutMapping(),
                      mqttSource(null),
                      new SinkSpec(SinkType.FROST, null),
                      "7"));
      verify("planner-mqtt-frost", plan.snapshotJson());
      assertEquals(Map.of(), plan.sensitivePropsByComponent());
    }
  }

  @Test
  void plannerMqttToFrostWithMapping() throws Exception {
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "golden-frost-map",
                      NifiTestFixtures.graphWithFrostMapping(),
                      mqttSource(null),
                      new SinkSpec(SinkType.FROST, null),
                      "7"));
      verify("planner-mqtt-frost-mapping", plan.snapshotJson());
      assertEquals(Map.of(), plan.sensitivePropsByComponent());
    }
  }

  @Test
  void plannerSqlToFrostWithMapping() throws Exception {
    byte[] key = stretchedKey();
    try (CredentialResolver resolver = new CredentialResolver(key)) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "golden-sql-frost-map",
                      NifiTestFixtures.graphWithFrostMapping(),
                      sqlSource(enc(key)),
                      new SinkSpec(SinkType.FROST, null),
                      "7"));
      verify("planner-sql-frost-mapping", plan.snapshotJson());
      assertEquals(
          Map.of("SourceConnectionPool", Map.of("Password", SECRET)),
          plan.sensitivePropsByComponent());
    }
  }

  @Test
  void plannerMqttToPostgisWithGeoPoint() throws Exception {
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "golden-geo", graphWithGeoPoint(), mqttSource(null), postgisSink()));
      verify("planner-mqtt-postgis-geopoint", plan.snapshotJson());
    }
  }

  // ── harness ──

  private static final String SECRET = "sup3r-s3cret-mqtt-pw";

  private static String enc(byte[] key) throws Exception {
    return "ENC("
        + CredentialEncryptor.encrypt(
            SECRET, key, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT)
        + ")";
  }

  private void verifyBuilder(String name, FlowBuildSpec spec) throws Exception {
    verify(name, NifiTestFixtures.flowBuilder().build(spec));
  }

  private void verify(String name, String snapshot) throws Exception {
    Path golden = GOLDEN_DIR.resolve(name + ".json");
    if (UPDATE) {
      Files.createDirectories(GOLDEN_DIR);
      Files.writeString(golden, snapshot);
      return;
    }
    assertTrue(
        Files.exists(golden),
        "missing golden file " + golden + " — generate with -Dgolden.update=true and commit it");
    assertEquals(
        Files.readString(golden),
        snapshot,
        "snapshot drifted from "
            + golden
            + " — if the change is intentional, regenerate with"
            + " -Dgolden.update=true and commit the diff");
  }
}
