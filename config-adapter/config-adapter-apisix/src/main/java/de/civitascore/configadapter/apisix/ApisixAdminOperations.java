/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.apisix;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.AdapterOperation;
import de.civitascore.configadapter.util.OkHttpJson;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.function.Supplier;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CRUD operations against the APISIX Admin API for the config-event flow, with the adapter
 * framework's error classification:
 *
 * <ul>
 *   <li>Network errors ({@link IOException}) → {@link RetryableAdapterException} ({@code
 *       NETWORK_ERROR})
 *   <li>HTTP 5xx → {@link RetryableAdapterException} ({@code SERVICE_UNAVAILABLE})
 *   <li>HTTP 409 on CREATE / 404 on DELETE → success (idempotent)
 *   <li>HTTP 400 on UPSTREAM_DELETE naming a still-referencing route → {@link
 *       RetryableAdapterException} ({@code SERVICE_UNAVAILABLE}), see {@link
 *       UpstreamReferenceCheck}
 *   <li>Other HTTP 4xx and unexpected runtime failures → {@link FatalAdapterException}
 * </ul>
 *
 * <p>The OkHttp {@link OkHttpClient} is read through a {@link Supplier} on every call because the
 * adapter may swap it after construction (test seam).
 */
final class ApisixAdminOperations {

  /** The two APISIX resource types the config-event flow manages. */
  enum ResourceKind {
    UPSTREAM(
        "upstream",
        "/apisix/admin/upstreams",
        AdapterErrorCode.APISIX_UPSTREAM_ERROR,
        AdapterOperation.UPSTREAM_CREATE,
        AdapterOperation.UPSTREAM_UPDATE,
        AdapterOperation.UPSTREAM_DELETE),
    ROUTE(
        "route",
        "/apisix/admin/routes",
        AdapterErrorCode.APISIX_ROUTE_ERROR,
        AdapterOperation.ROUTE_CREATE,
        AdapterOperation.ROUTE_UPDATE,
        AdapterOperation.ROUTE_DELETE);

    private final String resourceLabel;
    private final String collectionPath;
    private final AdapterErrorCode errorCode;
    private final AdapterOperation createOp;
    private final AdapterOperation updateOp;
    private final AdapterOperation deleteOp;

    ResourceKind(
        String label,
        String collectionPath,
        AdapterErrorCode errorCode,
        AdapterOperation createOp,
        AdapterOperation updateOp,
        AdapterOperation deleteOp) {
      this.resourceLabel = label;
      this.collectionPath = collectionPath;
      this.errorCode = errorCode;
      this.createOp = createOp;
      this.updateOp = updateOp;
      this.deleteOp = deleteOp;
    }

    String label() {
      return resourceLabel;
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(ApisixAdminOperations.class);
  private static final String X_API_KEY = "X-API-KEY";

  private final Supplier<OkHttpClient> client;
  private final String adminApiUrl;
  private final String adminApiKey;

  ApisixAdminOperations(Supplier<OkHttpClient> client, String adminApiUrl, String adminApiKey) {
    this.client = client;
    this.adminApiUrl = adminApiUrl;
    this.adminApiKey = adminApiKey;
  }

  /**
   * Builds the request. Declares {@link IOException} so a JSON serialization failure (via Jackson)
   * and OkHttp's checked network-failure exception both flow into the same {@code catch} in {@link
   * #execute}.
   */
  @FunctionalInterface
  private interface RequestSupplier {
    Request get() throws IOException;
  }

  void create(ResourceKind kind, Object config)
      throws FatalAdapterException, RetryableAdapterException {
    execute(
        kind, kind.createOp, () -> request(kind, null).post(OkHttpJson.jsonBody(config)).build());
  }

  void update(ResourceKind kind, String id, Object config)
      throws FatalAdapterException, RetryableAdapterException {
    execute(kind, kind.updateOp, () -> request(kind, id).put(OkHttpJson.jsonBody(config)).build());
  }

  void delete(ResourceKind kind, String id)
      throws FatalAdapterException, RetryableAdapterException {
    execute(kind, kind.deleteOp, () -> request(kind, id).delete().build());
  }

  /** Executes the HTTP request with the framework's standardized error classification. */
  // The RuntimeException catch is a deliberate boundary: an unexpected failure must surface as a
  // classified FatalAdapterException (failure result + DLQ), not crash the consumer loop.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private void execute(ResourceKind kind, AdapterOperation operation, RequestSupplier request)
      throws FatalAdapterException, RetryableAdapterException {
    try (Response response = client.get().newCall(request.get()).execute()) {
      handleHttpResponse(response, kind.errorCode, operation);
    } catch (IOException e) {
      LOG.warn(
          "Network error during {}: {}",
          operation.getDescription(),
          Encode.forJava(String.valueOf(e.getMessage())));
      throw new RetryableAdapterException(
          AdapterErrorCode.NETWORK_ERROR, e, ApisixAdapter.ADAPTER_NAME, e.getMessage());
    } catch (RuntimeException e) {
      LOG.error("Failed to execute {}", operation.getDescription(), e);
      throw new FatalAdapterException(
          kind.errorCode, e, operation.getDescription() + " failed: " + e.getMessage());
    }
  }

  private Request.Builder request(ResourceKind kind, String id) {
    String path = id == null ? kind.collectionPath : kind.collectionPath + "/" + id;
    HttpUrl url =
        HttpUrl.get(adminApiUrl).newBuilder().addPathSegments(path.replaceFirst("^/", "")).build();
    return new Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .header(X_API_KEY, adminApiKey);
  }

  /**
   * Throws the classified exception for error status codes: 5xx retryable, 4xx fatal — except the
   * idempotent no-ops (409 on CREATE: already exists; 404 on DELETE: already gone) and a stale
   * route reference on UPSTREAM_DELETE, which is retryable rather than fatal.
   */
  private void handleHttpResponse(
      Response response, AdapterErrorCode errorCode, AdapterOperation operation)
      throws RetryableAdapterException, FatalAdapterException, IOException {
    int status = response.code();
    if (response.isSuccessful() || isIdempotentNoOp(status, operation)) {
      return;
    }

    String body = response.body().string();
    if (operation == AdapterOperation.UPSTREAM_DELETE
        && UpstreamReferenceCheck.isStaleRouteReference(status, body)) {
      // Not a client error: the referencing route may already be deleted and merely still visible
      // in the gateway's route cache. Retryable so the framework's backoff re-attempts it, rather
      // than DLQ-ing the delete and leaving the upstream orphaned.
      LOG.warn(
          "APISIX upstream still referenced by a route during {}: {} {}",
          operation.getDescription(),
          status,
          Encode.forJava(body));
      throw new RetryableAdapterException(
          AdapterErrorCode.SERVICE_UNAVAILABLE, ApisixAdapter.ADAPTER_NAME, status);
    }
    if (status >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
      LOG.warn(
          "APISIX server error during {}: {} {}",
          operation.getDescription(),
          status,
          Encode.forJava(body));
      throw new RetryableAdapterException(
          AdapterErrorCode.SERVICE_UNAVAILABLE, ApisixAdapter.ADAPTER_NAME, status);
    }
    LOG.error(
        "APISIX client error during {}: {} {}",
        operation.getDescription(),
        status,
        Encode.forJava(body));
    throw new FatalAdapterException(errorCode, "HTTP " + status + ": " + body);
  }

  private static boolean isIdempotentNoOp(int status, AdapterOperation operation) {
    if (status == HttpURLConnection.HTTP_CONFLICT
        && (operation == AdapterOperation.UPSTREAM_CREATE
            || operation == AdapterOperation.ROUTE_CREATE)) {
      LOG.info("APISIX resource already exists (409), treating create as success (idempotent)");
      return true;
    }
    if (status == HttpURLConnection.HTTP_NOT_FOUND
        && (operation == AdapterOperation.UPSTREAM_DELETE
            || operation == AdapterOperation.ROUTE_DELETE)) {
      LOG.info(
          "APISIX resource not found (404), treating delete as success (already deleted,"
              + " idempotent)");
      return true;
    }
    return false;
  }
}
