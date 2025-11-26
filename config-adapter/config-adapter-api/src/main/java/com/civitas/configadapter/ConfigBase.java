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
package com.civitas.configadapter;

/**
 * Interface for event base components that process messages from message brokers (Kafka, RabbitMQ,
 * etc.) and delegate to ConfigAdapter implementations.
 */
public interface ConfigBase extends AutoCloseable {

  /**
   * Returns the name of this consumer
   *
   * @return the name of the consumer
   */
  String getName();

  /**
   * Closes the event consumer and releases all resources.
   *
   * <p>Implementations should:
   *
   * <ul>
   *   <li>Stop the consumption loop gracefully
   *   <li>Close connections to the message broker
   *   <li>Close the injected ConfigAdapter by calling adapter.close()
   *   <li>Wait for in-flight messages to complete (if appropriate)
   *   <li>Release any other resources (threads, executors, etc.)
   * </ul>
   *
   * <p>Default implementation does nothing. Override this method to perform cleanup.
   */
  @Override
  default void close() {}
}
