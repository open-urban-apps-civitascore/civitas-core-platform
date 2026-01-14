/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
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
