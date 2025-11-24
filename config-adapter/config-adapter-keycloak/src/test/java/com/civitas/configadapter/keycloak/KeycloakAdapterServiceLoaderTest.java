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
package com.civitas.configadapter.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.civitas.configadapter.adapter.ConfigAdapter;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@link ServiceLoader} configuration
 *
 * @author Mark Hoffmann
 * @since 24.11.2025
 */
public class KeycloakAdapterServiceLoaderTest {

  @Test
  public void testServiceLoader() {
    ServiceLoader<ConfigAdapter> caSL = ServiceLoader.load(ConfigAdapter.class);
    assertNotNull(caSL);
    assertEquals(1, caSL.stream().count());
    ConfigAdapter ca = caSL.iterator().next();
    assertNotNull(ca);
    assertEquals(KeycloakAdapter.ADAPTER_NAME, ca.getName());
  }
}
