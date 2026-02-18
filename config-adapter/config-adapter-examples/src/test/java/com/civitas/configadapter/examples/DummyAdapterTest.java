/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.keycloak.KeycloakAdapter;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

/**
 * Tests the ServiceLoader Setup
 *
 * @author Mark Hoffmann
 * @since 24.11.2025
 */
public class DummyAdapterTest {

  /**
   * We have an additional test dependency to the keycloak adapter configured. So we should have
   * multiple implementations for the {@link ConfigAdapter} service
   */
  @Test
  public void testServiceLoader() {
    ServiceLoader<ConfigAdapter> caSL = ServiceLoader.load(ConfigAdapter.class);
    assertNotNull(caSL);
    List<String> names = caSL.stream().map(p -> p.get().getName()).toList();
    assertEquals(2, names.size());
    assertTrue(names.contains(DummyLogAdapter.ADAPTER_NAME));
    assertTrue(names.contains(KeycloakAdapter.ADAPTER_NAME));
  }
}
