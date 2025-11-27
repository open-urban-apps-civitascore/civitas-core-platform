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
package com.civitas.configadapter.configuration;

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
   * ServiceLoader by matching against {@link com.civitas.configadapter.ConfigBase#getName()}.
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
