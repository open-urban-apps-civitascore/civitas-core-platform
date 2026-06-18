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

import com.fasterxml.jackson.annotation.JsonProperty;
import de.civitascore.configadapter.model.AbstractApiModel;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration value for a GeoServer feature type (WFS layer). Maps to a single PostGIS table
 * exposed via the WFS protocol.
 *
 * <p>Produces the GeoServer REST API JSON:
 *
 * <pre>{@code
 * {
 *   "featureType": {
 *     "name": "traffic_counts",
 *     "nativeName": "traffic_counts",
 *     "title": "Traffic Counts",
 *     "abstract": "Traffic counting data",
 *     "srs": "EPSG:4326",
 *     "projectionPolicy": "REPROJECT_TO_DECLARED",
 *     "enabled": true,
 *     "nativeBoundingBox":  {"minx": -180.0, "maxx": 180.0, "miny": -90.0, "maxy": 90.0, "crs": "EPSG:4326"},
 *     "latLonBoundingBox":  {"minx": -180.0, "maxx": 180.0, "miny": -90.0, "maxy": 90.0, "crs": "EPSG:4326"}
 *   }
 * }
 * }</pre>
 *
 * <p>Jackson discriminator: {@code "resourceType": "geoserver-featuretype"}
 */
public final class FeatureTypeConfig extends AbstractApiModel implements GeoServerConfigValue {

  private String name;
  private String nativeName;
  private String title;

  @JsonProperty("abstract")
  private String abstractText;

  private String srs;
  private BoundingBox nativeBoundingBox;
  private BoundingBox latLonBoundingBox;

  /**
   * GeoServer projection policy. Valid values: {@code NONE}, {@code REPROJECT_TO_DECLARED}, {@code
   * FORCE_DECLARED}.
   */
  private String projectionPolicy;

  private Boolean enabled;
  private Boolean advertised;

  public FeatureTypeConfig() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getNativeName() {
    return nativeName;
  }

  public void setNativeName(String nativeName) {
    this.nativeName = nativeName;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  @JsonProperty("abstract")
  public String getAbstractText() {
    return abstractText;
  }

  @JsonProperty("abstract")
  public void setAbstractText(String abstractText) {
    this.abstractText = abstractText;
  }

  public String getSrs() {
    return srs;
  }

  public void setSrs(String srs) {
    this.srs = srs;
  }

  public BoundingBox getNativeBoundingBox() {
    return nativeBoundingBox;
  }

  public void setNativeBoundingBox(BoundingBox nativeBoundingBox) {
    this.nativeBoundingBox = nativeBoundingBox;
  }

  public BoundingBox getLatLonBoundingBox() {
    return latLonBoundingBox;
  }

  public void setLatLonBoundingBox(BoundingBox latLonBoundingBox) {
    this.latLonBoundingBox = latLonBoundingBox;
  }

  public String getProjectionPolicy() {
    return projectionPolicy;
  }

  public void setProjectionPolicy(String projectionPolicy) {
    this.projectionPolicy = projectionPolicy;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getAdvertised() {
    return advertised;
  }

  public void setAdvertised(Boolean advertised) {
    this.advertised = advertised;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> ft = new LinkedHashMap<>();
    if (name != null) ft.put("name", name);
    if (nativeName != null) ft.put("nativeName", nativeName);
    if (title != null) ft.put("title", title);
    if (abstractText != null) ft.put("abstract", abstractText);
    if (srs != null) ft.put("srs", srs);
    if (nativeBoundingBox != null) ft.put("nativeBoundingBox", nativeBoundingBox.toMap());
    if (latLonBoundingBox != null) ft.put("latLonBoundingBox", latLonBoundingBox.toMap());
    if (projectionPolicy != null) ft.put("projectionPolicy", projectionPolicy);
    if (enabled != null) ft.put("enabled", enabled);
    if (advertised != null) ft.put("advertised", advertised);
    additionalProperties().forEach(ft::putIfAbsent);
    return Map.of("featureType", Collections.unmodifiableMap(ft));
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (FeatureTypeConfig) obj;
    return Objects.equals(name, that.name)
        && Objects.equals(nativeName, that.nativeName)
        && Objects.equals(title, that.title)
        && Objects.equals(abstractText, that.abstractText)
        && Objects.equals(srs, that.srs)
        && Objects.equals(nativeBoundingBox, that.nativeBoundingBox)
        && Objects.equals(latLonBoundingBox, that.latLonBoundingBox)
        && Objects.equals(projectionPolicy, that.projectionPolicy)
        && Objects.equals(enabled, that.enabled)
        && Objects.equals(advertised, that.advertised)
        && Objects.equals(additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        name,
        nativeName,
        title,
        abstractText,
        srs,
        nativeBoundingBox,
        latLonBoundingBox,
        projectionPolicy,
        enabled,
        advertised,
        additionalProperties());
  }

  @Override
  public String toString() {
    return "FeatureTypeConfig[name="
        + name
        + ", srs="
        + srs
        + ", projectionPolicy="
        + projectionPolicy
        + ']';
  }
}
