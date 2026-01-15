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
package com.civitas.configadapter.messaging;

import com.civitas.configadapter.model.ConfigResultEvent;

/**
 * Interface for publishing events from adapters. Adapters can use this to send result events, error
 * notifications, or status updates.
 */
public interface EventPublisher extends EventBase {

  /**
   * Publish a configuration result event to a specific topic. The implementation is responsible for
   * converting the ConfigResultEvent to the appropriate wire format (e.g., CloudEvent).
   *
   * @param topic the topic to publish to
   * @param resultEvent the configuration result event to publish
   */
  void publish(String topic, ConfigResultEvent resultEvent);
}
