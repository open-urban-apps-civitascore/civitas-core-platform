/**
 * Copyright (c) 2012 - 2025 Data In Motion and others. All rights reserved.
 *
 * <p>This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * <p>SPDX-License-Identifier: EPL-2.0
 *
 * <p>Contributors: Data In Motion - initial API and implementation
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
