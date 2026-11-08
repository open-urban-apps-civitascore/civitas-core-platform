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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Saga-contract shape of a dashboard, carried alongside the dataset on {@code DATASET_CREATE} and
 * {@code DATASET_UPDATE} triggers. The dashboard engine (Superset) materialises a dashboard from
 * this and reports its engine id back asynchronously.
 *
 * <p>This is the <b>forward</b> payload. The engine source projections and each source's {@code
 * mappedDatasetId} scope binding flow back the other way (engine → platform) and are not on this
 * record.
 *
 * <p>{@code id} is the platform-side dashboard UUID and doubles as the correlation key: the engine
 * echoes it so the platform can match the returned {@code externalDashboardId} to the right row
 * (mirrors how named APIs correlate by {@code slug}).
 *
 * <p>{@code externalDashboardId} tells the engine whether this is a create or an update:
 *
 * <ul>
 *   <li>{@code null} — the platform has no engine id yet; the engine <b>creates</b> a new dashboard
 *       and reports the assigned id back in the result.
 *   <li>non-null — the dashboard already exists in the engine; the engine <b>updates it in
 *       place</b> under the same id, so the id stays stable (saved filters, permissions, references
 *       survive).
 * </ul>
 *
 * @param id platform dashboard UUID; correlation key for the confirmation
 * @param modelUrn logical CORE URN of the authoritative Model Forge artifact
 * @param externalDashboardId engine id when known (update), else {@code null} (create)
 * @param editorGroupIds CORE group IDs allowed to edit the engine dashboard
 * @param viewerGroupIds CORE group IDs allowed to view the published dashboard
 * @param publicAccess whether the parent CORE dataset is public
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record Dashboard(
    String id,
    String modelUrn,
    String externalDashboardId,
    List<String> editorGroupIds,
    List<String> viewerGroupIds,
    boolean publicAccess) {
  public Dashboard {
    editorGroupIds = editorGroupIds == null ? List.of() : List.copyOf(editorGroupIds);
    viewerGroupIds = viewerGroupIds == null ? List.of() : List.copyOf(viewerGroupIds);
  }
}
