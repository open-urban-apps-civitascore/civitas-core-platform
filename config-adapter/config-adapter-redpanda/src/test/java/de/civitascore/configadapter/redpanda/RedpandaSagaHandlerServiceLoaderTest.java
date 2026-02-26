/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.redpanda;

import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.adapter.SagaCommandHandler;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class RedpandaSagaHandlerServiceLoaderTest {

  @Test
  void serviceLoader_discovery_findsRedpandaSagaHandler() {
    ServiceLoader<SagaCommandHandler> loader = ServiceLoader.load(SagaCommandHandler.class);
    boolean found = false;
    for (SagaCommandHandler handler : loader) {
      if (handler instanceof RedpandaSagaHandler) {
        found = true;
        break;
      }
    }
    assertTrue(found, "ServiceLoader should discover RedpandaSagaHandler");
  }
}
