/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.frost;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.AdapterOperation;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes FROST entity HTTP requests with the adapter framework's error classification:
 *
 * <ul>
 *   <li>Network errors ({@link ProcessingException}) → {@link RetryableAdapterException} ({@code
 *       NETWORK_ERROR})
 *   <li>HTTP 5xx → {@link RetryableAdapterException} ({@code SERVICE_UNAVAILABLE}) — except FROST's
 *       500 + "Failed to store data." on CREATE, which is a duplicate (UNIQUE constraint) and
 *       treated as idempotent success like a real 409
 *   <li>HTTP 409 on CREATE / 404 on DELETE → success (idempotent)
 *   <li>Other HTTP 4xx and unexpected runtime failures → {@link FatalAdapterException} ({@code
 *       FROST_ENTITY_ERROR})
 * </ul>
 */
final class FrostHttpExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(FrostHttpExecutor.class);
  private static final String HTTP_STATUS_PREFIX = "HTTP ";

  /** Functional interface for the actual HTTP call (POST/PATCH/DELETE). */
  @FunctionalInterface
  interface HttpRequestOperation {
    Response execute();
  }

  private FrostHttpExecutor() {}

  /**
   * Executes the request and returns the effective resource id: for a CREATE that returns 201 the
   * id is extracted from the {@code Location} header, otherwise the given {@code resourceId} is
   * passed through.
   */
  // The RuntimeException catch is a deliberate boundary: an unexpected failure must surface as a
  // classified FatalAdapterException (failure result + DLQ), not crash the consumer loop.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  static String execute(AdapterOperation operation, String resourceId, HttpRequestOperation request)
      throws FatalAdapterException, RetryableAdapterException {
    try (Response response = request.execute()) {
      handleHttpResponse(response, operation);

      if (operation == AdapterOperation.FROST_ENTITY_CREATE
          && response.getStatus() == Response.Status.CREATED.getStatusCode()) {
        return FrostUtils.extractIdFromLocation(response.getHeaderString("Location"));
      }
      return resourceId;
    } catch (ProcessingException e) {
      LOG.warn(
          "Network error during {}: {}",
          Encode.forJava(operation.getDescription()),
          Encode.forJava(e.getMessage()));
      throw new RetryableAdapterException(
          AdapterErrorCode.NETWORK_ERROR, e, FrostAdapter.ADAPTER_NAME, e.getMessage());
    } catch (RuntimeException e) {
      LOG.error("Failed to execute {}", Encode.forJava(operation.getDescription()), e);
      throw new FatalAdapterException(
          AdapterErrorCode.FROST_ENTITY_ERROR,
          e,
          operation.getDescription() + " failed: " + e.getMessage());
    }
  }

  private static void handleHttpResponse(Response response, AdapterOperation operation)
      throws RetryableAdapterException, FatalAdapterException {
    int status = response.getStatus();
    if (Response.Status.Family.familyOf(status) == Response.Status.Family.SUCCESSFUL
        || isIdempotentNoOp(status, operation)) {
      return;
    }

    String body = response.readEntity(String.class);
    if (status >= Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
      // FROST returns 500 with "Failed to store data." on UNIQUE constraint violations instead of
      // 409 — treated as idempotent success for CREATE operations, same as a real 409.
      if (operation == AdapterOperation.FROST_ENTITY_CREATE
          && body.contains(FrostAdapter.ERROR_FAILED_TO_STORE_DATA)) {
        LOG.info(
            "FROST entity already exists (500 'Failed to store data.'), treating create as"
                + " success (idempotent)");
        return;
      }
      LOG.warn(
          "FROST server error during {}: {} {}",
          Encode.forJava(operation.getDescription()),
          status,
          Encode.forJava(body));
      throw new RetryableAdapterException(
          AdapterErrorCode.SERVICE_UNAVAILABLE, null, FrostAdapter.ADAPTER_NAME, status);
    }
    LOG.error(
        "FROST client error during {}: {} {}",
        Encode.forJava(operation.getDescription()),
        status,
        Encode.forJava(body));
    throw new FatalAdapterException(
        AdapterErrorCode.FROST_ENTITY_ERROR, null, HTTP_STATUS_PREFIX + status + ": " + body);
  }

  /** Status codes that already represent the desired end state — no error, nothing to do. */
  private static boolean isIdempotentNoOp(int status, AdapterOperation operation) {
    if (status == Response.Status.CONFLICT.getStatusCode()
        && operation == AdapterOperation.FROST_ENTITY_CREATE) {
      LOG.info("FROST entity already exists (409), treating create as success (idempotent)");
      return true;
    }
    if (status == Response.Status.NOT_FOUND.getStatusCode()
        && operation == AdapterOperation.FROST_ENTITY_DELETE) {
      LOG.info(
          "FROST entity not found (404), treating delete as success (already deleted, idempotent)");
      return true;
    }
    return false;
  }
}
