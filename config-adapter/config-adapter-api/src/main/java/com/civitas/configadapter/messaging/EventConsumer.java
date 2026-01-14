/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.messaging;

/**
 * Interface for event consumers that process messages from message brokers (Kafka, RabbitMQ, etc.)
 * and delegate to ConfigAdapter implementations.
 *
 * <p>Implementations are responsible for:
 *
 * <ul>
 *   <li>Connecting to the message broker
 *   <li>Subscribing to topics provided by the injected ConfigAdapter
 *   <li>Consuming and deserializing CloudEvents
 *   <li>Delegating event processing to the ConfigAdapter
 *   <li>Handling processing errors appropriately (retry, DLQ, etc.)
 *   <li>Managing lifecycle and cleanup of resources
 * </ul>
 *
 * <p>Example implementations: KafkaEventHandler, RabbitMQEventConsumer
 */
public interface EventConsumer extends EventBase {

  /**
   * Starts the event consumer to begin processing messages.
   *
   * <p><strong>Important:</strong> This method should be <strong>non-blocking</strong>. It should
   * start the consumption process (typically in a separate thread or using an executor) and return
   * immediately. The consumption loop should run asynchronously in the background.
   *
   * <p>Example non-blocking implementation:
   *
   * <pre>{@code
   * private volatile boolean running = false;
   * private Thread consumerThread;
   *
   * @Override
   * public void start() {
   *   running = true;
   *   consumerThread = Thread.ofVirtual().start(() -> {
   *     while (running) {
   *       // Poll and process messages
   *     }
   *   });
   * }
   * }</pre>
   *
   * <p>The method should:
   *
   * <ul>
   *   <li>Initialize connection to the message broker
   *   <li>Start consuming messages in a background thread or executor
   *   <li>Return immediately without blocking
   *   <li>Handle exceptions gracefully within the consumption loop
   * </ul>
   */
  void start();
}
