/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.dataset;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Derives APISIX route IDs for {@link NamedApi} entries from a stable {@code (datasetId, slug)}
 * pair.
 *
 * <p>The derivation is part of the saga contract: any code that creates routes for named APIs MUST
 * use this helper so route IDs persisted on portal-backend entities remain valid across changes to
 * the underlying provisioning implementation. Both the orchestrator's result-aggregation path and
 * the APISIX adapter's route-creation path must agree on the same UUID for the same input.
 */
public final class NamedApiRouteIds {

  private NamedApiRouteIds() {}

  /**
   * Derive the deterministic UUID route ID for a named API on a given dataset. {@link
   * UUID#nameUUIDFromBytes(byte[])} guarantees the same input always produces the same UUID.
   */
  public static String derive(String datasetId, String slug) {
    String input = datasetId + "/" + slug;
    return UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8)).toString();
  }
}
