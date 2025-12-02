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
package com.civitas.configadapter.apisix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.civitas.configadapter.adapter.ConfigAdapter;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

/** Tests the {@link ServiceLoader} configuration for ApisixAdapter */
class ApisixAdapterServiceLoaderTest {

  @Test
  void testServiceLoaderDiscoversApisixAdapter() {
    ServiceLoader<ConfigAdapter> serviceLoader = ServiceLoader.load(ConfigAdapter.class);
    assertNotNull(serviceLoader);
    assertEquals(1, serviceLoader.stream().count());
    ConfigAdapter adapter = serviceLoader.iterator().next();
    assertNotNull(adapter);
    assertEquals(ApisixAdapter.ADAPTER_NAME, adapter.getName());
  }
}
