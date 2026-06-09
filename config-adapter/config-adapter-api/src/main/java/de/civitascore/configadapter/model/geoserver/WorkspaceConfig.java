/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.geoserver;

import de.civitascore.configadapter.model.AbstractApiModel;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration value for a GeoServer workspace. Produces the GeoServer REST API JSON:
 *
 * <pre>{@code
 * {
 *   "workspace": {
 *     "name": "civitas_dataset1",
 *     "isolated": false
 *   }
 * }
 * }</pre>
 *
 * <p>Jackson discriminator: {@code "resourceType": "geoserver-workspace"}
 */
public final class WorkspaceConfig extends AbstractApiModel implements GeoServerConfigValue {

  private String name;
  private Boolean isolated;

  public WorkspaceConfig() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Boolean getIsolated() {
    return isolated;
  }

  public void setIsolated(Boolean isolated) {
    this.isolated = isolated;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> workspace = new LinkedHashMap<>();
    if (name != null) workspace.put("name", name);
    if (isolated != null) workspace.put("isolated", isolated);
    additionalProperties().forEach(workspace::putIfAbsent);
    return Map.of("workspace", Collections.unmodifiableMap(workspace));
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (WorkspaceConfig) obj;
    return Objects.equals(name, that.name)
        && Objects.equals(isolated, that.isolated)
        && Objects.equals(additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, isolated, additionalProperties());
  }

  @Override
  public String toString() {
    return "WorkspaceConfig[name=" + name + ", isolated=" + isolated + ']';
  }
}
