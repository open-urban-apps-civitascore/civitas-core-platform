/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.application;

import de.civitascore.configadapter.adapter.AbstractConfigAdapter;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;

/**
 * Second minimal adapter for ApplicationTest. Used to test multiple adapter configurations without
 * requiring external services like Keycloak.
 */
public class TestDummyAdapter2 extends AbstractConfigAdapter {

  @Override
  public String getName() {
    return "dummylog2";
  }

  @Override
  protected String getResultType() {
    return "de.civitascore.test.processing.result2";
  }

  @Override
  protected String getAdapterSource() {
    return "de.civitascore.config-adapter.test-dummy2";
  }

  @Override
  protected void doProcessConfigEvent(String topic, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    if (getEventPublisher() != null && event.metadata().resultTopic() != null) {
      getEventPublisher()
          .publish(
              event.metadata().resultTopic(),
              ConfigResultEvent.success(
                  event.metadata().correlationId(),
                  event.metadata().messageId(),
                  "Test dummy2 processed",
                  event.payload().targetResource(),
                  event.payload().operation(),
                  event.payload().targetResource(),
                  getAdapterSource(),
                  getResultType()));
    }
  }

  @Override
  public void close() {}
}
