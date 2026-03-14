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
 * Minimal adapter for ApplicationTest. Replaces the external DummyLogAdapter dependency so that
 * config-adapter-application tests do not depend on config-adapter-examples.
 */
public class TestDummyAdapter extends AbstractConfigAdapter {

  @Override
  public String getName() {
    return "dummylog";
  }

  @Override
  protected String getResultType() {
    return "de.civitascore.test.processing.result";
  }

  @Override
  protected String getAdapterSource() {
    return "de.civitascore.config-adapter.test-dummy";
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
                  "Test dummy processed",
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
