/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Deserialization tests for Dataset, Datasource, and DataPipeline using the example CloudEvent. */
class DatasetSerializationTest {

  private ObjectMapper objectMapper;
  private Dataset dataset;

  @BeforeEach
  void setUp() throws Exception {
    objectMapper = new ObjectMapper();
    try (InputStream is = getClass().getResourceAsStream("/dataset-event.json")) {
      assertNotNull(is, "Test resource dataset-event.json not found on classpath");
      JsonNode root = objectMapper.readTree(is);
      JsonNode dataNode = root.get("data");
      assertNotNull(dataNode, "'data' field must be present in CloudEvent");
      dataset = objectMapper.treeToValue(dataNode, Dataset.class);
    }
  }

  @Test
  void shouldDeserializeDatasetFields() {
    assertEquals("b7c8b5d4-3d9c-4e3b-9a12-6b7c3f1d9e2a", dataset.id());
    assertEquals("Neustadt Traffic Counts 2025", dataset.name());
  }

  @Test
  void shouldDeserializeTwoDatasources() {
    assertEquals(2, dataset.datasources().size());
  }

  @Test
  void shouldDeserializePostgresqlDatasource() {
    Datasource pg = dataset.datasources().getFirst();
    assertEquals("0a7b8c9d-1e2f-4a5b-9c0d-1e2f3a4b5c6d", pg.getId());
    assertEquals("postgresql", pg.getType());
    assertEquals("Neustadt Mobility DB", pg.getName());
    assertEquals("pg-mobility.neustadt.de", pg.getHost());
    assertEquals(5432, pg.getPort());

    // type-specific fields captured as additional properties
    assertEquals("mobility", pg.getAdditionalProperties().get("database"));
    assertEquals("public", pg.getAdditionalProperties().get("schema"));
    assertEquals("mobility_reader", pg.getAdditionalProperties().get("username"));
    assertEquals(true, pg.getAdditionalProperties().get("ssl"));
    assertEquals("require", pg.getAdditionalProperties().get("ssl_mode"));
    assertEquals(10, pg.getAdditionalProperties().get("connect_timeout_seconds"));

    // nested additional property
    @SuppressWarnings("unchecked")
    Map<String, Object> pool = (Map<String, Object>) pg.getAdditionalProperties().get("pool");
    assertNotNull(pool);
    assertEquals(1, pool.get("min"));
    assertEquals(10, pool.get("max"));
  }

  @Test
  void shouldDeserializeMqttDatasource() {
    Datasource mqtt = dataset.datasources().get(1);
    assertEquals("5f2a1c3e-7b8d-4c9e-a1b2-3c4d5e6f7a8b", mqtt.getId());
    assertEquals("mqtt", mqtt.getType());
    assertEquals("Neustadt IoT MQTT Broker", mqtt.getName());
    assertEquals("iot-broker.neustadt.de", mqtt.getHost());
    assertEquals(8883, mqtt.getPort());

    // type-specific fields
    assertEquals(
        "mqtts://iot-broker.neustadt.de", mqtt.getAdditionalProperties().get("broker_url"));
    assertEquals("civitas-core-ingestion", mqtt.getAdditionalProperties().get("client_id"));
    assertEquals(1, mqtt.getAdditionalProperties().get("qos"));

    @SuppressWarnings("unchecked")
    List<String> topics = (List<String>) mqtt.getAdditionalProperties().get("topics");
    assertEquals(3, topics.size());
    assertEquals("neustadt/traffic/+/counts", topics.getFirst());

    @SuppressWarnings("unchecked")
    Map<String, Object> tls = (Map<String, Object>) mqtt.getAdditionalProperties().get("tls");
    assertNotNull(tls);
    assertEquals(true, tls.get("enabled"));

    @SuppressWarnings("unchecked")
    Map<String, Object> reconnect =
        (Map<String, Object>) mqtt.getAdditionalProperties().get("reconnect");
    assertNotNull(reconnect);
    assertEquals(20, reconnect.get("max_retries"));
  }

  @Test
  void shouldDeserializeTwoDataPipelines() {
    assertEquals(2, dataset.datapipelines().size());
  }

  @Test
  void shouldDeserializeAddPipeline() {
    DataPipeline addPipeline = dataset.datapipelines().getFirst();
    assertEquals("db-to-frost-01", addPipeline.id());
    assertEquals("1", addPipeline.version());
    assertEquals("ADD", addPipeline.action());
    assertNotNull(addPipeline.data());
    assertTrue(addPipeline.data().containsKey("input"));
    assertTrue(addPipeline.data().containsKey("pipeline"));
    assertTrue(addPipeline.data().containsKey("output"));
  }

  @Test
  void shouldDeserializeDeletePipeline() {
    DataPipeline deletePipeline = dataset.datapipelines().get(1);
    assertEquals("db-to-frost-02", deletePipeline.id());
    assertEquals("2", deletePipeline.version());
    assertEquals("DELETE", deletePipeline.action());
    assertNull(deletePipeline.data());
  }

  @Test
  void shouldDeserializeNamedApis() {
    assertNotNull(dataset.namedApis());
    assertEquals(2, dataset.namedApis().size());

    NamedApi traffic = dataset.namedApis().getFirst();
    assertEquals("traffic", traffic.slug());
    assertEquals(ApiStandards.STA, traffic.standard());
    assertEquals("1.1", traffic.version());

    // Second fixture entry exercises a non-STA standard and an absent (null) version.
    NamedApi boundaries = dataset.namedApis().get(1);
    assertEquals("boundaries", boundaries.slug());
    assertEquals(ApiStandards.OWS, boundaries.standard());
    assertNull(boundaries.version());
  }

  /**
   * Pins the forward-compat property documented on {@link NamedApi}: the record uses
   * {@code @JsonIgnoreProperties(ignoreUnknown = true)} so portal-backend can add fields (e.g. the
   * portal-backend-private {@code name} and {@code description}) without breaking config-adapter
   * deserialization.
   */
  @Test
  void shouldDeserializeNamedApiIgnoringUnknownFields() throws Exception {
    String json =
        """
        {
          "slug": "traffic",
          "standard": "STA",
          "version": "1.1",
          "name": "Traffic Sensor Readings",
          "description": "Live traffic counter readings from city sensors."
        }
        """;

    NamedApi api = objectMapper.readValue(json, NamedApi.class);

    assertEquals("traffic", api.slug());
    assertEquals(ApiStandards.STA, api.standard());
    assertEquals("1.1", api.version());
  }

  /**
   * #1309: {@code standard} is a String, so a value outside the current controlled vocabulary must
   * deserialize cleanly (an adapter can then ignore or diagnose it) instead of failing the whole
   * event — the forward-compatibility property that a Java enum would break.
   */
  @Test
  void shouldDeserializeUnknownStandardValueForForwardCompatibility() throws Exception {
    String json =
        """
        {
          "slug": "coverage",
          "standard": "COVERAGE",
          "version": null
        }
        """;

    NamedApi api = objectMapper.readValue(json, NamedApi.class);

    assertEquals("coverage", api.slug());
    assertEquals("COVERAGE", api.standard());
    assertNull(api.version());
  }

  @Test
  void shouldRoundTripSerializeDataset() throws Exception {
    String json = objectMapper.writeValueAsString(dataset);
    Dataset roundTripped = objectMapper.readValue(json, Dataset.class);
    assertEquals(dataset.id(), roundTripped.id());
    assertEquals(dataset.name(), roundTripped.name());
    assertEquals(dataset.datasources().size(), roundTripped.datasources().size());
    assertEquals(dataset.datapipelines().size(), roundTripped.datapipelines().size());
    assertEquals(dataset.datasources().get(0), roundTripped.datasources().get(0));
    assertEquals(dataset.datasources().get(1), roundTripped.datasources().get(1));
    assertEquals(dataset.namedApis().size(), roundTripped.namedApis().size());
    assertEquals(dataset.namedApis().get(0), roundTripped.namedApis().get(0));
    assertEquals(dataset.namedApis().get(1), roundTripped.namedApis().get(1));
  }
}
