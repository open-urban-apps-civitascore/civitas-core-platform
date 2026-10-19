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

import de.civitascore.configadapter.adapter.AbstractSagaCommandHandler.SagaApiException;
import de.civitascore.configadapter.util.BackoffCalculator;
import de.civitascore.configadapter.util.OkHttpJson;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin client for the APISIX Admin API route/upstream resources used by the dataset saga. Owns the
 * HTTP mechanics (auth header, etcd {@code value} envelope unwrap, 404 tolerance for idempotent
 * deletes) so the saga handler deals only in route state.
 *
 * <p>The OkHttp {@link OkHttpClient} is read through a {@link Supplier} on every call because the
 * handler may swap it after initialization (test seam).
 */
@SuppressWarnings("PMD.TooManyMethods")
final class ApisixAdminClient {

  private static final String ROUTES_PATH = "apisix/admin/routes/";
  private static final String UPSTREAMS_PATH = "apisix/admin/upstreams/";
  private static final String X_API_KEY = "X-API-KEY";

  /**
   * Retry budget for {@link #deleteUpstream}. Only the gateway's stale route-reference rejection is
   * retried, and only long enough for a worker's route cache to catch up — see {@link
   * UpstreamReferenceCheck}.
   */
  private static final int UPSTREAM_DELETE_MAX_ATTEMPTS = 3;

  private static final BackoffCalculator UPSTREAM_DELETE_BACKOFF = new BackoffCalculator(250, 1000);

  private static final Logger LOG = LoggerFactory.getLogger(ApisixAdminClient.class);

  private final Supplier<OkHttpClient> client;
  private final String adminApiUrl;
  private final String adminApiKey;

  ApisixAdminClient(Supplier<OkHttpClient> client, String adminApiUrl, String adminApiKey) {
    this.client = client;
    this.adminApiUrl = adminApiUrl;
    this.adminApiKey = adminApiKey;
  }

  /**
   * Reads the route, unwrapping the etcd {@code value} envelope. An absent route (404) yields
   * {@link Optional#empty()} so idempotent UPDATE/RESTORE compensation can skip it; any other
   * non-2xx throws.
   */
  Optional<Map<String, Object>> readRoute(String routeId) {
    try (Response response = execute(request(ROUTES_PATH + routeId).get().build())) {
      if (response.code() == HttpURLConnection.HTTP_NOT_FOUND) {
        return Optional.empty();
      }
      checkResponse(response, "GET route");
      Map<String, Object> responseBody = OkHttpJson.readJsonMap(response);
      Object value = responseBody.get("value");
      if (responseBody.containsKey("value") && !(value instanceof Map)) {
        // Guard the etcd-envelope unwrap: a 2xx with a non-object `value` (or an unexpected body
        // shape) would otherwise surface as an opaque ClassCastException. Fail with a clear
        // message.
        throw new SagaApiException(
            "GET route returned an unexpected body shape (value is not an object) for routeId="
                + Encode.forJava(routeId),
            502);
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> routeValue =
          responseBody.containsKey("value") ? (Map<String, Object>) value : responseBody;
      return Optional.of(routeValue);
    }
  }

  /** PUTs a route body, stripping APISIX-managed read-only fields the Admin API rejects. */
  void putRoute(String routeId, Map<String, Object> body, String operationDesc) {
    body.remove("create_time");
    body.remove("update_time");
    put(ROUTES_PATH + routeId, body, operationDesc);
  }

  void putUpstream(String upstreamId, Map<String, Object> body, String operationDesc) {
    put(UPSTREAMS_PATH + upstreamId, body, operationDesc);
  }

  /** See {@link #delete(String, String)}. */
  boolean deleteRoute(String routeId, String operationDesc) {
    return delete(ROUTES_PATH + routeId, operationDesc);
  }

  /**
   * See {@link #delete(String, String)}, plus a bounded retry while the gateway still reports a
   * stale route reference ({@link UpstreamReferenceCheck}): three attempts give the route cache
   * ~750ms to catch up. A reference still reported after that is surfaced rather than waited out,
   * since a route outliving its dataset must not be hidden by retrying longer.
   */
  boolean deleteUpstream(String upstreamId, String operationDesc) {
    for (int attempt = 1; ; attempt++) {
      try {
        return delete(UPSTREAMS_PATH + upstreamId, operationDesc);
      } catch (SagaApiException ex) {
        if (attempt >= UPSTREAM_DELETE_MAX_ATTEMPTS
            || !UpstreamReferenceCheck.isStaleRouteReference(ex.statusCode(), ex.getMessage())) {
          throw ex;
        }
        LOG.info(
            "{} — upstream {} still referenced by a route (attempt {}/{}); retrying after the"
                + " gateway route cache catches up",
            Encode.forJava(operationDesc),
            Encode.forJava(upstreamId),
            attempt,
            UPSTREAM_DELETE_MAX_ATTEMPTS);
        boolean interruptedWhileWaiting = false;
        try {
          Thread.sleep(UPSTREAM_DELETE_BACKOFF.calculate(attempt));
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          ex.addSuppressed(interrupted);
          interruptedWhileWaiting = true;
        }
        if (interruptedWhileWaiting) {
          // Surface the gateway rejection, not the interruption: it names the referencing route,
          // which is what the saga needs. The interruption rides along as a suppressed cause.
          throw ex;
        }
      }
    }
  }

  /**
   * Best-effort rollback of a partially-completed CREATE_ROUTE: deletes the given routes plus the
   * dataset upstream(s) created by the step (the FROST upstream and/or the map-server upstream).
   * Each delete is idempotent (404-tolerant) and any secondary failure is logged and swallowed so
   * the caller's original failure is the one propagated.
   */
  // The generic catches are deliberate: cleanup is best-effort and must swallow ANY runtime
  // failure so the caller's original provisioning failure stays the propagated one.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  void bestEffortCleanup(Iterable<String> upstreamIds, Iterable<String> routeIds, String sagaId) {
    for (String routeId : routeIds) {
      try {
        deleteRoute(routeId, "CLEANUP partial route");
      } catch (RuntimeException ex) {
        LOG.warn(
            "CLEANUP: could not delete partial route {} (saga={}) — may be orphaned on the gateway:"
                + " {}",
            Encode.forJava(routeId),
            Encode.forJava(sagaId),
            Encode.forJava(ex.getMessage()));
      }
    }
    for (String upstreamId : upstreamIds) {
      try {
        deleteUpstream(upstreamId, "CLEANUP partial upstream");
      } catch (RuntimeException ex) {
        LOG.warn(
            "CLEANUP: could not delete partial upstream {} (saga={}) — may be orphaned on the"
                + " gateway: {}",
            Encode.forJava(upstreamId),
            Encode.forJava(sagaId),
            Encode.forJava(ex.getMessage()));
      }
    }
  }

  private void put(String path, Map<String, Object> body, String operationDesc) {
    try (Response response =
        execute(request(path).put(OkHttpJson.jsonBodyUnchecked(body)).build())) {
      checkResponse(response, operationDesc);
    }
  }

  /**
   * Deletes a resource. Returns {@code true} if it was actually removed (2xx), {@code false} if it
   * was already absent (404). A 404 is tolerated — the desired end state is reached — so saga
   * DELETE and its compensation stay idempotent across re-runs and partial provisioning. Other
   * non-2xx still throw. The boolean lets callers detect a wholesale "nothing existed" miss.
   */
  private boolean delete(String path, String operationDesc) {
    try (Response response = execute(request(path).delete().build())) {
      if (response.code() == HttpURLConnection.HTTP_NOT_FOUND) {
        LOG.info(
            "{} — resource already absent (404): {}",
            Encode.forJava(operationDesc),
            Encode.forJava(path));
        return false;
      }
      checkResponse(response, operationDesc);
      return true;
    }
  }

  private Request.Builder request(String path) {
    HttpUrl url = HttpUrl.get(adminApiUrl).newBuilder().addPathSegments(path).build();
    return new Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .header(X_API_KEY, adminApiKey);
  }

  private Response execute(Request request) {
    return OkHttpJson.execute(client.get(), request);
  }

  /** Throws {@link SagaApiException} on non-2xx, mirroring the saga handler base contract. */
  private static void checkResponse(Response response, String operationDesc) {
    if (response.isSuccessful()) {
      return;
    }
    throw new SagaApiException(
        operationDesc + " failed: HTTP " + response.code() + " — " + OkHttpJson.readBody(response),
        response.code());
  }
}
