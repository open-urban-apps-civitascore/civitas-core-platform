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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

  private static final String[] LOCAL_DEF_PREFIXES = {"#/$defs/", "#/definitions/"};

  private DataStructureTableMapper() {}

  /** Derived non-spatial and geometry columns for one sink table. */
  public record TableColumns(
      List<ColumnConfig> columns, List<GeometryColumnConfig> geometryColumns) {}

  /**
   * @throws IllegalArgumentException if the schema contains no usable definition with properties
   */
  public static TableColumns deriveColumns(Map<String, Object> schema, Set<String> excludedNames) {
    ResolvedDefinition resolved = resolveDefinition(schema);
    Map<String, Object> properties = resolved.properties();
    if (properties.isEmpty()) {
      throw new IllegalArgumentException(
          "dataStructure JSON Schema has no properties; cannot derive sink table columns");
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
      Boolean nullable = required.contains(name) ? false : null;

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

  /** Merged {@code properties} (column name → spec) and unioned {@code required} for one table. */
  private record ResolvedDefinition(
      LinkedHashMap<String, Object> properties, LinkedHashSet<String> required) {}

  /**
   * Resolves the table definition into merged properties/required, following {@code allOf}
   * inheritance. The root is the entry point when it carries {@code properties} or {@code allOf};
   * otherwise the single (or title-matching) named definition is selected and then merged (so a
   * named definition with its own {@code allOf} is resolved too).
   */
  private static ResolvedDefinition resolveDefinition(Map<String, Object> schema) {
    Map<String, Object> definitions = definitions(schema);

    if (!mapValue(schema.get("properties")).isEmpty() || schema.get("allOf") instanceof List<?>) {
      ResolvedDefinition merged = mergeDefinition(schema, definitions);
      if (!merged.properties().isEmpty()) {
        return merged;
      }
    }

    Map<String, Object> selected = selectDefinitionNode(schema, definitions);
    return mergeDefinition(selected, definitions);
  }

  /**
   * Selects the single (or title-matching) named definition. Unlike the root entry in {@link
   * #resolveDefinition}, this never inspects root {@code properties}; it only chooses among {@code
   * $defs}/{@code definitions}.
   */
  private static Map<String, Object> selectDefinitionNode(
      Map<String, Object> schema, Map<String, Object> definitions) {
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
                + title
                + "'; the sink table mapping requires exactly one");
  }

  /**
   * Recursively merges a definition node's {@code allOf} branches, local {@code $ref} parents, and
   * own {@code properties}/{@code required} into a single resolved definition.
   */
  private static ResolvedDefinition mergeDefinition(
      Map<String, Object> node, Map<String, Object> definitions) {
    LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
    LinkedHashSet<String> required = new LinkedHashSet<>();
    collectInto(node, definitions, new HashSet<>(), properties, required);
    return new ResolvedDefinition(properties, required);
  }

  private static void collectInto(
      Map<String, Object> node,
      Map<String, Object> definitions,
      Set<String> visitedRefs,
      LinkedHashMap<String, Object> properties,
      LinkedHashSet<String> required) {
    // 1. allOf branches first, so inherited (parent) columns precede the node's own.
    if (node.get("allOf") instanceof List<?> branches) {
      for (Object branch : branches) {
        collectInto(mapValue(branch), definitions, visitedRefs, properties, required);
      }
    }

    // 2. A node-level local $ref names a parent definition. GeoJSON refs are geometry, not parents,
    // and a property-level $ref never reaches here (it is a value inside `properties`). An
    // unresolvable parent ref would silently drop inherited columns and create a partial table, so
    // it is rejected rather than ignored.
    String ref = stringValue(node.get("$ref"));
    if (ref != null && geometryType(ref) == null) {
      String key = localDefName(ref);
      if (key == null) {
        throw new IllegalArgumentException(
            "dataStructure JSON Schema parent $ref '"
                + ref
                + "' is not a local definition reference");
      }
      if (visitedRefs.add(key)) {
        Map<String, Object> target = mapValue(definitions.get(key));
        if (target.isEmpty()) {
          throw new IllegalArgumentException(
              "dataStructure JSON Schema parent $ref '" + ref + "' resolves to no definition");
        }
        collectInto(target, definitions, visitedRefs, properties, required);
      }
    }

    // 3. The node's own properties/required. putAll lets the most specific (last-processed)
    // definition win the type while keeping each column's first-seen position.
    properties.putAll(mapValue(node.get("properties")));
    if (node.get("required") instanceof List<?> list) {
      list.forEach(value -> required.add(String.valueOf(value)));
    }
  }

  /**
   * Local definition key from {@code #/$defs/<Name>} or {@code #/definitions/<Name>}, else null.
   */
  private static String localDefName(String ref) {
    for (String prefix : LOCAL_DEF_PREFIXES) {
      if (ref.startsWith(prefix)) {
        return ref.substring(prefix.length());
      }
    }
    return null;
  }

  /**
   * Named type definitions, merging draft 2020-12 {@code $defs} with the older {@code definitions}.
   */
  private static Map<String, Object> definitions(Map<String, Object> schema) {
    Map<String, Object> merged = new LinkedHashMap<>(mapValue(schema.get("definitions")));
    merged.putAll(mapValue(schema.get("$defs")));
    return merged;
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
