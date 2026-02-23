/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter;

/**
 * Base interface for all named components that can be discovered via {@link
 * java.util.ServiceLoader}. This includes ConfigAdapters, EventConsumers, and EventPublishers.
 *
 * <p>Components implementing this interface can be looked up by name using {@link
 * de.civitascore.configadapter.util.ServiceLoaderUtils#getInstanceByFilter}.
 */
public interface ConfigBase extends AutoCloseable {

  /**
   * Returns the unique name of this component. This name is used for ServiceLoader-based discovery
   * and configuration mapping.
   *
   * @return the name of this component, must not be null
   */
  String getName();

  /**
   * Closes this component and releases all resources.
   *
   * <p>Implementations should release any held resources such as:
   *
   * <ul>
   *   <li>Network connections
   *   <li>Thread pools or executors
   *   <li>File handles
   *   <li>Any other system resources
   * </ul>
   *
   * <p>Default implementation does nothing. Override this method to perform cleanup.
   */
  @Override
  default void close() {}
}
