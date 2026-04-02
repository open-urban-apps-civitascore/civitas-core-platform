/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.messaging;

import de.civitascore.configadapter.ConfigBase;
import de.civitascore.configadapter.adapter.ConfigAdapter;
import de.civitascore.configadapter.configuration.ApplicationConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;

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
  void initialize(ApplicationConfig config, ConfigAdapter adapter) throws FatalAdapterException;
}
