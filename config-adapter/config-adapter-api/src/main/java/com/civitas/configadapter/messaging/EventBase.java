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

import com.civitas.configadapter.ConfigBase;
import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.configuration.ApplicationConfig;

/**
 * Interface for event base components that process messages from message brokers (Kafka, RabbitMQ,
 * etc.) and delegate to ConfigAdapter implementations.
 */
public interface EventBase extends ConfigBase {

  /**
   * Initializes the event consumer.
   *
   * @param config the {@link ApplicationConfig} must not be <code>null</code>
   * @param adapter the {@link ConfigAdapter} must not be <code>null</code>
   */
  void initialize(ApplicationConfig config, ConfigAdapter adapter);
}
