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
