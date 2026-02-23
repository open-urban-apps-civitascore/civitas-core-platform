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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civitas.configadapter.model.BasicAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link HttpClientOutput}. */
class HttpClientOutputTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void constructor_whenNoArgs_shouldHaveNullFields() {
    HttpClientOutput output = new HttpClientOutput();
    assertNull(output.getUrl());
    assertNull(output.getVerb());
    assertNull(output.getHeaders());
    assertNull(output.getRateLimit());
    assertNull(output.getTimeout());
    assertNull(output.getMaxInFlight());
    assertNull(output.getBasicAuth());
    assertNotNull(output.getAdditionalProperties());
    assertTrue(output.getAdditionalProperties().isEmpty());
  }

  @Test
  void setters_whenCalled_shouldStoreValues() {
    HttpClientOutput output = new HttpClientOutput();
    output.setUrl("http://api.example.com/data");
    output.setVerb("POST");
    output.setHeaders(Map.of("Content-Type", "application/json"));
    output.setRateLimit("10/s");
    output.setTimeout("30s");
    output.setMaxInFlight(64);
    BasicAuth auth = new BasicAuth();
    auth.setUsername("admin");
    output.setBasicAuth(auth);

    assertEquals("http://api.example.com/data", output.getUrl());
    assertEquals("POST", output.getVerb());
    assertEquals(Map.of("Content-Type", "application/json"), output.getHeaders());
    assertEquals("10/s", output.getRateLimit());
    assertEquals("30s", output.getTimeout());
    assertEquals(64, output.getMaxInFlight());
    assertEquals(auth, output.getBasicAuth());
  }

  @Test
  void toApiMap_whenAllFieldsSet_shouldContainAll() {
    HttpClientOutput output = new HttpClientOutput();
    output.setUrl("http://api.example.com/data");
    output.setVerb("POST");
    output.setHeaders(Map.of("Content-Type", "application/json"));
    output.setRateLimit("10/s");
    output.setTimeout("30s");
    output.setMaxInFlight(64);
    BasicAuth auth = new BasicAuth();
    auth.setUsername("admin");
    auth.setPassword("secret");
    output.setBasicAuth(auth);

    Map<String, Object> map = output.toApiMap();
    assertEquals("http://api.example.com/data", map.get("url"));
    assertEquals("POST", map.get("verb"));
    assertEquals(Map.of("Content-Type", "application/json"), map.get("headers"));
    assertEquals("10/s", map.get("rate_limit"));
    assertEquals("30s", map.get("timeout"));
    assertEquals(64, map.get("max_in_flight"));

    @SuppressWarnings("unchecked")
    Map<String, Object> authMap = (Map<String, Object>) map.get("basic_auth");
    assertNotNull(authMap);
    assertEquals("admin", authMap.get("username"));
    assertEquals("secret", authMap.get("password"));
  }

  @Test
  void toApiMap_whenEmpty_shouldReturnEmptyMap() {
    HttpClientOutput output = new HttpClientOutput();
    Map<String, Object> map = output.toApiMap();
    assertTrue(map.isEmpty());
  }

  @Test
  void toApiMap_whenPartialFields_shouldOmitNull() {
    HttpClientOutput output = new HttpClientOutput();
    output.setUrl("http://api.example.com/data");
    output.setVerb("POST");

    Map<String, Object> map = output.toApiMap();
    assertEquals(2, map.size());
    assertEquals("http://api.example.com/data", map.get("url"));
    assertEquals("POST", map.get("verb"));
    assertNull(map.get("basic_auth"));
  }

  @Test
  void toApiMap_shouldIncludeAdditionalProperties() {
    HttpClientOutput output = new HttpClientOutput();
    output.setUrl("http://api.example.com/data");
    output.handleUnknownProperty("retries", 3);

    Map<String, Object> map = output.toApiMap();
    assertEquals("http://api.example.com/data", map.get("url"));
    assertEquals(3, map.get("retries"));
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    HttpClientOutput output1 = new HttpClientOutput();
    output1.setUrl("http://api.example.com/data");
    output1.setVerb("POST");
    HttpClientOutput output2 = new HttpClientOutput();
    output2.setUrl("http://api.example.com/data");
    output2.setVerb("POST");

    assertEquals(output1, output2);
    assertEquals(output1.hashCode(), output2.hashCode());
  }

  @Test
  void equals_whenSameObject_shouldReturnTrue() {
    HttpClientOutput output = new HttpClientOutput();
    assertEquals(output, output);
  }

  @Test
  void equals_whenNull_shouldReturnFalse() {
    HttpClientOutput output = new HttpClientOutput();
    assertNotEquals(null, output);
  }

  @Test
  void equals_whenDifferentClass_shouldReturnFalse() {
    HttpClientOutput output = new HttpClientOutput();
    assertNotEquals("string", output);
  }

  @Test
  void equals_whenDifferentUrl_shouldReturnFalse() {
    HttpClientOutput output1 = new HttpClientOutput();
    output1.setUrl("http://api1.example.com");
    HttpClientOutput output2 = new HttpClientOutput();
    output2.setUrl("http://api2.example.com");

    assertNotEquals(output1, output2);
  }

  @Test
  void equals_whenDifferentBasicAuth_shouldReturnFalse() {
    HttpClientOutput output1 = new HttpClientOutput();
    BasicAuth auth1 = new BasicAuth();
    auth1.setUsername("admin");
    output1.setBasicAuth(auth1);
    HttpClientOutput output2 = new HttpClientOutput();
    BasicAuth auth2 = new BasicAuth();
    auth2.setUsername("other");
    output2.setBasicAuth(auth2);

    assertNotEquals(output1, output2);
  }

  @Test
  void toString_whenCalled_shouldContainClassName() {
    HttpClientOutput output = new HttpClientOutput();
    output.setUrl("http://api.example.com/data");
    output.setVerb("POST");

    String str = output.toString();
    assertTrue(str.contains("HttpClientOutput"));
    assertTrue(str.contains("http://api.example.com/data"));
    assertTrue(str.contains("POST"));
  }

  @Test
  void jsonSerialization_shouldUseSnakeCaseForAnnotatedFields() throws Exception {
    HttpClientOutput output = new HttpClientOutput();
    output.setRateLimit("10/s");
    output.setMaxInFlight(64);
    BasicAuth auth = new BasicAuth();
    auth.setUsername("admin");
    output.setBasicAuth(auth);

    String json = objectMapper.writeValueAsString(output);
    assertTrue(json.contains("\"rate_limit\""));
    assertTrue(json.contains("\"max_in_flight\""));
    assertTrue(json.contains("\"basic_auth\""));
  }

  @Test
  void jsonDeserialization_fromValidJson_shouldMapSnakeCaseFields() throws Exception {
    String json =
        """
                {
                  "url": "http://api.example.com/data",
                  "verb": "POST",
                  "headers": {"Content-Type": "application/json"},
                  "rate_limit": "10/s",
                  "timeout": "30s",
                  "max_in_flight": 64,
                  "basic_auth": {
                    "username": "admin",
                    "password": "secret"
                  }
                }
                """;

    HttpClientOutput output = objectMapper.readValue(json, HttpClientOutput.class);
    assertEquals("http://api.example.com/data", output.getUrl());
    assertEquals("POST", output.getVerb());
    assertEquals("application/json", output.getHeaders().get("Content-Type"));
    assertEquals("10/s", output.getRateLimit());
    assertEquals("30s", output.getTimeout());
    assertEquals(64, output.getMaxInFlight());
    assertNotNull(output.getBasicAuth());
    assertEquals("admin", output.getBasicAuth().getUsername());
    assertEquals("secret", output.getBasicAuth().getPassword());
  }

  @Test
  void jsonRoundTrip_shouldPreserveValues() throws Exception {
    HttpClientOutput original = new HttpClientOutput();
    original.setUrl("http://api.example.com/data");
    original.setVerb("POST");
    original.setHeaders(Map.of("Content-Type", "application/json"));
    original.setRateLimit("10/s");
    original.setTimeout("30s");
    original.setMaxInFlight(64);
    BasicAuth auth = new BasicAuth();
    auth.setUsername("admin");
    auth.setPassword("secret");
    original.setBasicAuth(auth);

    String json = objectMapper.writeValueAsString(original);
    HttpClientOutput deserialized = objectMapper.readValue(json, HttpClientOutput.class);

    assertEquals(original, deserialized);
  }

  @Test
  void jsonDeserialization_withUnknownProperties_shouldCaptureAdditionalProperties()
      throws Exception {
    String json =
        """
                {
                  "url": "http://api.example.com/data",
                  "tls": {"enabled": true}
                }
                """;

    HttpClientOutput output = objectMapper.readValue(json, HttpClientOutput.class);
    assertEquals("http://api.example.com/data", output.getUrl());
    assertNotNull(output.getAdditionalProperties().get("tls"));
  }
}
