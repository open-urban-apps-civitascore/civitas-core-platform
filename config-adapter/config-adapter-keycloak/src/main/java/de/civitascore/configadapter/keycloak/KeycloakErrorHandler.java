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

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.util.PiiMaskingUtil;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared error handling utilities for Keycloak resource handlers. */
final class KeycloakErrorHandler {

  private static final Logger logger = LoggerFactory.getLogger(KeycloakErrorHandler.class);

  private KeycloakErrorHandler() {}

  static void validateResponse(KeycloakOperation operation, int expectedStatus, Response response)
      throws KeycloakOperationException {
    if (response.getStatus() != expectedStatus) {
      String errorBody = response.readEntity(String.class);
      throw new KeycloakOperationException(
          operation.getDescription()
              + " failed with status "
              + response.getStatus()
              + ": "
              + errorBody);
    }
  }

  static RetryableAdapterException wrapNetworkException(
      ProcessingException e, KeycloakOperation operation) {
    logger.warn(
        "Network error during {}: {}", operation, Encode.forJava(String.valueOf(e.getMessage())));
    return new RetryableAdapterException(
        AdapterErrorCode.NETWORK_ERROR, e, "keycloak", maskPII(e.getMessage()));
  }

  static void wrapWebException(
      WebApplicationException e, KeycloakOperation operation, AdapterErrorCode defaultErrorCode)
      throws RetryableAdapterException, FatalAdapterException {
    int status = e.getResponse().getStatus();

    if (status >= 500) {
      logger.warn("Keycloak server error during {}: HTTP {}", operation, status);
      throw new RetryableAdapterException(
          AdapterErrorCode.SERVICE_UNAVAILABLE, e, "keycloak", status);
    }

    if (status == 409) {
      logger.warn("Keycloak conflict during {}: HTTP 409", operation);
      throw new FatalAdapterException(
          AdapterErrorCode.KEYCLOAK_CONFLICT, e, "Resource already exists");
    }

    logger.error("Keycloak client error during {}: HTTP {}", operation, status);
    throw new FatalAdapterException(defaultErrorCode, e, "HTTP " + status);
  }

  static String maskId(String id) {
    return PiiMaskingUtil.maskId(id);
  }

  static String maskPII(String message) {
    return PiiMaskingUtil.maskPII(message);
  }

  static class KeycloakOperationException extends Exception {
    private static final long serialVersionUID = -8977227116700940716L;

    KeycloakOperationException(String message) {
      super(message);
    }
  }
}
