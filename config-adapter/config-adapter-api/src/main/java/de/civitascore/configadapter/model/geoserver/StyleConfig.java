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
 * Configuration value for a GeoServer style (SLD). This creates the style metadata record; the SLD
 * document body is uploaded separately via a dedicated GeoServer REST endpoint.
 *
 * <p>Produces the GeoServer REST API JSON:
 *
 * <pre>{@code
 * {
 *   "style": {
 *     "name": "traffic_style",
 *     "filename": "traffic_style.sld",
 *     "format": "sld",
 *     "languageVersion": {"version": "1.0.0"},
 *     "workspace": {"name": "civitas_dataset1"}
 *   }
 * }
 * }</pre>
 *
 * <p>The {@code workspace} field is optional and only included for workspace-scoped styles. Omit it
 * for global styles.
 *
 * <p>Jackson discriminator: {@code "resourceType": "geoserver-style"}
 */
public final class StyleConfig extends AbstractApiModel implements GeoServerConfigValue {

  private String name;
  private String filename;
  private String format;

  /** SLD language version string, e.g. {@code "1.0.0"} or {@code "1.1.0"}. */
  private String languageVersion;

  /** Workspace name for workspace-scoped styles. {@code null} for global styles. */
  private String workspace;

  public StyleConfig() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public String getFormat() {
    return format;
  }

  public void setFormat(String format) {
    this.format = format;
  }

  public String getLanguageVersion() {
    return languageVersion;
  }

  public void setLanguageVersion(String languageVersion) {
    this.languageVersion = languageVersion;
  }

  public String getWorkspace() {
    return workspace;
  }

  public void setWorkspace(String workspace) {
    this.workspace = workspace;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> style = new LinkedHashMap<>();
    if (name != null) style.put("name", name);
    if (filename != null) style.put("filename", filename);
    if (format != null) style.put("format", format);
    if (languageVersion != null) style.put("languageVersion", Map.of("version", languageVersion));
    if (workspace != null) style.put("workspace", Map.of("name", workspace));
    additionalProperties().forEach(style::putIfAbsent);
    return Map.of("style", Collections.unmodifiableMap(style));
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (StyleConfig) obj;
    return Objects.equals(name, that.name)
        && Objects.equals(filename, that.filename)
        && Objects.equals(format, that.format)
        && Objects.equals(languageVersion, that.languageVersion)
        && Objects.equals(workspace, that.workspace)
        && Objects.equals(additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, filename, format, languageVersion, workspace, additionalProperties());
  }

  @Override
  public String toString() {
    return "StyleConfig[name=" + name + ", format=" + format + ", workspace=" + workspace + ']';
  }
}
