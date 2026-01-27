/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.adapter;

import com.civitas.configadapter.ConfigBase;
import com.civitas.configadapter.exception.AdapterException;
import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.exception.RetryableAdapterException;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.ConfigEvent;
import java.util.List;

public interface ConfigAdapter extends ConfigBase {

  void processConfigEvent(String topic, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException;

  List<String> getSubscribedTopics();

  void setEventPublisher(EventPublisher publisher);

  /**
   * Publishes a failure result event for the given config event and exception. This is a
   * best-effort operation — exceptions during publishing are caught and logged.
   *
   * @param event the original config event (may be null)
   * @param exception the adapter exception that caused the failure
   */
  void publishFailureResult(ConfigEvent event, AdapterException exception);
}
