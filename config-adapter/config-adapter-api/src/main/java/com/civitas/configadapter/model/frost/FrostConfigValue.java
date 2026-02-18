/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model.frost;

import com.civitas.configadapter.model.AbstractApiModel;
import com.civitas.configadapter.model.ConfigValue;
import java.util.Collections;
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
public final class FrostConfigValue extends AbstractApiModel implements ConfigValue {

  public static final String FROST_RESULT_TYPE = "de.civitascore.data.processing.result";

  private String name;
  private String description;
  private Map<String, Object> properties;
  private String encodingType;
  private Map<String, Object> location;
  private String definition;
  private UnitOfMeasurement unitOfMeasurement;
  private String observationType;

  /**
   * Arbitrary metadata; structure depends on the SensorThings entity type. Typically a {@link
   * String} or a {@link java.util.Map Map&lt;String, Object&gt;} that Jackson can serialize to
   * JSON. For Sensor entities this is often a document URL or description string.
   */
  private Object metadata;

  public FrostConfigValue() {}

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

  public Map<String, Object> getProperties() {
    return properties;
  }

  public void setProperties(Map<String, Object> properties) {
    this.properties = properties;
  }

  public String getEncodingType() {
    return encodingType;
  }

  public void setEncodingType(String encodingType) {
    this.encodingType = encodingType;
  }

  public Map<String, Object> getLocation() {
    return location;
  }

  public void setLocation(Map<String, Object> location) {
    this.location = location;
  }

  public String getDefinition() {
    return definition;
  }

  public void setDefinition(String definition) {
    this.definition = definition;
  }

  public UnitOfMeasurement getUnitOfMeasurement() {
    return unitOfMeasurement;
  }

  public void setUnitOfMeasurement(UnitOfMeasurement unitOfMeasurement) {
    this.unitOfMeasurement = unitOfMeasurement;
  }

  public String getObservationType() {
    return observationType;
  }

  public void setObservationType(String observationType) {
    this.observationType = observationType;
  }

  public Object getMetadata() {
    return metadata;
  }

  public void setMetadata(Object metadata) {
    this.metadata = metadata;
  }

  /**
   * Converts typed fields and additional properties to a plain map for the FROST-Server API.
   *
   * @return an unmodifiable map of configuration key-value pairs
   */
  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (name != null) map.put("name", name);
    if (description != null) map.put("description", description);
    if (properties != null) map.put("properties", properties);
    if (encodingType != null) map.put("encodingType", encodingType);
    if (location != null) map.put("location", location);
    if (definition != null) map.put("definition", definition);
    if (unitOfMeasurement != null) {
      Map<String, Object> uom = unitOfMeasurement.toApiMap();
      if (!uom.isEmpty()) map.put("unitOfMeasurement", uom);
    }
    if (observationType != null) map.put("observationType", observationType);
    if (metadata != null) map.put("metadata", metadata);
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (FrostConfigValue) obj;
    return Objects.equals(this.name, that.name)
        && Objects.equals(this.description, that.description)
        && Objects.equals(this.properties, that.properties)
        && Objects.equals(this.encodingType, that.encodingType)
        && Objects.equals(this.location, that.location)
        && Objects.equals(this.definition, that.definition)
        && Objects.equals(this.unitOfMeasurement, that.unitOfMeasurement)
        && Objects.equals(this.observationType, that.observationType)
        && Objects.equals(this.metadata, that.metadata)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        name,
        description,
        properties,
        encodingType,
        location,
        definition,
        unitOfMeasurement,
        observationType,
        metadata,
        additionalProperties());
  }

  @Override
  public String toString() {
    return "FrostConfigValue["
        + "name="
        + name
        + ", description="
        + description
        + ", properties="
        + properties
        + ", encodingType="
        + encodingType
        + ", location="
        + location
        + ", definition="
        + definition
        + ", unitOfMeasurement="
        + unitOfMeasurement
        + ", observationType="
        + observationType
        + ", metadata="
        + metadata
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}
