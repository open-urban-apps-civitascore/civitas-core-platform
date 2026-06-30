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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Engine-neutral reads over a DataStructure JSON Schema (the model the portal stores on a
 * data-structure version and ships at {@code datasinks[].dataStructure}).
 *
 * <p>This is the single source of truth for deriving the conceptual primary key from the {@code
 * x-core-primaryKey} marker, so the PostGIS adapter (which sets the table {@code PRIMARY KEY}) and
 * the NiFi adapter (which sets PutDatabaseRecord {@code Update Keys} for UPSERT) can never disagree
 * on what the key columns are.
 */
public final class DataStructureSchema {

  /** JSON Schema extension keyword carrying the conceptual primary key (UML {@code {id}}). */
  public static final String PRIMARY_KEY_MARKER = "x-core-primaryKey";

  private DataStructureSchema() {}

  /**
   * The property names marked {@code x-core-primaryKey} in the schema's main definition, in
   * declaration order; empty if the schema is null, has no usable definition, or marks none.
   */
  @SuppressWarnings("unchecked")
  public static List<String> primaryKeyColumns(Map<String, Object> schema) {
    if (schema == null) {
      return List.of();
    }
    List<String> keys = new ArrayList<>();
    for (Map.Entry<String, Object> entry : mainProperties(schema).entrySet()) {
      if (entry.getValue() instanceof Map<?, ?> spec
          && Boolean.TRUE.equals(((Map<String, Object>) spec).get(PRIMARY_KEY_MARKER))) {
        keys.add(entry.getKey());
      }
    }
    return List.copyOf(keys);
  }

  /** The {@code properties} of the {@link #mainDefinition(Map)}, or empty. */
  public static Map<String, Object> mainProperties(Map<String, Object> schema) {
    return propertiesOf(mainDefinition(schema));
  }

  /**
   * The single source of truth for selecting a DataStructure's main definition: the root object if
   * it has {@code properties}, else the title-matching definition, else the unique definition that
   * carries {@code properties}; an empty map when none can be resolved unambiguously. Both the
   * PostGIS column derivation and the PK derivation select through this method, so they cannot pick
   * different definitions.
   */
  public static Map<String, Object> mainDefinition(Map<String, Object> schema) {
    if (schema == null) {
      return Map.of();
    }
    if (!propertiesOf(schema).isEmpty()) {
      return schema;
    }
    Map<String, Object> defs = definitions(schema);
    if (defs.get(asString(schema.get("title"))) instanceof Map<?, ?> titled
        && !propertiesOf(titled).isEmpty()) {
      return castMap(titled);
    }
    return singleDefinition(defs);
  }

  /** The {@code properties} map of a schema/definition object, or empty if absent. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> propertiesOf(Object definition) {
    if (definition instanceof Map<?, ?> map && map.get("properties") instanceof Map<?, ?> props) {
      return (Map<String, Object>) props;
    }
    return Map.of();
  }

  /**
   * Named type definitions, merging draft 2020-12 {@code $defs} over the older {@code definitions}.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> definitions(Map<String, Object> schema) {
    Map<String, Object> defs = new LinkedHashMap<>();
    if (schema.get("definitions") instanceof Map<?, ?> d) {
      defs.putAll((Map<String, Object>) d);
    }
    if (schema.get("$defs") instanceof Map<?, ?> d) {
      defs.putAll((Map<String, Object>) d);
    }
    return defs;
  }

  /** The only definition that carries {@code properties} if exactly one does, else empty. */
  private static Map<String, Object> singleDefinition(Map<String, Object> defs) {
    Map<String, Object> single = Map.of();
    int withProps = 0;
    for (Object value : defs.values()) {
      if (value instanceof Map<?, ?> def && !propertiesOf(def).isEmpty()) {
        single = castMap(def);
        withProps++;
      }
    }
    return withProps == 1 ? single : Map.of();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Map<?, ?> map) {
    return (Map<String, Object>) map;
  }

  private static String asString(Object value) {
    return value instanceof String s ? s : null;
  }
}
