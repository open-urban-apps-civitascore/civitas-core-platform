/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.idm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that GroupConfig round-trips cleanly through Jackson with the same configuration the
 * Keycloak adapter uses (FAIL_ON_UNKNOWN_PROPERTIES disabled, default ObjectMapper). Guards against
 * regressions like the Jackson Scala module ServiceLoader issue noted in CLAUDE.md memory: a
 * silently-loaded module module can corrupt deserialization shape.
 */
@DisplayName("GroupConfig Jackson round-trip")
class GroupConfigJacksonTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  @Test
  @DisplayName("Round-trips id, name, path, parentId without loss")
  void shouldRoundTripCoreFields() throws Exception {
    GroupConfig original = new GroupConfig();
    original.setId("kc-id-123");
    original.setName("Engineers");
    original.setPath("/engineers");
    original.setParentId("kc-parent-456");

    String json = objectMapper.writeValueAsString(original);
    GroupConfig deserialized = objectMapper.readValue(json, GroupConfig.class);

    assertEquals("kc-id-123", deserialized.getId());
    assertEquals("Engineers", deserialized.getName());
    assertEquals("/engineers", deserialized.getPath());
    assertEquals("kc-parent-456", deserialized.getParentId());
  }

  @Test
  @DisplayName("Null fields round-trip as null (not as empty strings or omitted)")
  void shouldRoundTripNulls() throws Exception {
    GroupConfig original = new GroupConfig();
    original.setName("OnlyName");

    String json = objectMapper.writeValueAsString(original);
    GroupConfig deserialized = objectMapper.readValue(json, GroupConfig.class);

    assertEquals("OnlyName", deserialized.getName());
    assertNull(deserialized.getId());
    assertNull(deserialized.getParentId());
  }

  @Test
  @DisplayName("Unknown JSON properties are tolerated when FAIL_ON_UNKNOWN_PROPERTIES is disabled")
  void shouldTolerateUnknownProperties() throws Exception {
    GroupConfig original = new GroupConfig();
    original.setName("Engineers");

    // Serialize to a tree so the polymorphism discriminator the writer adds is preserved,
    // then add an unknown sibling field — the reader must tolerate the unknown without failing.
    ObjectNode node = (ObjectNode) objectMapper.valueToTree(original);
    node.put("unknownField", "oh no");

    GroupConfig deserialized = objectMapper.treeToValue(node, GroupConfig.class);

    assertEquals("Engineers", deserialized.getName());
  }
}
