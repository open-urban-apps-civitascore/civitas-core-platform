/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.redpanda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PipelineOutput}. */
class PipelineOutputTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void constructor_whenNoArgs_shouldHaveNullFields() {
    PipelineOutput output = new PipelineOutput();
    assertNull(output.getHttpClient());
    assertNotNull(output.getAdditionalProperties());
    assertTrue(output.getAdditionalProperties().isEmpty());
  }

  @Test
  void setters_whenCalled_shouldStoreValues() {
    PipelineOutput output = new PipelineOutput();
    HttpClientOutput httpClient = new HttpClientOutput();
    httpClient.setUrl("http://api.example.com/data");
    output.setHttpClient(httpClient);

    assertNotNull(output.getHttpClient());
    assertEquals("http://api.example.com/data", output.getHttpClient().getUrl());
  }

  @Test
  void toApiMap_whenHttpClientSet_shouldContainNestedMap() {
    HttpClientOutput httpClient = new HttpClientOutput();
    httpClient.setUrl("http://api.example.com/data");
    httpClient.setVerb("POST");
    PipelineOutput output = new PipelineOutput();
    output.setHttpClient(httpClient);

    Map<String, Object> map = output.toApiMap();
    @SuppressWarnings("unchecked")
    Map<String, Object> httpClientMap = (Map<String, Object>) map.get("http_client");
    assertNotNull(httpClientMap);
    assertEquals("http://api.example.com/data", httpClientMap.get("url"));
    assertEquals("POST", httpClientMap.get("verb"));
  }

  @Test
  void toApiMap_whenEmpty_shouldReturnEmptyMap() {
    PipelineOutput output = new PipelineOutput();
    Map<String, Object> map = output.toApiMap();
    assertTrue(map.isEmpty());
  }

  @Test
  void toApiMap_shouldIncludeAdditionalProperties() {
    PipelineOutput output = new PipelineOutput();
    output.handleUnknownProperty("kafka", Map.of("addresses", "broker:9092"));

    Map<String, Object> map = output.toApiMap();
    assertNotNull(map.get("kafka"));
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    HttpClientOutput httpClient = new HttpClientOutput();
    httpClient.setUrl("http://api.example.com/data");
    PipelineOutput output1 = new PipelineOutput();
    output1.setHttpClient(httpClient);
    HttpClientOutput httpClient2 = new HttpClientOutput();
    httpClient2.setUrl("http://api.example.com/data");
    PipelineOutput output2 = new PipelineOutput();
    output2.setHttpClient(httpClient2);

    assertEquals(output1, output2);
    assertEquals(output1.hashCode(), output2.hashCode());
  }

  @Test
  void equals_whenSameObject_shouldReturnTrue() {
    PipelineOutput output = new PipelineOutput();
    assertEquals(output, output);
  }

  @Test
  void equals_whenNull_shouldReturnFalse() {
    PipelineOutput output = new PipelineOutput();
    assertNotEquals(null, output);
  }

  @Test
  void equals_whenDifferentClass_shouldReturnFalse() {
    PipelineOutput output = new PipelineOutput();
    assertNotEquals("string", output);
  }

  @Test
  void equals_whenDifferentHttpClient_shouldReturnFalse() {
    HttpClientOutput hc1 = new HttpClientOutput();
    hc1.setUrl("http://api1.example.com");
    PipelineOutput output1 = new PipelineOutput();
    output1.setHttpClient(hc1);

    HttpClientOutput hc2 = new HttpClientOutput();
    hc2.setUrl("http://api2.example.com");
    PipelineOutput output2 = new PipelineOutput();
    output2.setHttpClient(hc2);

    assertNotEquals(output1, output2);
  }

  @Test
  void toString_whenCalled_shouldContainClassName() {
    PipelineOutput output = new PipelineOutput();
    HttpClientOutput httpClient = new HttpClientOutput();
    httpClient.setUrl("http://api.example.com/data");
    output.setHttpClient(httpClient);

    String str = output.toString();
    assertTrue(str.contains("PipelineOutput"));
    assertTrue(str.contains("httpClient="));
  }

  @Test
  void jsonSerialization_shouldUseSnakeCaseForAnnotatedFields() throws Exception {
    HttpClientOutput httpClient = new HttpClientOutput();
    httpClient.setUrl("http://api.example.com/data");
    PipelineOutput output = new PipelineOutput();
    output.setHttpClient(httpClient);

    String json = objectMapper.writeValueAsString(output);
    assertTrue(json.contains("\"http_client\""));
  }

  @Test
  void jsonDeserialization_fromValidJson_shouldMapSnakeCaseFields() throws Exception {
    String json =
        """
                {
                  "http_client": {
                    "url": "http://api.example.com/data",
                    "verb": "POST",
                    "timeout": "30s"
                  }
                }
                """;

    PipelineOutput output = objectMapper.readValue(json, PipelineOutput.class);
    assertNotNull(output.getHttpClient());
    assertEquals("http://api.example.com/data", output.getHttpClient().getUrl());
    assertEquals("POST", output.getHttpClient().getVerb());
    assertEquals("30s", output.getHttpClient().getTimeout());
  }

  @Test
  void jsonRoundTrip_shouldPreserveValues() throws Exception {
    HttpClientOutput httpClient = new HttpClientOutput();
    httpClient.setUrl("http://api.example.com/data");
    httpClient.setVerb("POST");
    httpClient.setTimeout("30s");
    httpClient.setMaxInFlight(64);
    PipelineOutput original = new PipelineOutput();
    original.setHttpClient(httpClient);

    String json = objectMapper.writeValueAsString(original);
    PipelineOutput deserialized = objectMapper.readValue(json, PipelineOutput.class);

    assertEquals(original, deserialized);
  }

  @Test
  void jsonDeserialization_withUnknownProperties_shouldCaptureAdditionalProperties()
      throws Exception {
    String json =
        """
                {
                  "kafka": {"addresses": ["broker:9092"], "topic": "output-topic"}
                }
                """;

    PipelineOutput output = objectMapper.readValue(json, PipelineOutput.class);
    assertNull(output.getHttpClient());
    assertNotNull(output.getAdditionalProperties().get("kafka"));
  }
}
