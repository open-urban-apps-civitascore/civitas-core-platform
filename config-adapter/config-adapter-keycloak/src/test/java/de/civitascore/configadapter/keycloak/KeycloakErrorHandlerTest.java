/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.keycloak.KeycloakErrorHandler.KeycloakOperationException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KeycloakErrorHandlerTest {

  @Nested
  @DisplayName("validateResponse")
  class ValidateResponse {

    @Test
    @DisplayName("does not throw when status matches expected")
    void shouldPassWhenStatusMatches() throws KeycloakOperationException {
      Response response = mock(Response.class);
      when(response.getStatus()).thenReturn(201);

      KeycloakErrorHandler.validateResponse(KeycloakOperation.USER_CREATION, 201, response);
    }

    @Test
    @DisplayName("throws KeycloakOperationException when status differs")
    void shouldThrowWhenStatusDiffers() {
      Response response = mock(Response.class);
      when(response.getStatus()).thenReturn(400);
      when(response.readEntity(String.class)).thenReturn("Bad Request");

      KeycloakOperationException ex =
          assertThrows(
              KeycloakOperationException.class,
              () ->
                  KeycloakErrorHandler.validateResponse(
                      KeycloakOperation.USER_CREATION, 201, response));

      assertEquals("user creation failed with status 400: Bad Request", ex.getMessage());
    }
  }

  @Nested
  @DisplayName("wrapNetworkException")
  class WrapNetworkException {

    @Test
    @DisplayName("wraps ProcessingException as RetryableAdapterException")
    void shouldWrapAsRetryable() {
      ProcessingException cause = new ProcessingException("Connection refused");

      RetryableAdapterException result =
          KeycloakErrorHandler.wrapNetworkException(cause, KeycloakOperation.REALM_CREATION);

      assertInstanceOf(RetryableAdapterException.class, result);
      assertEquals(cause, result.getCause());
    }
  }

  @Nested
  @DisplayName("wrapWebException")
  class WrapWebException {

    @Test
    @DisplayName("throws RetryableAdapterException for 5xx errors")
    void shouldThrowRetryableFor5xx() {
      WebApplicationException e = mock(WebApplicationException.class);
      Response response = mock(Response.class);
      when(e.getResponse()).thenReturn(response);
      when(response.getStatus()).thenReturn(503);

      assertThrows(
          RetryableAdapterException.class,
          () ->
              KeycloakErrorHandler.wrapWebException(
                  e, KeycloakOperation.REALM_CREATION, AdapterErrorCode.KEYCLOAK_REALM_ERROR));
    }

    @Test
    @DisplayName("throws FatalAdapterException with KEYCLOAK_CONFLICT for 409")
    void shouldThrowFatalFor409() {
      WebApplicationException e = mock(WebApplicationException.class);
      Response response = mock(Response.class);
      when(e.getResponse()).thenReturn(response);
      when(response.getStatus()).thenReturn(409);

      assertThrows(
          FatalAdapterException.class,
          () ->
              KeycloakErrorHandler.wrapWebException(
                  e, KeycloakOperation.REALM_CREATION, AdapterErrorCode.KEYCLOAK_REALM_ERROR));
    }

    @Test
    @DisplayName("throws FatalAdapterException for other 4xx errors")
    void shouldThrowFatalFor4xx() {
      WebApplicationException e = mock(WebApplicationException.class);
      Response response = mock(Response.class);
      when(e.getResponse()).thenReturn(response);
      when(response.getStatus()).thenReturn(400);

      assertThrows(
          FatalAdapterException.class,
          () ->
              KeycloakErrorHandler.wrapWebException(
                  e, KeycloakOperation.CLIENT_CREATION, AdapterErrorCode.KEYCLOAK_CLIENT_ERROR));
    }
  }
}
