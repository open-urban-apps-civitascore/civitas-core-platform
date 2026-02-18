/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ServiceLoaderUtils}. */
class ServiceLoaderUtilsTest {

  @Test
  @DisplayName("Should throw NullPointerException when serviceClass is null")
  void testNullServiceClass() {
    assertThrows(
        NullPointerException.class,
        () -> ServiceLoaderUtils.getInstanceByFilter(null, s -> true),
        "Should throw NullPointerException when serviceClass is null");
  }

  @Test
  @DisplayName("Should throw NullPointerException when filter is null")
  void testNullFilter() {
    assertThrows(
        NullPointerException.class,
        () -> ServiceLoaderUtils.getInstanceByFilter(TestService.class, null),
        "Should throw NullPointerException when filter is null");
  }

  @Test
  @DisplayName("Should return empty Optional when no service matches filter")
  void testNoMatchingService() {
    // Given a filter that matches nothing
    Optional<TestService> result =
        ServiceLoaderUtils.getInstanceByFilter(
            TestService.class, s -> "nonexistent".equals(s.getName()));

    // Then the result should be empty
    assertFalse(result.isPresent(), "Should return empty Optional when no service matches");
  }

  @Test
  @DisplayName("Should return matching service when filter matches")
  void testMatchingService() {
    // Given a filter that matches TestServiceImpl
    Optional<TestService> result =
        ServiceLoaderUtils.getInstanceByFilter(
            TestService.class, s -> "test-service".equals(s.getName()));

    // Then the result should contain the matching service
    assertTrue(result.isPresent(), "Should return present Optional when service matches");
    assertEquals("test-service", result.get().getName());
  }

  @Test
  @DisplayName("Should return first matching service when multiple services match")
  void testMultipleMatchingServices() {
    // Given a filter that matches any service
    Optional<TestService> result =
        ServiceLoaderUtils.getInstanceByFilter(TestService.class, s -> s.getName() != null);

    // Then the result should contain one of the services
    assertTrue(result.isPresent(), "Should return present Optional when services match");
  }

  @Test
  @DisplayName("Should return empty Optional when no services are registered")
  void testNoRegisteredServices() {
    // Given a service type with no registered implementations
    Optional<UnregisteredService> result =
        ServiceLoaderUtils.getInstanceByFilter(UnregisteredService.class, s -> true);

    // Then the result should be empty
    assertFalse(result.isPresent(), "Should return empty Optional when no services are registered");
  }

  /** Test service interface for ServiceLoader testing. */
  public interface TestService {
    String getName();
  }

  /** Interface with no registered implementations for testing. */
  public interface UnregisteredService {
    String getName();
  }

  /** Test implementation of TestService. */
  public static class TestServiceImpl implements TestService {
    @Override
    public String getName() {
      return "test-service";
    }
  }

  /** Second test implementation of TestService. */
  public static class AnotherTestServiceImpl implements TestService {
    @Override
    public String getName() {
      return "another-service";
    }
  }
}
