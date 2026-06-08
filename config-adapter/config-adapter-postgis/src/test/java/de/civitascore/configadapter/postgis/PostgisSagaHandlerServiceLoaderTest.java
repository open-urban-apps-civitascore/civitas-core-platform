/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.postgis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.adapter.SagaCommandHandler;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@code META-INF/services} registration so the application's {@code
 * SagaComponentFactory} discovers {@link PostgisSagaHandler} at runtime under the {@code postgis}
 * adapter name.
 */
class PostgisSagaHandlerServiceLoaderTest {

  @Test
  void serviceLoaderDiscoversPostgisSagaHandler() {
    ServiceLoader<SagaCommandHandler> loader = ServiceLoader.load(SagaCommandHandler.class);

    PostgisSagaHandler discovered = null;
    for (SagaCommandHandler handler : loader) {
      if (handler instanceof PostgisSagaHandler postgisHandler) {
        discovered = postgisHandler;
        break;
      }
    }

    assertTrue(discovered != null, "ServiceLoader should discover PostgisSagaHandler");
    assertEquals("postgis", discovered.adapter());
  }
}
