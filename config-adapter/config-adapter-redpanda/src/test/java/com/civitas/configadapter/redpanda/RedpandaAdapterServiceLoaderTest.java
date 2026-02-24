/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.redpanda;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civitas.configadapter.adapter.ConfigAdapter;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class RedpandaAdapterServiceLoaderTest {

  @Test
  void serviceLoader_discovery_findsRedpandaAdapter() {
    ServiceLoader<ConfigAdapter> loader = ServiceLoader.load(ConfigAdapter.class);
    boolean found = false;
    for (ConfigAdapter adapter : loader) {
      if (adapter instanceof RedpandaAdapter) {
        found = true;
        break;
      }
    }
    assertTrue(found, "ServiceLoader should discover RedpandaAdapter");
  }
}
