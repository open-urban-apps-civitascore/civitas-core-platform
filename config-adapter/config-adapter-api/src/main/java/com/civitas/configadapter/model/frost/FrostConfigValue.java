/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.model.frost;

import com.civitas.configadapter.model.ConfigValue;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration value for FROST-Server SensorThings API entities.
 *
 * <p>Supports the following OGC SensorThings API entity types:
 *
 * <ul>
 *   <li>Thing - A physical or virtual object capable of being identified and integrated
 *   <li>Location - The geospatial location of a Thing
 *   <li>Sensor - An instrument that observes a property
 *   <li>ObservedProperty - The phenomenon being observed
 *   <li>Datastream - A collection of Observations measuring the same ObservedProperty
 * </ul>
 *
 * <p>Additionally supports FROST-Server specific entity types:
 *
 * <ul>
 *   <li>Project - A FROST project that scopes access to SensorThings entities
 * </ul>
 *
 * <p>Example Thing configuration:
 *
 * <pre>{@code
 * {
 *   "name": "Temperature Sensor Unit 1",
 *   "description": "A temperature sensor in building A",
 *   "properties": {
 *     "serialNumber": "TMP-001",
 *     "manufacturer": "SensorCorp"
 *   }
 * }
 * }</pre>
 *
 * <p>Example Location configuration:
 *
 * <pre>{@code
 * {
 *   "name": "Building A, Room 101",
 *   "description": "Main entrance sensor location",
 *   "encodingType": "application/geo+json",
 *   "location": {
 *     "type": "Point",
 *     "coordinates": [8.4037, 49.0069]
 *   }
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class FrostConfigValue implements ConfigValue {

  public static final String FROST_RESULT_TYPE = "de.civitascore.data.processing.result";

  private final Map<String, Object> data;

  @JsonCreator
  public FrostConfigValue() {
    this.data = new LinkedHashMap<>();
  }

  public FrostConfigValue(Map<String, Object> data) {
    this.data = data != null ? new LinkedHashMap<>(data) : new LinkedHashMap<>();
  }

  @JsonAnyGetter
  public Map<String, Object> data() {
    return data;
  }

  @JsonAnySetter
  public void setProperty(String key, Object value) {
    data.put(key, value);
  }

  /**
   * Get a property value by key.
   *
   * @param key the property key
   * @return the property value, or null if not present
   */
  public Object get(String key) {
    return data.get(key);
  }

  /**
   * Check if a property exists.
   *
   * @param key the property key
   * @return true if the property exists
   */
  public boolean has(String key) {
    return data.containsKey(key);
  }

  /**
   * Get the entity name (common to all SensorThings entities).
   *
   * @return the entity name, or null if not specified
   */
  public String getName() {
    return (String) data.get("name");
  }

  /**
   * Get the entity description (common to all SensorThings entities).
   *
   * @return the entity description, or null if not specified
   */
  public String getDescription() {
    return (String) data.get("description");
  }

  /**
   * Get the properties map (user-defined metadata).
   *
   * @return map of custom properties, or null if not specified
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> getProperties() {
    return (Map<String, Object>) data.get("properties");
  }

  /**
   * Get the encoding type (for Location entities).
   *
   * @return the encoding type (e.g., "application/geo+json"), or null if not specified
   */
  public String getEncodingType() {
    return (String) data.get("encodingType");
  }

  /**
   * Get the location object (for Location entities).
   *
   * @return the GeoJSON location object, or null if not specified
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> getLocation() {
    return (Map<String, Object>) data.get("location");
  }

  /**
   * Get the definition URI (for Sensor and ObservedProperty entities).
   *
   * @return the definition URI, or null if not specified
   */
  public String getDefinition() {
    return (String) data.get("definition");
  }

  /**
   * Get the unit of measurement (for Datastream entities).
   *
   * @return the unit of measurement object, or null if not specified
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> getUnitOfMeasurement() {
    return (Map<String, Object>) data.get("unitOfMeasurement");
  }

  /**
   * Get the observation type (for Datastream entities).
   *
   * @return the observation type URI, or null if not specified
   */
  public String getObservationType() {
    return (String) data.get("observationType");
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (FrostConfigValue) obj;
    return Objects.equals(this.data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(data);
  }

  @Override
  public String toString() {
    return "FrostConfigValue[" + "data=" + data + ']';
  }
}
