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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest.SinkSpec;
import de.civitascore.configadapter.nifi.graph.GraphParser;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlowDeploymentPlannerTest {

  private static final String MASTER_KEY_HEX =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  private static final String SECRET = "sup3r-s3cret-mqtt-pw";

  private final ObjectMapper mapper = new ObjectMapper();

  private byte[] stretchedKey() throws Exception {
    return CryptoKeyLoader.stretchMasterKey(CryptoKeyLoader.hexStringToBytes(MASTER_KEY_HEX));
  }

  private FlowDeploymentPlanner planner(CredentialResolver resolver) {
    return new FlowDeploymentPlanner(
        new GraphParser(),
        new MappingConfigParser(),
        new RecordPathCompiler(),
        new NifiFlowBuilder(),
        resolver,
        new FlowDeploymentPlanner.PlatformSinkConfig(
            "jdbc:postgresql://db:5432/civitas", "nifi", "db-secret"),
        "http://frost:8080/FROST-Server/v1.1");
  }

  private Map<String, Object> map(String json) throws Exception {
    return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
  }

  private Map<String, Object> graphWithMapping() throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "type": "start", "data": {} },
            { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                "fields": {
                  "$.station_id": "$.station_id",
                  "$.temperature": "$.temperature",
                  "$.observed_at": { "op": "toDate", "input": "$.ts", "pattern": "yyyy-MM-dd" }
                } } } },
            { "id": "n-end", "type": "end", "data": {} }
          ],
          "edges": []
        }
        """);
  }

  private Datasource mqttSource(String encryptedPassword) {
    Datasource source = new Datasource();
    source.setId("a1");
    source.setType("MQTT");
    source.handleUnknownProperty("brokerUrl", "tcp://mosquitto:1883");
    source.handleUnknownProperty("topic", "sensors/+/temp");
    source.handleUnknownProperty("password", encryptedPassword);
    return source;
  }

  private SinkSpec postgisSink() {
    return new SinkSpec(SinkType.POSTGIS, "sensor_observations");
  }

  @Test
  void buildsDeployablePlanWithoutLeakingSecrets() throws Exception {
    byte[] key = stretchedKey();
    String enc =
        "ENC("
            + CredentialEncryptor.encrypt(
                SECRET, key, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT)
            + ")";

    try (CredentialResolver resolver = new CredentialResolver(key)) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "b222", graphWithMapping(), mqttSource(enc), postgisSink()));

      // process-group name is the idempotency key
      assertEquals("pipeline-b222", plan.processGroupName());

      String snapshot = plan.snapshotJson();
      // non-sensitive slots are bound
      assertTrue(snapshot.contains("tcp://mosquitto:1883"));
      assertTrue(snapshot.contains("sensors/+/temp"));
      assertTrue(snapshot.contains("sensor_observations"));
      // compiled RecordPath mapping is present (copied field + a toDate conversion)
      assertTrue(snapshot.contains("station_id"));
      assertTrue(snapshot.contains("toDate(/ts, 'yyyy-MM-dd')"));

      // SECURITY INVARIANT: the plaintext secret never appears in the uploaded snapshot
      assertFalse(snapshot.contains(SECRET));
      // ...but is available for the post-upload sensitive-property push
      assertEquals(SECRET, plan.sensitivePropsByComponent().get("ConsumeMQTT").get("Password"));
    }
  }

  @Test
  void postgisSinkWithoutPlatformConnectionIsRejected() throws Exception {
    FlowDeploymentPlanner noDbPlanner =
        new FlowDeploymentPlanner(
            new GraphParser(),
            new MappingConfigParser(),
            new RecordPathCompiler(),
            new NifiFlowBuilder(),
            new CredentialResolver(stretchedKey()),
            null,
            "http://frost:8080/FROST-Server/v1.1");
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                noDbPlanner.plan(
                    new PipelineDeploymentRequest(
                        "p-nodb", graphWithMapping(), mqttSource(null), postgisSink())));
    assertEquals(
        de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
  }

  @Test
  void typedConversionPassesThroughWhenNoSinkSchema() throws Exception {
    // toInt/toFloat are transparent in the adapter — coercion happens at the sink
    // (PutDatabaseRecord
    // against the DB columns). A FROST sink without a data structure is therefore NOT rejected.
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.count": { "op": "toInt", "input": "$.n" } } } } } ],
              "edges": [] }
            """);
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-typed", graph, mqttSource(null), new SinkSpec(SinkType.FROST, null)));
      // the conversion is rendered as a transparent record-path copy, no schema involved
      assertTrue(plan.snapshotJson().contains("/n"));
    }
  }

  @Test
  void unsupportedSourceSinkCombinationIsRejected() throws Exception {
    Datasource source = new Datasource();
    source.setId("x");
    source.setType("kafka"); // no curated template

    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p1", graphWithMapping(), source, postgisSink())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }
}
