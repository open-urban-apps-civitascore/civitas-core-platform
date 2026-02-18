/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model.dataset;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A datasource connection within a dataset. Contains common fields shared across all datasource
 * types (postgresql, mqtt, etc.) and captures type-specific fields via {@link JsonAnySetter}.
 *
 * <p>This follows the same pattern as {@link com.civitas.configadapter.model.AbstractApiModel} for
 * handling unknown JSON properties, but is not an API model (no {@code toApiMap()}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Datasource {

  private static final Logger LOG = LoggerFactory.getLogger(Datasource.class);

  private String id;
  private String type;
  private String name;
  private String description;
  private String host;
  private Integer port;

  private final Map<String, Object> additionalProperties = new LinkedHashMap<>();
  private final Map<String, Object> additionalPropertiesView =
      Collections.unmodifiableMap(additionalProperties);

  public Datasource() {}

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public Integer getPort() {
    return port;
  }

  public void setPort(Integer port) {
    this.port = port;
  }

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return additionalPropertiesView;
  }

  @JsonAnySetter
  public void handleUnknownProperty(String key, Object value) {
    LOG.debug("Unknown datasource property '{}' for type '{}'", Encode.forJava(key), type);
    additionalProperties.put(key, value);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (Datasource) obj;
    return Objects.equals(this.id, that.id)
        && Objects.equals(this.type, that.type)
        && Objects.equals(this.name, that.name)
        && Objects.equals(this.description, that.description)
        && Objects.equals(this.host, that.host)
        && Objects.equals(this.port, that.port)
        && Objects.equals(this.additionalProperties, that.additionalProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, type, name, description, host, port, additionalProperties);
  }

  @Override
  public String toString() {
    return "Datasource["
        + "id="
        + id
        + ", type="
        + type
        + ", name="
        + name
        + ", host="
        + host
        + ", port="
        + port
        + ", additionalProperties="
        + additionalProperties.keySet()
        + ']';
  }
}
