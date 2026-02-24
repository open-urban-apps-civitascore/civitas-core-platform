/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.configuration;

import java.util.List;

/**
 * Configuration interface for the application layer. Provides access to application-specific
 * configuration such as adapter names, event handler classes, and health check settings.
 *
 * <p>This interface extends {@link AdapterConfig} to provide both application-level and
 * adapter-level configuration access.
 */
public interface ApplicationConfig extends AdapterConfig {

  /**
   * Gets the list of adapter names to instantiate. These names are used to look up adapters via
   * ServiceLoader by matching against {@link de.civitascore.configadapter.ConfigBase#getName()}.
   *
   * @return list of adapter names (e.g., "keycloak", "dummylog")
   */
  List<String> getAdapterNames();

  /**
   * Gets the combined event handler name. When specified, this handler is used for both consuming
   * and publishing events. Cannot be used together with separate consumer/publisher names.
   *
   * @return the event handler name, or null if using separate consumer/publisher
   */
  String getEventHandlerName();

  /**
   * Gets the event consumer name. Used when configuring separate consumer and publisher components.
   *
   * @return the event consumer name, or null if using combined handler
   */
  String getEventConsumerName();

  /**
   * Gets the event publisher name. Used when configuring separate consumer and publisher
   * components. The publisher is optional - adapters can function without one.
   *
   * @return the event publisher name, or null if not configured or using combined handler
   */
  String getEventPublisherName();

  /**
   * Gets the health check server port.
   *
   * @return the port number for the health check server
   * @throws RuntimeException if the port value is not a valid integer
   */
  int getHealthCheckPort();
}
