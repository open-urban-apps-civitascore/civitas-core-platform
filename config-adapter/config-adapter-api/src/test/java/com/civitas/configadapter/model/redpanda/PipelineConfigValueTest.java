/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model.redpanda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civitas.configadapter.model.ConfigValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PipelineConfigValue}. */
class PipelineConfigValueTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void constructor_whenNoArgs_shouldHaveNullFields() {
    PipelineConfigValue value = new PipelineConfigValue();
    assertNull(value.getPipelineId());
    assertNull(value.getInput());
    assertNull(value.getPipeline());
    assertNull(value.getOutput());
    assertNotNull(value.getAdditionalProperties());
    assertTrue(value.getAdditionalProperties().isEmpty());
  }

  @Test
  void implementsConfigValue_shouldBeTrue() {
    PipelineConfigValue value = new PipelineConfigValue();
    assertInstanceOf(ConfigValue.class, value);
  }

  @Test
  void redpandaResultType_shouldHaveExpectedValue() {
    assertEquals(
        "de.civitascore.data.pipeline.processing.result", PipelineConfigValue.REDPANDA_RESULT_TYPE);
  }

  @Test
  void setters_whenCalled_shouldStoreValues() {
    PipelineConfigValue value = new PipelineConfigValue();
    value.setPipelineId("pipeline-123");

    PipelineInput input = new PipelineInput();
    MqttInput mqtt = new MqttInput();
    mqtt.setUrls(List.of("tcp://broker:1883"));
    input.setMqtt(mqtt);
    value.setInput(input);

    PipelineProcessors pipeline = new PipelineProcessors();
    ProcessorStep step = new ProcessorStep();
    step.setMapping("root = this");
    pipeline.setProcessors(List.of(step));
    value.setPipeline(pipeline);

    PipelineOutput output = new PipelineOutput();
    HttpClientOutput httpClient = new HttpClientOutput();
    httpClient.setUrl("http://api.example.com/data");
    output.setHttpClient(httpClient);
    value.setOutput(output);

    assertEquals("pipeline-123", value.getPipelineId());
    assertNotNull(value.getInput());
    assertNotNull(value.getPipeline());
    assertNotNull(value.getOutput());
  }

  @Test
  void toApiMap_whenAllFieldsSet_shouldContainAll() {
    PipelineConfigValue value = new PipelineConfigValue();
    value.setPipelineId("pipeline-123");

    PipelineInput input = new PipelineInput();
    MqttInput mqtt = new MqttInput();
    mqtt.setUrls(List.of("tcp://broker:1883"));
    input.setMqtt(mqtt);
    value.setInput(input);

    PipelineProcessors pipeline = new PipelineProcessors();
    ProcessorStep step = new ProcessorStep();
    step.setMapping("root = this");
    pipeline.setProcessors(List.of(step));
    value.setPipeline(pipeline);

    PipelineOutput output = new PipelineOutput();
    HttpClientOutput httpClient = new HttpClientOutput();
    httpClient.setUrl("http://api.example.com/data");
    output.setHttpClient(httpClient);
    value.setOutput(output);

    Map<String, Object> map = value.toApiMap();
    assertNull(map.get("pipeline_id"));
    assertNotNull(map.get("input"));
    assertNotNull(map.get("pipeline"));
    assertNotNull(map.get("output"));
  }

  @Test
  void toApiMap_whenEmpty_shouldReturnEmptyMap() {
    PipelineConfigValue value = new PipelineConfigValue();
    Map<String, Object> map = value.toApiMap();
    assertTrue(map.isEmpty());
  }

  @Test
  void toApiMap_whenOnlyPipelineIdSet_shouldNotIncludePipelineId() {
    PipelineConfigValue value = new PipelineConfigValue();
    value.setPipelineId("pipeline-123");

    Map<String, Object> map = value.toApiMap();
    assertTrue(map.isEmpty(), "pipeline_id is a routing field and should not appear in toApiMap()");
  }

  @Test
  void toApiMap_shouldIncludeAdditionalProperties() {
    PipelineConfigValue value = new PipelineConfigValue();
    value.setPipelineId("pipeline-123");
    value.handleUnknownProperty("logger", Map.of("level", "DEBUG"));

    Map<String, Object> map = value.toApiMap();
    assertNull(map.get("pipeline_id"));
    assertNotNull(map.get("logger"));
  }

  @Test
  void toApiMap_shouldContainNestedMaps() {
    PipelineConfigValue value = buildFullPipelineConfigValue();

    Map<String, Object> map = value.toApiMap();
    @SuppressWarnings("unchecked")
    Map<String, Object> inputMap = (Map<String, Object>) map.get("input");
    assertNotNull(inputMap);
    assertNotNull(inputMap.get("mqtt"));

    @SuppressWarnings("unchecked")
    Map<String, Object> pipelineMap = (Map<String, Object>) map.get("pipeline");
    assertNotNull(pipelineMap);
    assertNotNull(pipelineMap.get("processors"));

    @SuppressWarnings("unchecked")
    Map<String, Object> outputMap = (Map<String, Object>) map.get("output");
    assertNotNull(outputMap);
    assertNotNull(outputMap.get("http_client"));
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    PipelineConfigValue value1 = new PipelineConfigValue();
    value1.setPipelineId("pipeline-123");
    PipelineConfigValue value2 = new PipelineConfigValue();
    value2.setPipelineId("pipeline-123");

    assertEquals(value1, value2);
    assertEquals(value1.hashCode(), value2.hashCode());
  }

  @Test
  void equals_whenSameObject_shouldReturnTrue() {
    PipelineConfigValue value = new PipelineConfigValue();
    assertEquals(value, value);
  }

  @Test
  void equals_whenNull_shouldReturnFalse() {
    PipelineConfigValue value = new PipelineConfigValue();
    assertNotEquals(null, value);
  }

  @Test
  void equals_whenDifferentClass_shouldReturnFalse() {
    PipelineConfigValue value = new PipelineConfigValue();
    assertNotEquals("string", value);
  }

  @Test
  void equals_whenDifferentPipelineId_shouldReturnFalse() {
    PipelineConfigValue value1 = new PipelineConfigValue();
    value1.setPipelineId("pipeline-1");
    PipelineConfigValue value2 = new PipelineConfigValue();
    value2.setPipelineId("pipeline-2");

    assertNotEquals(value1, value2);
  }

  @Test
  void equals_whenDifferentAdditionalProperties_shouldReturnFalse() {
    PipelineConfigValue value1 = new PipelineConfigValue();
    value1.setPipelineId("pipeline-123");
    PipelineConfigValue value2 = new PipelineConfigValue();
    value2.setPipelineId("pipeline-123");
    value2.handleUnknownProperty("extra", "val");

    assertNotEquals(value1, value2);
  }

  @Test
  void toString_whenCalled_shouldContainClassName() {
    PipelineConfigValue value = new PipelineConfigValue();
    value.setPipelineId("pipeline-123");

    String str = value.toString();
    assertTrue(str.contains("PipelineConfigValue"));
    assertTrue(str.contains("pipeline-123"));
  }

  @Test
  void jsonSerialization_shouldUseSnakeCaseForAnnotatedFields() throws Exception {
    PipelineConfigValue value = new PipelineConfigValue();
    value.setPipelineId("pipeline-123");

    String json = objectMapper.writeValueAsString(value);
    assertTrue(json.contains("\"pipeline_id\""));
    assertTrue(json.contains("pipeline-123"));
  }

  @Test
  void jsonDeserialization_fromValidJson_shouldMapAllFields() throws Exception {
    String json =
        """
                {
                  "resourceType": "redpanda-pipeline",
                  "pipeline_id": "pipeline-123",
                  "input": {
                    "mqtt": {
                      "urls": ["tcp://broker:1883"],
                      "topics": ["sensor/data"],
                      "client_id": "client-1",
                      "qos": 1
                    }
                  },
                  "pipeline": {
                    "processors": [
                      {"mapping": "root = this"}
                    ]
                  },
                  "output": {
                    "http_client": {
                      "url": "http://api.example.com/data",
                      "verb": "POST"
                    }
                  }
                }
                """;

    PipelineConfigValue value = objectMapper.readValue(json, PipelineConfigValue.class);
    assertEquals("pipeline-123", value.getPipelineId());

    assertNotNull(value.getInput());
    assertNotNull(value.getInput().getMqtt());
    assertEquals(List.of("tcp://broker:1883"), value.getInput().getMqtt().getUrls());
    assertEquals(List.of("sensor/data"), value.getInput().getMqtt().getTopics());
    assertEquals("client-1", value.getInput().getMqtt().getClientId());
    assertEquals(1, value.getInput().getMqtt().getQos());

    assertNotNull(value.getPipeline());
    assertEquals(1, value.getPipeline().getProcessors().size());
    assertEquals("root = this", value.getPipeline().getProcessors().get(0).getMapping());

    assertNotNull(value.getOutput());
    assertNotNull(value.getOutput().getHttpClient());
    assertEquals("http://api.example.com/data", value.getOutput().getHttpClient().getUrl());
    assertEquals("POST", value.getOutput().getHttpClient().getVerb());
  }

  @Test
  void jsonDeserialization_viaConfigValueInterface_shouldResolveToCorrectType() throws Exception {
    String json =
        """
                {
                  "resourceType": "redpanda-pipeline",
                  "pipeline_id": "pipeline-123"
                }
                """;

    ConfigValue configValue = objectMapper.readValue(json, ConfigValue.class);
    assertInstanceOf(PipelineConfigValue.class, configValue);
    PipelineConfigValue value = (PipelineConfigValue) configValue;
    assertEquals("pipeline-123", value.getPipelineId());
  }

  @Test
  void jsonDeserialization_withMinimalJson_shouldCreateObject() throws Exception {
    String json =
        """
                {
                  "resourceType": "redpanda-pipeline",
                  "pipeline_id": "pipeline-456"
                }
                """;

    PipelineConfigValue value = objectMapper.readValue(json, PipelineConfigValue.class);
    assertEquals("pipeline-456", value.getPipelineId());
    assertNull(value.getInput());
    assertNull(value.getPipeline());
    assertNull(value.getOutput());
  }

  @Test
  void jsonRoundTrip_shouldPreserveValues() throws Exception {
    PipelineConfigValue original = buildFullPipelineConfigValue();

    String json = objectMapper.writeValueAsString(original);
    PipelineConfigValue deserialized = objectMapper.readValue(json, PipelineConfigValue.class);

    assertEquals(original, deserialized);
  }

  @Test
  void jsonDeserialization_withUnknownProperties_shouldCaptureAdditionalProperties()
      throws Exception {
    String json =
        """
                {
                  "resourceType": "redpanda-pipeline",
                  "pipeline_id": "pipeline-123",
                  "logger": {"level": "DEBUG"},
                  "metrics": {"prometheus": {"port": 9090}}
                }
                """;

    PipelineConfigValue value = objectMapper.readValue(json, PipelineConfigValue.class);
    assertEquals("pipeline-123", value.getPipelineId());
    assertNotNull(value.getAdditionalProperties().get("logger"));
    assertNotNull(value.getAdditionalProperties().get("metrics"));
  }

  @Test
  void jsonDeserialization_withSqlRawInput_shouldMapCorrectly() throws Exception {
    String json =
        """
                {
                  "resourceType": "redpanda-pipeline",
                  "pipeline_id": "sql-pipeline",
                  "input": {
                    "sql_raw": {
                      "driver": "postgres",
                      "dsn": "postgres://user:pass@host:5432/db",
                      "query": "SELECT * FROM sensors",
                      "args_mapping": "root = []"
                    }
                  },
                  "output": {
                    "http_client": {
                      "url": "http://api.example.com/data",
                      "verb": "POST"
                    }
                  }
                }
                """;

    PipelineConfigValue value = objectMapper.readValue(json, PipelineConfigValue.class);
    assertEquals("sql-pipeline", value.getPipelineId());
    assertNotNull(value.getInput());
    assertNull(value.getInput().getMqtt());
    assertNotNull(value.getInput().getSqlRaw());
    assertEquals("postgres", value.getInput().getSqlRaw().getDriver());
    assertEquals("root = []", value.getInput().getSqlRaw().getArgsMapping());
  }

  @Test
  void jsonDeserialization_withBasicAuth_shouldMapCorrectly() throws Exception {
    String json =
        """
                {
                  "resourceType": "redpanda-pipeline",
                  "pipeline_id": "auth-pipeline",
                  "output": {
                    "http_client": {
                      "url": "http://api.example.com/data",
                      "verb": "POST",
                      "basic_auth": {
                        "username": "admin",
                        "password": "secret"
                      }
                    }
                  }
                }
                """;

    PipelineConfigValue value = objectMapper.readValue(json, PipelineConfigValue.class);
    assertNotNull(value.getOutput());
    assertNotNull(value.getOutput().getHttpClient());
    assertNotNull(value.getOutput().getHttpClient().getBasicAuth());
    assertEquals("admin", value.getOutput().getHttpClient().getBasicAuth().getUsername());
    assertEquals("secret", value.getOutput().getHttpClient().getBasicAuth().getPassword());
  }

  private PipelineConfigValue buildFullPipelineConfigValue() {
    PipelineConfigValue value = new PipelineConfigValue();
    value.setPipelineId("pipeline-123");

    MqttInput mqtt = new MqttInput();
    mqtt.setUrls(List.of("tcp://broker:1883"));
    mqtt.setTopics(List.of("sensor/data"));
    mqtt.setClientId("client-1");
    mqtt.setQos(1);
    mqtt.setConnectTimeout("30s");
    mqtt.setKeepalive(60);
    PipelineInput input = new PipelineInput();
    input.setMqtt(mqtt);
    value.setInput(input);

    ProcessorStep step = new ProcessorStep();
    step.setMapping("root.temperature = this.temp_c * 9/5 + 32");
    PipelineProcessors pipeline = new PipelineProcessors();
    pipeline.setProcessors(List.of(step));
    value.setPipeline(pipeline);

    BasicAuth auth = new BasicAuth();
    auth.setUsername("admin");
    auth.setPassword("secret");
    HttpClientOutput httpClient = new HttpClientOutput();
    httpClient.setUrl("http://api.example.com/data");
    httpClient.setVerb("POST");
    httpClient.setHeaders(Map.of("Content-Type", "application/json"));
    httpClient.setTimeout("30s");
    httpClient.setMaxInFlight(64);
    httpClient.setBasicAuth(auth);
    PipelineOutput output = new PipelineOutput();
    output.setHttpClient(httpClient);
    value.setOutput(output);

    return value;
  }
}
