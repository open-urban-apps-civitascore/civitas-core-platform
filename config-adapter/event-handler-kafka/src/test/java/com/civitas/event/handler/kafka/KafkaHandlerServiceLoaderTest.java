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
package com.civitas.event.handler.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.civitas.configadapter.messaging.EventConsumer;
import com.civitas.configadapter.messaging.EventPublisher;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@link ServiceLoader} configuration
 *
 * @author Mark Hoffmann
 * @since 24.11.2025
 */
public class KafkaHandlerServiceLoaderTest {

  @Test
  public void testPublisherServiceLoader() {
    ServiceLoader<EventPublisher> epSL = ServiceLoader.load(EventPublisher.class);
    assertNotNull(epSL);
    assertEquals(1, epSL.stream().count());
    EventPublisher ep = epSL.iterator().next();
    assertNotNull(ep);
    assertEquals(KafkaEventHandler.HANDLER_NAME, ep.getName());
  }

  @Test
  public void testConsumerServiceLoader() {
    ServiceLoader<EventConsumer> ecSL = ServiceLoader.load(EventConsumer.class);
    assertNotNull(ecSL);
    assertEquals(1, ecSL.stream().count());
    EventConsumer ec = ecSL.iterator().next();
    assertNotNull(ec);
    assertEquals(KafkaEventHandler.HANDLER_NAME, ec.getName());
  }
}
