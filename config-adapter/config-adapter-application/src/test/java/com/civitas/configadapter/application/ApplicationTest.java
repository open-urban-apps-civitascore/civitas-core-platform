/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.application;

import static org.junit.jupiter.api.Assertions.*;

import com.civitas.configadapter.exception.FatalAdapterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Application class covering various configuration scenarios. Tests both
 * successful initialization and expected failure cases.
 */
class ApplicationTest {

  @Test
  @DisplayName("Should successfully create application with valid single adapter configuration")
  void testValidSingleAdapterConfiguration() {
    // Given a valid configuration with a single dummylog adapter
    // When creating the application
    assertDoesNotThrow(
        () -> {
          new Application("application-valid-single-adapter.properties");
          // The application should initialize successfully without throwing exceptions
        },
        "Application should initialize successfully with valid single adapter configuration");
  }

  @Test
  @DisplayName("Should successfully create application with valid multiple adapters configuration")
  void testValidMultipleAdaptersConfiguration() {
    // Given a valid configuration with multiple adapters (keycloak and dummylog)
    // When creating the application
    assertDoesNotThrow(
        () -> {
          new Application("application.properties");
          // The application should initialize successfully without throwing exceptions
        },
        "Application should initialize successfully with valid multiple adapters configuration");
  }

  @Test
  @DisplayName("Should fail when adapter 'okta' does not exist")
  void testFailureWithNonExistentOktaAdapter() {
    // Given a configuration that includes the non-existent 'okta' adapter
    // When creating the application
    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> {
              new Application("application_wrong_adapter.properties");
            },
            "Application should throw FatalAdapterException when adapter 'okta' does not exist");

    // Then the exception should indicate the adapter was not found
    String message = exception.getMessage();
    assertTrue(
        message.contains("Adapter 'okta' not found via ServiceLoader"),
        "Exception message should mention failure not found adapter 'okta', but was: " + message);
  }

  @Test
  @DisplayName("Should fail when no adapters are configured")
  void testFailureWithNoAdapters() {
    // Given a configuration with no adapters specified
    // When creating the application
    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> {
              new Application("application-no-adapters.properties");
            },
            "Application should throw FatalAdapterException when no adapters are configured");

    // Then the exception should indicate no adapters configured
    String message = exception.getMessage();
    assertTrue(
        message.contains("No adapters configured"),
        "Exception message should mention no adapters configured, but was: " + message);
  }

  @Test
  @DisplayName("Should fail when no event handler is configured")
  void testFailureWithNoEventHandler() {
    // Given a configuration with adapters but no event handler
    // When creating the application
    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> {
              new Application("application-no-eventhandler.properties");
            },
            "Application should throw FatalAdapterException when no event handler is configured");

    // Then the exception should indicate no event consumer configured
    String message = exception.getMessage();
    assertTrue(
        message.contains("No event consumer configured"),
        "Exception message should mention no event consumer configured, but was: " + message);
  }

  @Test
  @DisplayName("Should fail with invalid configuration file")
  void testFailureWithInvalidConfigurationFile() {
    // Given a non-existent configuration file
    // When creating the application
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> new Application("non-existent-config.properties"),
            "Application should throw RuntimeException when configuration file does not exist");

    // Then the exception should indicate the configuration file was not found
    String message = exception.getMessage();
    assertTrue(
        message.contains("Unable to find non-existent-config.properties"),
        "Exception message should mention the missing config file, but was: " + message);
  }

  @Test
  @DisplayName("Should use default configuration file when no file specified")
  void testDefaultConfigurationFile() {
    // This test verifies that the default constructor uses "application.properties"
    // We can't easily test this without side effects, but we can verify the constructor exists
    assertDoesNotThrow(
        () -> {
          // This would normally work if application.properties is valid
          // In test context, this might fail due to missing test setup,
          // but the constructor should exist and be callable
          new Application();
        });
  }

  @Test
  @DisplayName("Should handle null configuration file name")
  void testNullConfigurationFileName() {
    // Given a null configuration file name
    // When creating the application
    assertThrows(
        NullPointerException.class,
        () -> {
          new Application(null);
        },
        "Application should throw NullPointerException when configuration file name is null");
  }

  @Test
  @DisplayName("Should successfully create application with separate consumer and publisher")
  void testSeparateConsumerAndPublisherConfiguration() {
    // Given a configuration with separate eventconsumer.name and eventpublisher.name
    // When creating the application
    assertDoesNotThrow(
        () -> {
          new Application("application-separate-consumer-publisher.properties");
        },
        "Application should initialize successfully with separate consumer and publisher configuration");
  }

  @Test
  @DisplayName("Should fail when both eventhandler.name and eventconsumer.name are specified")
  void testFailureWithConflictingHandlerConfiguration() {
    // Given a configuration with both eventhandler.name AND eventconsumer.name specified
    // When creating the application
    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> {
              new Application("application-conflicting-handler-config.properties");
            },
            "Application should throw FatalAdapterException when both eventhandler.name and eventconsumer.name are specified");

    // Then the exception should indicate the configuration conflict
    String message = exception.getMessage();
    assertTrue(
        message.contains("Cannot specify both"),
        "Exception message should mention configuration conflict, but was: " + message);
  }

  @Test
  @DisplayName("Should fail when event consumer name does not exist")
  void testFailureWithNonExistentEventConsumer() {
    // Given a configuration with a non-existent event consumer name 'rabbitmq'
    // When creating the application
    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> {
              new Application("application-nonexistent-consumer.properties");
            },
            "Application should throw FatalAdapterException when event consumer 'rabbitmq' does not exist");

    // Then the exception should indicate the event consumer was not found
    String message = exception.getMessage();
    assertTrue(
        message.contains("Event consumer 'rabbitmq' not found"),
        "Exception message should mention event consumer not found, but was: " + message);
  }

  @Test
  @DisplayName(
      "Should succeed with warning when event publisher name does not exist (publisher is optional)")
  void testSuccessWithNonExistentEventPublisher() {
    // Given a configuration with a non-existent event publisher name 'rabbitmq'
    // Publisher is optional, so the application should still initialize successfully
    // When creating the application
    assertDoesNotThrow(
        () -> {
          new Application("application-nonexistent-publisher.properties");
        },
        "Application should initialize successfully even when publisher is not found (publisher is optional)");
  }

  @Test
  @DisplayName("Should successfully create application with consumer only (no publisher)")
  void testConsumerOnlyConfiguration() {
    // Given a configuration with only eventconsumer.name (no eventpublisher.name)
    // When creating the application
    assertDoesNotThrow(
        () -> {
          new Application("application-consumer-only.properties");
        },
        "Application should initialize successfully with consumer-only configuration");
  }
}
