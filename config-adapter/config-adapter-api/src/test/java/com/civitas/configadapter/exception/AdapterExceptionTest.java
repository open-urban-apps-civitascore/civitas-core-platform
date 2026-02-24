/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civitas.configadapter.model.AdapterErrorCode;
import org.junit.jupiter.api.Test;

class AdapterExceptionTest {

  @Test
  void retryableException_shouldBeRetryable() {
    RetryableAdapterException exception =
        new RetryableAdapterException(
            AdapterErrorCode.NETWORK_ERROR, "keycloak", "Connection refused");

    assertTrue(exception.isRetryable());
    assertEquals(AdapterErrorCode.NETWORK_ERROR, exception.getErrorCode());
    assertEquals(2003, exception.getNumericCode());
  }

  @Test
  void fatalException_shouldNotBeRetryable() {
    FatalAdapterException exception =
        new FatalAdapterException(
            AdapterErrorCode.INVALID_PAYLOAD, "Missing required field 'email'");

    assertFalse(exception.isRetryable());
    assertEquals(AdapterErrorCode.INVALID_PAYLOAD, exception.getErrorCode());
    assertEquals(1001, exception.getNumericCode());
  }

  @Test
  void exception_shouldFormatInternalMessage() {
    RetryableAdapterException exception =
        new RetryableAdapterException(AdapterErrorCode.SERVICE_UNAVAILABLE, "keycloak", 503);

    String internalMessage = exception.getInternalMessage();
    assertTrue(internalMessage.contains("keycloak"));
    assertTrue(internalMessage.contains("503"));
  }

  @Test
  void exception_shouldReturnSafeExternalMessage() {
    FatalAdapterException exception =
        new FatalAdapterException(
            AdapterErrorCode.KEYCLOAK_ERROR, "Detailed internal error with user@email.com");

    String externalMessage = exception.getSafeExternalMessage();
    assertEquals("Identity provider error", externalMessage);
    assertFalse(externalMessage.contains("user@email.com"));
    assertFalse(externalMessage.contains("Detailed"));
  }

  @Test
  void exception_shouldPreserveCause() {
    RuntimeException cause = new RuntimeException("Original error");
    RetryableAdapterException exception =
        new RetryableAdapterException(AdapterErrorCode.CONNECTION_TIMEOUT, cause, "keycloak");

    assertSame(cause, exception.getCause());
  }

  @Test
  void exception_shouldProvideFullErrorIdentifier() {
    FatalAdapterException exception =
        new FatalAdapterException(AdapterErrorCode.RESOURCE_NOT_FOUND, "user-123");

    String identifier = exception.getFullErrorIdentifier();
    assertEquals("RESOURCE_NOT_FOUND(1002)", identifier);
  }

  @Test
  void toString_shouldContainAllRelevantInfo() {
    FatalAdapterException exception =
        new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, "Missing field");

    String result = exception.toString();
    assertTrue(result.contains("FatalAdapterException"));
    assertTrue(result.contains("INVALID_PAYLOAD"));
    assertTrue(result.contains("1001"));
    assertTrue(result.contains("Missing field"));
  }

  @Test
  void errorCode_shouldProvideCategoryInfo() {
    // 1xxx = Fatal/Validation
    assertFalse(AdapterErrorCode.INVALID_PAYLOAD.isRetryable());
    assertTrue(AdapterErrorCode.INVALID_PAYLOAD.getCode() >= 1000);
    assertTrue(AdapterErrorCode.INVALID_PAYLOAD.getCode() < 2000);

    // 2xxx = Connectivity/Retryable
    assertTrue(AdapterErrorCode.CONNECTION_TIMEOUT.isRetryable());
    assertTrue(AdapterErrorCode.CONNECTION_TIMEOUT.getCode() >= 2000);
    assertTrue(AdapterErrorCode.CONNECTION_TIMEOUT.getCode() < 3000);

    // 3xxx = Adapter-specific
    assertFalse(AdapterErrorCode.KEYCLOAK_ERROR.isRetryable());
    assertTrue(AdapterErrorCode.KEYCLOAK_ERROR.getCode() >= 3000);
    assertTrue(AdapterErrorCode.KEYCLOAK_ERROR.getCode() < 4000);

    // 3xxx: REDPANDA_ERROR is retryable (used for HTTP 5xx via RetryableAdapterException)
    assertTrue(AdapterErrorCode.REDPANDA_ERROR.isRetryable());

    // 9xxx = Unknown
    assertFalse(AdapterErrorCode.UNKNOWN_ERROR.isRetryable());
    assertTrue(AdapterErrorCode.UNKNOWN_ERROR.getCode() >= 9000);
  }

  @Test
  void externalMessage_shouldNeverContainStackTrace() {
    RuntimeException cause = new RuntimeException("Internal error at line 42");
    FatalAdapterException exception =
        new FatalAdapterException(AdapterErrorCode.UNKNOWN_ERROR, cause, cause.getMessage());

    String externalMessage = exception.getSafeExternalMessage();
    assertNotNull(externalMessage);
    assertFalse(externalMessage.contains("line 42"));
    assertFalse(externalMessage.contains("at "));
    assertFalse(externalMessage.contains(".java"));
    assertEquals("Internal error", externalMessage);
  }

  @Test
  void errorCode_formatInternalMessage_shouldHandleNullArgs() {
    String result = AdapterErrorCode.UNKNOWN_ERROR.formatInternalMessage((Object[]) null);
    assertNotNull(result);
  }

  @Test
  void errorCode_formatInternalMessage_shouldHandleEmptyArgs() {
    String result = AdapterErrorCode.UNKNOWN_ERROR.formatInternalMessage();
    assertNotNull(result);
  }

  @Test
  void allErrorCodes_shouldHaveValidMessages() {
    for (AdapterErrorCode code : AdapterErrorCode.values()) {
      assertNotNull(code.getInternalLogTemplate(), "Missing internal template for " + code);
      assertNotNull(code.getExternalUserMessage(), "Missing external message for " + code);
      assertFalse(code.getExternalUserMessage().isEmpty(), "Empty external message for " + code);
      assertTrue(code.getCode() > 0, "Invalid code for " + code);
    }
  }
}
