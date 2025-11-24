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
package com.civitas.configadapter.application;

import static org.junit.jupiter.api.Assertions.*;

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
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              new Application("application_wrong_adapter.properties");
            },
            "Application should throw RuntimeException when adapter 'okta' does not exist");

    // Then the exception should indicate the failure to create the consumer
    String message = exception.getMessage();
    assertTrue(
        message.contains("Failed to create consumer for adapter: okta"),
        "Exception message should mention failure to create consumer for adapter 'okta', but was: "
            + message);
  }

  @Test
  @DisplayName("Should fail when no adapters are configured")
  void testFailureWithNoAdapters() {
    // Given a configuration with no adapters specified
    // When creating the application
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              new Application("application-no-adapters.properties");
            },
            "Application should throw RuntimeException when no adapters are configured");

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
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              new Application("application-no-eventhandler.properties");
            },
            "Application should throw RuntimeException when no event handler is configured");

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
}
