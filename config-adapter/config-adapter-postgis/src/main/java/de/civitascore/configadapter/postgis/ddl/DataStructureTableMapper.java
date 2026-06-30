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

import de.civitascore.configadapter.model.dataset.DataStructureSchema;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives the table columns for a PostGIS sink from the JSON Schema the portal-backend stores on
 * the data-structure version and ships at {@code datasinks[].dataStructure}.
 *
 * <p>Properties come from the root {@code properties} or the schema's {@code $defs}/{@code
 * definitions}; {@code required} properties become {@code NOT NULL}. A property whose {@code $ref}
 * points at a GeoJSON geometry schema ({@code https://geojson.org/schema/<Type>.json}) becomes a
 * geometry column; its SRID is read from an optional {@code crs} on the property (e.g. {@code
 * EPSG:25832}), defaulting to 4326 (the GeoServer handler's CRS default). Any other {@code $ref} is
 * a nested object and maps to {@code JSONB}.
 *
 * <p>Names in {@code excludedNames} (explicitly configured geometry columns) are skipped so they
 * are not duplicated as derived columns.
 *
 * <p>A subclass's own properties live outside the root (under {@code allOf}), so columns are
 * gathered by resolving {@code allOf} branches and their local {@code $ref} parents, not just the
 * root {@code properties}. Inherited columns come first, and on a name collision the subclass wins
 * the column type while the column keeps its first-seen position. A property-level {@code $ref}
 * stays {@code JSONB}; only node-level parents are followed.
 */
public final class DataStructureTableMapper {

  private static final int DEFAULT_SRID = 4326;

  /** Geometry properties reference a GeoJSON schema under this host; the type is the file name. */
  private static final String GEOJSON_SCHEMA_MARKER = "geojson.org/schema/";

  /** Extracts the numeric SRID from a CRS identifier such as {@code EPSG:25832}. */
  private static final Pattern EPSG_CODE = Pattern.compile("(?i)EPSG:+\\s*(\\d+)");

  private DataStructureTableMapper() {}

  /** Derived non-spatial and geometry columns for one sink table. */
  public record TableColumns(
      List<ColumnConfig> columns, List<GeometryColumnConfig> geometryColumns) {}

  /**
   * @throws IllegalArgumentException if the schema contains no usable definition with properties
   */
  public static TableColumns deriveColumns(Map<String, Object> schema, Set<String> excludedNames) {
    // Resolve the table definition through the shared DataStructureSchema (allOf-aware) so the
    // columns and the primary key are derived from the SAME merged definition — no divergence.
    DataStructureSchema.ResolvedDefinition resolved = DataStructureSchema.resolveDefinition(schema);
    Map<String, Object> properties = resolved.properties();
    if (properties.isEmpty()) {
      throw new IllegalArgumentException(
          "dataStructure JSON Schema has no usable definition with properties; cannot derive sink"
              + " table columns");
    }
    List<String> required = List.copyOf(resolved.required());

    List<ColumnConfig> columns = new ArrayList<>();
    List<GeometryColumnConfig> geometryColumns = new ArrayList<>();
    for (Map.Entry<String, Object> property : properties.entrySet()) {
      String name = property.getKey();
      if (excludedNames.contains(name)) {
        continue;
      }
      Map<String, Object> spec = mapValue(property.getValue());
      // A primary-key column must be NOT NULL even if the schema's `required` list omits it; the
      // x-core-primaryKey marker therefore also forces non-nullability here. The PK column LIST
      // itself is derived centrally by DataStructureSchema (used by both the PostGIS and NiFi
      // adapters) so the table PRIMARY KEY and the UPSERT keys cannot diverge.
      boolean isPrimaryKey = Boolean.TRUE.equals(spec.get(DataStructureSchema.PRIMARY_KEY_MARKER));
      Boolean nullable = (required.contains(name) || isPrimaryKey) ? false : null;

      GeometryType geometryType = geometryType(stringValue(spec.get("$ref")));
      if (geometryType != null) {
        Integer srid = sridFromCrs(spec.get("crs"));
        geometryColumns.add(
            new GeometryColumnConfig(
                name, geometryType, srid != null ? srid : DEFAULT_SRID, null, nullable));
        continue;
      }
      ColumnType type = columnType(spec);
      columns.add(new ColumnConfig(name, type, null, null, null, nullable));
    }
    return new TableColumns(columns, geometryColumns);
  }

  /**
   * Geometry type when the ref points at a GeoJSON geometry schema ({@code
   * https://geojson.org/schema/<Type>.json}), else null. Only GeoJSON refs are geometry; a local
   * {@code $ref} to a named type (e.g. {@code #/$defs/Point}) stays a nested object so a class
   * merely named "Point" is not misread as a geometry column.
   */
  private static GeometryType geometryType(String ref) {
    if (ref == null) {
      return null;
    }
    int marker = ref.indexOf(GEOJSON_SCHEMA_MARKER);
    if (marker < 0) {
      return null;
    }
    String name = ref.substring(marker + GEOJSON_SCHEMA_MARKER.length());
    if (name.endsWith(".json")) {
      name = name.substring(0, name.length() - ".json".length());
    }
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

  /**
   * SRID from a CRS identifier like {@code EPSG:25832} (or a {@code urn:…:EPSG::25832} form), else
   * null.
   */
  private static Integer sridFromCrs(Object crs) {
    if (!(crs instanceof String s)) {
      return null;
    }
    Matcher matcher = EPSG_CODE.matcher(s);
    return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
  }
}
