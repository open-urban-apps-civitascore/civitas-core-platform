/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.messaging.EventConsumer;
import java.util.List;
import java.util.Map;
import org.apache.commons.configuration2.MapConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConsumerFactoryTest {

  private final ConsumerFactory factory = new ConsumerFactory();

  private AppConfig configWith(Map<String, String> props) {
    return new AppConfig(new MapConfiguration(props));
  }

  @Nested
  @DisplayName("createAll")
  class CreateAll {

    @Test
    @DisplayName("Should throw when no adapters are configured")
    void noAdaptersConfigured() {
      AppConfig config =
          configWith(
              Map.of(
                  "eventhandler.name", "kafka",
                  "kafka.bootstrap.servers", "localhost:9092",
                  "kafka.group.id", "test-group"));

      FatalAdapterException ex =
          assertThrows(FatalAdapterException.class, () -> factory.createAll(config));
      String message = ex.getMessage();
      assertTrue(
          message.contains("No adapters configured"),
          "Expected 'No adapters configured' but was: " + message);
    }

    @Test
    @DisplayName("Should throw when adapter is not found via ServiceLoader")
    void adapterNotFound() {
      AppConfig config =
          configWith(
              Map.of(
                  "adapters", "nonexistent-adapter",
                  "eventhandler.name", "kafka",
                  "kafka.bootstrap.servers", "localhost:9092",
                  "kafka.group.id", "test-group"));

      FatalAdapterException ex =
          assertThrows(FatalAdapterException.class, () -> factory.createAll(config));
      String message = ex.getMessage();
      assertTrue(
          message.contains("not found via ServiceLoader"),
          "Expected 'not found via ServiceLoader' but was: " + message);
    }

    @Test
    @DisplayName("Should create consumers for valid adapter configuration")
    void validSingleAdapter() throws FatalAdapterException {
      AppConfig config =
          configWith(
              Map.of(
                  "adapters", "dummylog",
                  "eventhandler.name", "kafka",
                  "kafka.bootstrap.servers", "localhost:9092",
                  "kafka.group.id", "test-group",
                  "dummylog.topics",
                      "de.civitascore.idm.user.created,de.civitascore.idm.user.updated"));

      List<EventConsumer> consumers = factory.createAll(config);
      assertFalse(consumers.isEmpty());
      assertEquals(1, consumers.size());
    }

    @Test
    @DisplayName("Should skip adapters with no subscribed topics")
    void adapterWithNoTopicsSkipped() {
      AppConfig config =
          configWith(
              Map.of(
                  "adapters", "dummylog",
                  "eventhandler.name", "kafka",
                  "kafka.bootstrap.servers", "localhost:9092",
                  "kafka.group.id", "test-group"));

      FatalAdapterException ex =
          assertThrows(FatalAdapterException.class, () -> factory.createAll(config));
      String message = ex.getMessage();
      assertTrue(
          message.contains("No valid consumers created"),
          "Expected 'No valid consumers created' but was: " + message);
    }

    @Test
    @DisplayName("Should create consumers for multiple adapters")
    void multipleAdapters() throws FatalAdapterException {
      AppConfig config =
          configWith(
              Map.of(
                  "adapters", "dummylog,dummylog2",
                  "eventhandler.name", "kafka",
                  "kafka.bootstrap.servers", "localhost:9092",
                  "kafka.group.id", "test-group",
                  "dummylog.topics", "de.civitascore.idm.user.created",
                  "dummylog2.topics", "de.civitascore.idm.user.updated"));

      List<EventConsumer> consumers = factory.createAll(config);
      assertEquals(2, consumers.size());
    }
  }

  @Nested
  @DisplayName("Event handler configuration validation")
  class HandlerValidation {

    @Test
    @DisplayName("Should throw when both eventhandler.name and eventconsumer.name are specified")
    void conflictingHandlerConfig() {
      AppConfig config =
          configWith(
              Map.of(
                  "adapters", "dummylog",
                  "eventhandler.name", "kafka",
                  "eventconsumer.name", "kafka",
                  "kafka.bootstrap.servers", "localhost:9092",
                  "kafka.group.id", "test-group",
                  "dummylog.topics", "de.civitascore.idm.user.created"));

      FatalAdapterException ex =
          assertThrows(FatalAdapterException.class, () -> factory.createAll(config));
      String message = ex.getMessage();
      assertTrue(
          message.contains("Cannot specify both"),
          "Expected 'Cannot specify both' but was: " + message);
    }

    @Test
    @DisplayName("Should throw when no event consumer is configured")
    void noEventConsumer() {
      AppConfig config =
          configWith(
              Map.of(
                  "adapters", "dummylog",
                  "kafka.bootstrap.servers", "localhost:9092",
                  "kafka.group.id", "test-group",
                  "dummylog.topics", "de.civitascore.idm.user.created"));

      FatalAdapterException ex =
          assertThrows(FatalAdapterException.class, () -> factory.createAll(config));
      String message = ex.getMessage();
      assertTrue(
          message.contains("No event consumer configured"),
          "Expected 'No event consumer configured' but was: " + message);
    }

    @Test
    @DisplayName("Should throw when event consumer name does not exist")
    void nonExistentConsumer() {
      AppConfig config =
          configWith(
              Map.of(
                  "adapters", "dummylog",
                  "eventconsumer.name", "rabbitmq",
                  "kafka.bootstrap.servers", "localhost:9092",
                  "kafka.group.id", "test-group",
                  "dummylog.topics", "de.civitascore.idm.user.created"));

      FatalAdapterException ex =
          assertThrows(FatalAdapterException.class, () -> factory.createAll(config));
      String message = ex.getMessage();
      assertTrue(
          message.contains("Event consumer 'rabbitmq' not found"),
          "Expected 'Event consumer 'rabbitmq' not found' but was: " + message);
    }

    @Test
    @DisplayName(
        "Should succeed with separate consumer and non-existent publisher (publisher optional)")
    void nonExistentPublisherIsOptional() throws FatalAdapterException {
      AppConfig config =
          configWith(
              Map.of(
                  "adapters", "dummylog",
                  "eventconsumer.name", "kafka",
                  "eventpublisher.name", "rabbitmq",
                  "kafka.bootstrap.servers", "localhost:9092",
                  "kafka.group.id", "test-group",
                  "dummylog.topics", "de.civitascore.idm.user.created"));

      List<EventConsumer> consumers = factory.createAll(config);
      assertFalse(consumers.isEmpty());
    }
  }
}
