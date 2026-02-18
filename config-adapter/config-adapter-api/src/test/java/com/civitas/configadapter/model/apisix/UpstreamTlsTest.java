/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.model.apisix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link UpstreamTls}. */
class UpstreamTlsTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void constructor_whenNoArgs_shouldHaveNullFields() {
    UpstreamTls tls = new UpstreamTls();
    assertNull(tls.getSni());
    assertNull(tls.getVerify());
    assertNull(tls.getClientCert());
    assertNull(tls.getClientKey());
    assertNull(tls.getClientCertId());
    assertNotNull(tls.getAdditionalProperties());
    assertTrue(tls.getAdditionalProperties().isEmpty());
  }

  @Test
  void setters_whenCalled_shouldStoreValues() {
    UpstreamTls tls = new UpstreamTls();
    tls.setSni("backend.example.com");
    tls.setVerify(true);
    tls.setClientCert("cert-data");
    tls.setClientKey("key-data");

    assertEquals("backend.example.com", tls.getSni());
    assertEquals(true, tls.getVerify());
    assertEquals("cert-data", tls.getClientCert());
    assertEquals("key-data", tls.getClientKey());
  }

  @Test
  void toApiMap_allFields_shouldContainAll() {
    UpstreamTls tls = new UpstreamTls();
    tls.setSni("backend.example.com");
    tls.setVerify(true);
    tls.setClientCert("cert-data");
    tls.setClientKey("key-data");

    Map<String, Object> map = tls.toApiMap();
    assertEquals("backend.example.com", map.get("sni"));
    assertEquals(true, map.get("verify"));
    assertEquals("cert-data", map.get("client_cert"));
    assertEquals("key-data", map.get("client_key"));
  }

  @Test
  void toApiMap_noFields_shouldReturnEmpty() {
    UpstreamTls tls = new UpstreamTls();
    Map<String, Object> map = tls.toApiMap();
    assertTrue(map.isEmpty());
  }

  @Test
  void toApiMap_partialFields_shouldOmitNull() {
    UpstreamTls tls = new UpstreamTls();
    tls.setSni("backend.example.com");

    Map<String, Object> map = tls.toApiMap();
    assertEquals(1, map.size());
    assertEquals("backend.example.com", map.get("sni"));
    assertNull(map.get("verify"));
  }

  @Test
  void jsonSerialization_withSnakeCaseFields_shouldSerializeCorrectly() throws Exception {
    UpstreamTls tls = new UpstreamTls();
    tls.setSni("backend.example.com");
    tls.setVerify(true);
    tls.setClientCert("cert-data");
    tls.setClientKey("key-data");

    String json = objectMapper.writeValueAsString(tls);
    assertTrue(json.contains("\"client_cert\""));
    assertTrue(json.contains("\"client_key\""));
    assertTrue(json.contains("\"sni\""));
    assertTrue(json.contains("\"verify\""));
  }

  @Test
  void jsonDeserialization_fromValidJson_shouldMapSnakeCaseFields() throws Exception {
    String json =
        """
                {
                  "sni": "backend.example.com",
                  "verify": true,
                  "client_cert": "cert-data",
                  "client_key": "key-data"
                }
                """;

    UpstreamTls tls = objectMapper.readValue(json, UpstreamTls.class);
    assertEquals("backend.example.com", tls.getSni());
    assertEquals(true, tls.getVerify());
    assertEquals("cert-data", tls.getClientCert());
    assertEquals("key-data", tls.getClientKey());
  }

  @Test
  void additionalProperties_shouldBeUnmodifiable() {
    UpstreamTls tls = new UpstreamTls();
    tls.handleUnknownProperty("custom_prop", "value");

    assertThrows(
        UnsupportedOperationException.class,
        () -> tls.getAdditionalProperties().put("new_key", "new_value"));
  }

  @Test
  void handleUnknownProperty_shouldStoreInAdditionalProperties() {
    UpstreamTls tls = new UpstreamTls();
    tls.handleUnknownProperty("custom_prop", "value");
    assertEquals("value", tls.getAdditionalProperties().get("custom_prop"));
  }

  @Test
  void toApiMap_shouldIncludeAdditionalProperties() {
    UpstreamTls tls = new UpstreamTls();
    tls.setSni("backend.example.com");
    tls.handleUnknownProperty("custom_prop", "value");

    Map<String, Object> map = tls.toApiMap();
    assertEquals("backend.example.com", map.get("sni"));
    assertEquals("value", map.get("custom_prop"));
  }

  @Test
  void jsonRoundTrip_shouldPreserveValues() throws Exception {
    UpstreamTls original = new UpstreamTls();
    original.setSni("backend.example.com");
    original.setVerify(true);
    original.setClientCert("cert-data");
    original.setClientKey("key-data");

    String json = objectMapper.writeValueAsString(original);
    UpstreamTls deserialized = objectMapper.readValue(json, UpstreamTls.class);

    assertEquals(original, deserialized);
  }

  @Test
  void toString_shouldNotLeakSensitiveData() {
    UpstreamTls tls = new UpstreamTls();
    tls.setClientCert("sensitive-cert");
    tls.setClientKey("sensitive-key");

    String str = tls.toString();
    assertTrue(str.contains("[PRESENT]"));
    assertTrue(!str.contains("sensitive-cert"));
    assertTrue(!str.contains("sensitive-key"));
  }

  @Test
  void setClientCertId_whenCalled_shouldStoreValue() {
    UpstreamTls tls = new UpstreamTls();
    tls.setClientCertId("ssl-cert-ref-123");
    assertEquals("ssl-cert-ref-123", tls.getClientCertId());
  }

  @Test
  void toApiMap_withClientCertId_shouldContainField() {
    UpstreamTls tls = new UpstreamTls();
    tls.setSni("backend.example.com");
    tls.setClientCertId("ssl-cert-ref-123");

    Map<String, Object> map = tls.toApiMap();
    assertEquals("backend.example.com", map.get("sni"));
    assertEquals("ssl-cert-ref-123", map.get("client_cert_id"));
    assertNull(map.get("client_cert"));
    assertNull(map.get("client_key"));
  }

  @Test
  void jsonDeserialization_withClientCertId_shouldMapField() throws Exception {
    String json =
        """
                {
                  "sni": "backend.example.com",
                  "verify": true,
                  "client_cert_id": "ssl-cert-ref-123"
                }
                """;

    UpstreamTls tls = objectMapper.readValue(json, UpstreamTls.class);
    assertEquals("backend.example.com", tls.getSni());
    assertEquals(true, tls.getVerify());
    assertEquals("ssl-cert-ref-123", tls.getClientCertId());
    assertNull(tls.getClientCert());
    assertNull(tls.getClientKey());
  }

  @Test
  void jsonSerialization_withClientCertId_shouldSerializeCorrectly() throws Exception {
    UpstreamTls tls = new UpstreamTls();
    tls.setSni("backend.example.com");
    tls.setClientCertId("ssl-cert-ref-123");

    String json = objectMapper.writeValueAsString(tls);
    assertTrue(json.contains("\"client_cert_id\""));
    assertTrue(json.contains("ssl-cert-ref-123"));
  }

  @Test
  void jsonRoundTrip_withClientCertId_shouldPreserveValues() throws Exception {
    UpstreamTls original = new UpstreamTls();
    original.setSni("backend.example.com");
    original.setVerify(true);
    original.setClientCertId("ssl-cert-ref-123");

    String json = objectMapper.writeValueAsString(original);
    UpstreamTls deserialized = objectMapper.readValue(json, UpstreamTls.class);

    assertEquals(original, deserialized);
  }

  @Test
  void toString_withClientCertId_shouldShowValue() {
    UpstreamTls tls = new UpstreamTls();
    tls.setClientCertId("ssl-cert-ref-123");

    String str = tls.toString();
    assertTrue(str.contains("clientCertId=ssl-cert-ref-123"));
  }

  @Test
  void equals_differentClientCertId_shouldNotBeEqual() {
    UpstreamTls tls1 = new UpstreamTls();
    tls1.setClientCertId("id-1");
    UpstreamTls tls2 = new UpstreamTls();
    tls2.setClientCertId("id-2");
    assertNotEquals(tls1, tls2);
  }
}
