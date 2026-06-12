/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.postgis.ddl;

import de.civitascore.configadapter.model.postgis.ColumnConfig;
import de.civitascore.configadapter.model.postgis.ColumnType;
import de.civitascore.configadapter.model.postgis.GeometryColumnConfig;
import de.civitascore.configadapter.model.postgis.GeometryType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Derives the table columns for a PostGIS sink from the JSON Schema the portal-backend resolves
 * from Model Atlas and ships at {@code datasinks[].dataStructure}.
 *
 * <p>Properties come from the root {@code properties} or the schema's {@code definitions} entry;
 * {@code required} properties become {@code NOT NULL}. A property referencing a geometry definition
 * ({@code $ref} fragment ending in a PostGIS geometry type name, e.g. {@code …/Point}) becomes a
 * geometry column with SRID 4326 — the schema cannot carry an SRID, and 4326 matches the GeoServer
 * handler's CRS default. Other {@code $ref}s are nested objects and map to {@code JSONB}.
 *
 * <p>Names in {@code excludedNames} (explicitly configured geometry columns) are skipped so they
 * are not duplicated as derived columns.
 */
public final class DataStructureTableMapper {

  private static final int DEFAULT_SRID = 4326;

  private DataStructureTableMapper() {}

  /** Derived non-spatial and geometry columns for one sink table. */
  public record TableColumns(
      List<ColumnConfig> columns, List<GeometryColumnConfig> geometryColumns) {}

  /**
   * @throws IllegalArgumentException if the schema contains no usable definition with properties
   */
  public static TableColumns deriveColumns(Map<String, Object> schema, Set<String> excludedNames) {
    Map<String, Object> definition = selectDefinition(schema);
    Map<String, Object> properties = mapValue(definition.get("properties"));
    if (properties.isEmpty()) {
      throw new IllegalArgumentException(
          "dataStructure JSON Schema has no properties; cannot derive sink table columns");
    }
    List<String> required =
        definition.get("required") instanceof List<?> list
            ? list.stream().map(String::valueOf).toList()
            : List.of();

    List<ColumnConfig> columns = new ArrayList<>();
    List<GeometryColumnConfig> geometryColumns = new ArrayList<>();
    for (Map.Entry<String, Object> property : properties.entrySet()) {
      String name = property.getKey();
      if (excludedNames.contains(name)) {
        continue;
      }
      Map<String, Object> spec = mapValue(property.getValue());
      Boolean nullable = required.contains(name) ? false : null;

      GeometryType geometryType = geometryType(stringValue(spec.get("$ref")));
      if (geometryType != null) {
        geometryColumns.add(
            new GeometryColumnConfig(name, geometryType, DEFAULT_SRID, null, nullable));
        continue;
      }
      ColumnType type = columnType(spec);
      columns.add(new ColumnConfig(name, type, null, null, null, nullable));
    }
    return new TableColumns(columns, geometryColumns);
  }

  /** Root {@code properties} if present, else the single (or title-matching) definition. */
  private static Map<String, Object> selectDefinition(Map<String, Object> schema) {
    if (!mapValue(schema.get("properties")).isEmpty()) {
      return schema;
    }
    Map<String, Object> definitions = mapValue(schema.get("definitions"));
    if (definitions.size() == 1) {
      return mapValue(definitions.values().iterator().next());
    }
    String title = stringValue(schema.get("title"));
    if (title != null && definitions.get(title) instanceof Map) {
      return mapValue(definitions.get(title));
    }
    Map<String, Object> single = singlePropertyDefinition(definitions);
    if (single != null) {
      return single;
    }
    throw new IllegalArgumentException(
        definitions.isEmpty()
            ? "dataStructure JSON Schema has empty definitions; cannot derive sink table columns"
            : "dataStructure JSON Schema has "
                + definitions.size()
                + " definitions and none matches the title '"
                + stringValue(schema.get("title"))
                + "'; the sink table mapping requires exactly one");
  }

  /**
   * The only definition that carries properties, if exactly one does. Referenced type definitions
   * (e.g. an inlined geometry class) have none and do not count as table candidates.
   */
  private static Map<String, Object> singlePropertyDefinition(Map<String, Object> definitions) {
    Map<String, Object> match = null;
    for (Object value : definitions.values()) {
      Map<String, Object> definition = mapValue(value);
      if (!mapValue(definition.get("properties")).isEmpty()) {
        if (match != null) {
          return null;
        }
        match = definition;
      }
    }
    return match;
  }

  /** Geometry type when the ref fragment ends in a PostGIS geometry type name, else null. */
  private static GeometryType geometryType(String ref) {
    if (ref == null) {
      return null;
    }
    String name = ref.substring(ref.lastIndexOf('/') + 1);
    for (GeometryType type : GeometryType.values()) {
      if (type.name().equals(name.toUpperCase(Locale.ROOT))) {
        return type;
      }
    }
    return null;
  }

  private static ColumnType columnType(Map<String, Object> spec) {
    if (spec.containsKey("$ref")) {
      return ColumnType.JSONB; // nested object reference
    }
    String type = stringValue(spec.get("type"));
    String format = stringValue(spec.get("format"));
    if ("string".equals(type) && format != null) {
      switch (format) {
        case "date-time":
          return ColumnType.TIMESTAMPTZ;
        case "date":
          return ColumnType.DATE;
        case "time":
          return ColumnType.TIME;
        case "uuid":
          return ColumnType.UUID;
        default:
          // fall through to the base type mapping
      }
    }
    return switch (type == null ? "" : type) {
      case "integer" -> ColumnType.BIGINT;
      case "number" -> ColumnType.DOUBLE_PRECISION;
      case "boolean" -> ColumnType.BOOLEAN;
      case "object", "array" -> ColumnType.JSONB;
      default -> ColumnType.TEXT;
    };
  }

  private static Map<String, Object> mapValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> cast = (Map<String, Object>) map;
      return cast;
    }
    return new LinkedHashMap<>();
  }

  private static String stringValue(Object value) {
    return value instanceof String s ? s : null;
  }
}
