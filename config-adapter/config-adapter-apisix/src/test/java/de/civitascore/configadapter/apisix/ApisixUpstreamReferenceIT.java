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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link UpstreamReferenceCheck} to the live Admin API's rejection wording, which both the
 * saga retry and the retryable classification depend on. A gateway upgrade that rewords the message
 * would otherwise disable them silently.
 */
class ApisixUpstreamReferenceIT extends AbstractApisixIT {

  @Test
  @DisplayName("a route reference is recognized as the retryable stale-reference rejection")
  void shouldRecognizeRouteReferenceFromLiveGateway() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String upstreamId = createDefaultUpstream(suffix);
    String routeId = "test-route-" + suffix;
    createRouteDirectly(
        routeId, Map.of("uri", "/reference-check/" + suffix, "upstream_id", upstreamId));

    try {
      HttpResponse<String> response = deleteUpstream(upstreamId);

      assertEquals(400, response.statusCode(), "the gateway must refuse a referenced upstream");
      assertTrue(
          UpstreamReferenceCheck.isStaleRouteReference(response.statusCode(), response.body()),
          "route-reference rejection not recognized — the Admin API wording changed: "
              + response.body());
    } finally {
      deleteRoute(routeId);
      awaitUpstreamDeleted(upstreamId);
    }
  }

  @Test
  @DisplayName("a service reference is refused but is NOT treated as the retryable rejection")
  void shouldNotRecognizeServiceReferenceFromLiveGateway() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String upstreamId = createDefaultUpstream(suffix);
    String serviceId = "test-service-" + suffix;
    createServiceDirectly(serviceId, Map.of("upstream_id", upstreamId));

    try {
      HttpResponse<String> response = deleteUpstream(upstreamId);

      // The gateway reuses one message for every referencing kind, so the trailing phrase alone
      // would match here too. A service reference is static configuration that waiting never
      // clears, so it must fail on the first attempt rather than burn the retry budget.
      assertEquals(400, response.statusCode(), "the gateway must refuse a referenced upstream");
      assertFalse(
          UpstreamReferenceCheck.isStaleRouteReference(response.statusCode(), response.body()),
          "a service reference must not be retried as route-cache lag: " + response.body());
    } finally {
      deleteService(serviceId);
      awaitUpstreamDeleted(upstreamId);
    }
  }

  /**
   * Deletes the upstream once the gateway stops reporting a reference to it. Teardown races the
   * very cache lag these tests provoke, so a single delete can be refused and leak the upstream
   * into the container the whole suite shares.
   */
  private void awaitUpstreamDeleted(String upstreamId) {
    await()
        .atMost(10, SECONDS)
        .pollInterval(250, MILLISECONDS)
        .untilAsserted(
            () -> {
              HttpResponse<String> response = deleteUpstream(upstreamId);
              assertTrue(
                  response.statusCode() < 300 || response.statusCode() == 404,
                  "upstream " + upstreamId + " not torn down: " + response.body());
            });
  }

  private HttpResponse<String> deleteUpstream(String upstreamId) throws Exception {
    return adminDelete("/apisix/admin/upstreams/" + upstreamId);
  }

  private HttpResponse<String> deleteRoute(String routeId) throws Exception {
    return adminDelete("/apisix/admin/routes/" + routeId);
  }

  private HttpResponse<String> deleteService(String serviceId) throws Exception {
    return adminDelete("/apisix/admin/services/" + serviceId);
  }

  private HttpResponse<String> adminDelete(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(adminApiUrl + path))
            .header("X-API-KEY", ADMIN_API_KEY)
            .DELETE()
            .timeout(Duration.ofSeconds(30))
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
