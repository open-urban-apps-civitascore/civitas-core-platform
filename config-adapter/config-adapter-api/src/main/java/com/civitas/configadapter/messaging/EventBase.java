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
 * Base interface for event-handling components ({@link EventConsumer} and {@link EventPublisher})
 * that interact with message brokers (Kafka, RabbitMQ, etc.) and work with ConfigAdapter
 * implementations.
 *
 * <p>This interface provides the common initialization contract for event components that need
 * access to application configuration and a ConfigAdapter instance.
 */
public interface EventBase extends ConfigBase {

  /**
   * Initializes this event component with configuration and adapter.
   *
   * <p>This method must be called before the component can be used. Implementations should use the
   * provided configuration to set up connections to message brokers and associate with the given
   * adapter.
   *
   * @param config the application configuration providing connection settings, must not be null
   * @param adapter the ConfigAdapter this component will work with, must not be null
   */
  void initialize(ApplicationConfig config, ConfigAdapter adapter);
}
