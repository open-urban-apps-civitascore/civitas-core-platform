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

import jakarta.ws.rs.core.Response;

/**
 * Recognizes the APISIX Admin API's "upstream still referenced by a route" rejection.
 *
 * <p>APISIX refuses to delete an upstream that a route still points at, but it resolves that
 * reference against {@code apisix.router.http_routes()} — each worker's etcd-synced route cache,
 * not etcd itself. A route DELETE that already returned 200 can therefore still be visible to the
 * reference check, and the follow-up upstream DELETE is rejected for a route that is already gone.
 *
 * <p>The rejection is transient by nature: it clears once the worker's cache catches up. Callers
 * retry rather than tolerate it — tolerating would leave the upstream orphaned on the gateway.
 */
final class UpstreamReferenceCheck {

  /**
   * Markers in the Admin API's 400 body. The message is {@code <kind> [<id>] is still using it now}
   * for any referencing kind (route, service, plugin_config, consumer, ...), so both markers are
   * required: only a route reference is cache lag, and a service or plugin_config reference is
   * static configuration that waiting never clears. {@code route [} also matches {@code plugin in
   * route [...]}.
   */
  private static final String STILL_REFERENCED = "is still using it now";

  private static final String ROUTE_REFERENCE = "route [";

  private UpstreamReferenceCheck() {}

  /** True if the response is APISIX rejecting an upstream delete for a still-referencing route. */
  static boolean isStaleRouteReference(int status, String body) {
    return status == Response.Status.BAD_REQUEST.getStatusCode()
        && body != null
        && body.contains(STILL_REFERENCED)
        && body.contains(ROUTE_REFERENCE);
  }
}
