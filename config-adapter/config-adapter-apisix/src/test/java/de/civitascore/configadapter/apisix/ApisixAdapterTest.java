/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.apisix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.configuration.AdapterConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for ApisixAdapter initialization and lifecycle. */
class ApisixAdapterTest {

  private ApisixAdapter adapter;
  private AdapterConfig mockConfig;

  @BeforeEach
  void setUp() {
    adapter = new ApisixAdapter();
    mockConfig = mock(AdapterConfig.class);
  }

  @Test
  void testAdapterName() {
    assertEquals("apisix", adapter.getName());
  }

  @Test
  void testInitializationWithDefaultValues() {
    when(mockConfig.getProperty("apisix.admin.key")).thenReturn("edd1c9f034335f136f87ad84b625c8f1");
    when(mockConfig.getProperty("apisix.topics"))
        .thenReturn(
            "de.civitascore.api.backend.created,de.civitascore.api.backend.updated,de.civitascore.api.backend.deleted");

    adapter.initialize(mockConfig);

    List<String> subscribedTopics = adapter.getSubscribedTopics();
    assertNotNull(subscribedTopics);
    assertEquals(3, subscribedTopics.size());
    assertTrue(subscribedTopics.contains("de.civitascore.api.backend.created"));
    assertTrue(subscribedTopics.contains("de.civitascore.api.backend.updated"));
    assertTrue(subscribedTopics.contains("de.civitascore.api.backend.deleted"));
  }

  @Test
  void testInitializationWithCustomAdminUrl() {
    when(mockConfig.getProperty("apisix.topics")).thenReturn("de.civitascore.api.backend.created");
    when(mockConfig.getProperty("apisix.admin.key")).thenReturn("edd1c9f034335f136f87ad84b625c8f1");
    when(mockConfig.getProperty("apisix.admin.url")).thenReturn("http://custom-apisix:9180");

    adapter.initialize(mockConfig);

    assertNotNull(adapter.getSubscribedTopics());
    assertEquals(1, adapter.getSubscribedTopics().size());
  }

  @Test
  void testInitializationWithOutAdminKey() {
    when(mockConfig.getProperty("apisix.topics")).thenReturn("de.civitascore.api.backend.created");

    try {
      adapter.initialize(mockConfig);
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException e) {
      assertEquals("The APISIX admin key cannot be null or blank.", e.getMessage());
    }

    assertNotNull(adapter.getSubscribedTopics());
    assertEquals(1, adapter.getSubscribedTopics().size());
  }

  @Test
  void testCloseAdapter() {
    when(mockConfig.getProperty("apisix.topics")).thenReturn("de.civitascore.api.backend.created");
    when(mockConfig.getProperty("apisix.admin.key")).thenReturn("edd1c9f034335f136f87ad84b625c8f1");

    adapter.initialize(mockConfig);
    adapter.close();
  }
}
