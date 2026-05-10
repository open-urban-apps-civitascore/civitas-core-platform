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
 * Configuration value for a GeoServer layer (WMS layer). Wraps a feature type or coverage for WMS
 * publication, optionally associating a default style.
 *
 * <p>Produces the GeoServer REST API JSON:
 *
 * <pre>{@code
 * {
 *   "layer": {
 *     "name": "traffic_counts",
 *     "title": "Traffic Counts",
 *     "type": "VECTOR",
 *     "defaultStyle": {"name": "traffic_style"},
 *     "enabled": true,
 *     "queryable": true
 *   }
 * }
 * }</pre>
 *
 * <p>Valid {@code type} values: {@code VECTOR}, {@code RASTER}, {@code REMOTE}, {@code WMS}, {@code
 * GROUP}.
 *
 * <p>Jackson discriminator: {@code "resourceType": "geoserver-layer"}
 */
public final class LayerConfig extends AbstractApiModel implements GeoServerConfigValue {

  private String name;
  private String title;
  private String type;
  private String defaultStyle;
  private Boolean enabled;
  private Boolean queryable;

  public LayerConfig() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  /** Name of the default style to associate with this layer. */
  public String getDefaultStyle() {
    return defaultStyle;
  }

  public void setDefaultStyle(String defaultStyle) {
    this.defaultStyle = defaultStyle;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getQueryable() {
    return queryable;
  }

  public void setQueryable(Boolean queryable) {
    this.queryable = queryable;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> layer = new LinkedHashMap<>();
    if (name != null) layer.put("name", name);
    if (title != null) layer.put("title", title);
    if (type != null) layer.put("type", type);
    if (defaultStyle != null) layer.put("defaultStyle", Map.of("name", defaultStyle));
    if (enabled != null) layer.put("enabled", enabled);
    if (queryable != null) layer.put("queryable", queryable);
    additionalProperties().forEach(layer::putIfAbsent);
    return Map.of("layer", Collections.unmodifiableMap(layer));
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (LayerConfig) obj;
    return Objects.equals(name, that.name)
        && Objects.equals(title, that.title)
        && Objects.equals(type, that.type)
        && Objects.equals(defaultStyle, that.defaultStyle)
        && Objects.equals(enabled, that.enabled)
        && Objects.equals(queryable, that.queryable)
        && Objects.equals(additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        name, title, type, defaultStyle, enabled, queryable, additionalProperties());
  }

  @Override
  public String toString() {
    return "LayerConfig[name=" + name + ", type=" + type + ", defaultStyle=" + defaultStyle + ']';
  }
}
