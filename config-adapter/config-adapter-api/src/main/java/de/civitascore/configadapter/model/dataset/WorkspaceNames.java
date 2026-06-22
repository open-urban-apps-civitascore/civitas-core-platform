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

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Derives the GeoServer workspace name for a dataset from its id.
 *
 * <p>This is the single source of truth for the derivation: the GeoServer adapter uses it when
 * provisioning the workspace, and the APISIX adapter uses it when building the path-rewrite target
 * for an {@code OWS} (map services) named-API route. Both sides MUST agree on the same workspace
 * name for the same dataset id, so the rule lives here in {@code config-adapter-api} rather than
 * being duplicated per adapter.
 *
 * <p>The normalization is lossy — distinct dataset ids can collapse to the same workspace name
 * (e.g. {@code "ds-1"} and {@code "ds_1"} both become {@code "ds_1"}); callers must ensure dataset
 * ids are unique under this mapping.
 */
public final class WorkspaceNames {

  private static final Pattern NON_WORKSPACE_CHAR = Pattern.compile("[^a-z0-9_]");

  private WorkspaceNames() {}

  /**
   * Normalizes a dataset id into a GeoServer workspace name: lowercased, with every character that
   * is not {@code a-z}, {@code 0-9} or {@code _} replaced by {@code _}.
   *
   * @param datasetId the dataset id (must not be blank)
   * @return the workspace name
   * @throws IllegalArgumentException if {@code datasetId} is null or blank
   */
  public static String fromDatasetId(String datasetId) {
    if (datasetId == null || datasetId.isBlank()) {
      throw new IllegalArgumentException("datasetId must not be blank");
    }
    return NON_WORKSPACE_CHAR.matcher(datasetId.toLowerCase(Locale.ROOT)).replaceAll("_");
  }
}
