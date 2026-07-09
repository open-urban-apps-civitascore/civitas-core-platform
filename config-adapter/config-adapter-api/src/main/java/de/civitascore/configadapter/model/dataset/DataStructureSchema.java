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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Engine-neutral reads over a DataStructure JSON Schema (the model the portal stores on a
 * data-structure version and ships at {@code datasinks[].dataStructure}).
 *
 * <p>This is the single source of truth for resolving a data structure's table definition — the
 * merged {@code properties}/{@code required} of the main type, following {@code allOf}/{@code $ref}
 * inheritance — and for deriving the conceptual primary key from the {@code x-core-primaryKey}
 * marker. Both the PostGIS adapter (table columns + {@code PRIMARY KEY}) and the NiFi adapter
 * (PutDatabaseRecord {@code Update Keys}) resolve through {@link #resolveDefinition(Map)}, so the
 * columns and the primary key are always derived from the same definition and cannot diverge.
 */
public final class DataStructureSchema {

  private static final Logger LOG = LoggerFactory.getLogger(DataStructureSchema.class);

  /** JSON Schema extension keyword carrying the conceptual primary key (UML {@code {id}}). */
  public static final String PRIMARY_KEY_MARKER = "x-core-primaryKey";

  /** Local definition reference prefixes ({@code #/$defs/Name} / {@code #/definitions/Name}). */
  private static final String[] LOCAL_DEF_PREFIXES = {"#/$defs/", "#/definitions/"};

  private DataStructureSchema() {}

  /** The merged {@code properties} (column name → spec) and unioned {@code required} of a table. */
  public record ResolvedDefinition(Map<String, Object> properties, Set<String> required) {
    public ResolvedDefinition {
      // Order-preserving unmodifiable views — declaration order drives column and key order, so
      // Map.copyOf (unspecified iteration order) is not an option.
      properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
      required = Collections.unmodifiableSet(new LinkedHashSet<>(required));
    }
  }

  /**
   * Resolves the table definition into merged properties/required, following {@code allOf}
   * inheritance. When the root is the data-structure wrapper — a single property whose value is a
   * local {@code $ref} into {@code $defs}/{@code definitions} — the referenced class is the table.
   * Otherwise the root is the entry point when it carries {@code properties} or {@code allOf}; else
   * the single (or title-matching) named definition is selected and then merged (so a named
   * definition with its own {@code allOf} is resolved too).
   *
   * @throws IllegalArgumentException if no usable definition can be selected unambiguously, or a
   *     parent {@code $ref} is not a resolvable local definition
   */
  public static ResolvedDefinition resolveDefinition(Map<String, Object> schema) {
    Map<String, Object> definitions = definitions(schema);

    Map<String, Object> wrapped = wrappedRootDefinition(schema, definitions);
    if (wrapped != null) {
      return mergeDefinition(wrapped, definitions);
    }

    if (!propertiesOf(schema).isEmpty() || schema.get("allOf") instanceof List<?>) {
      ResolvedDefinition merged = mergeDefinition(schema, definitions);
      if (!merged.properties().isEmpty()) {
        return merged;
      }
    }

    Map<String, Object> selected = selectDefinitionNode(schema, definitions);
    return mergeDefinition(selected, definitions);
  }

  /**
   * The class definition a data-structure wrapper root points at, or null if the root is not a
   * wrapper. The wrapper root (the data structure itself, titled after the diagram) holds exactly
   * one property whose only content is a local {@code $ref} into {@code $defs}/{@code definitions};
   * the referenced class carries the real columns. A root with its own {@code allOf} or with any
   * property that is more than a bare local {@code $ref} is a normal definition, not a wrapper.
   *
   * @throws IllegalArgumentException if the root is a wrapper but its {@code $ref} target is absent
   *     from {@code definitions}
   */
  private static Map<String, Object> wrappedRootDefinition(
      Map<String, Object> schema, Map<String, Object> definitions) {
    if (schema.get("allOf") instanceof List<?>) {
      return null;
    }
    Map<String, Object> properties = propertiesOf(schema);
    if (properties.size() != 1) {
      return null;
    }
    Map<String, Object> property = mapValue(properties.values().iterator().next());
    if (property.size() != 1) {
      return null;
    }
    String ref = stringValue(property.get("$ref"));
    String key = localDefName(ref);
    if (key == null) {
      return null;
    }
    // The root is unambiguously a wrapper (its sole property is a local $ref); a target that is
    // absent or empty is a broken schema, not a non-wrapper. Falling back to the legacy path would
    // derive a table from the unresolved wrapper, so reject it here like collectInto does.
    Map<String, Object> target = mapValue(definitions.get(key));
    if (target.isEmpty()) {
      throw new IllegalArgumentException(
          "dataStructure JSON Schema wrapper $ref '" + ref + "' resolves to no definition");
    }
    return target;
  }

  /**
   * The property names marked {@code x-core-primaryKey} in the resolved definition, in declaration
   * order; empty if the schema is null, marks none, or cannot be resolved (the column derivation
   * surfaces an unresolvable schema as an error — the primary key is best-effort here).
   *
   * <p>A marker on a non-scalar property (a {@code $ref}, or an {@code object}/{@code array} type —
   * i.e. a geometry or JSONB column downstream) cannot back a B-tree primary key, so it is dropped
   * with a warning rather than yielding a key both adapters would fail to create.
   */
  public static List<String> primaryKeyColumns(Map<String, Object> schema) {
    if (schema == null) {
      return List.of();
    }
    Map<String, Object> properties;
    try {
      properties = resolveDefinition(schema).properties();
    } catch (IllegalArgumentException unresolvable) {
      // Best-effort here (the column derivation surfaces an unresolvable schema as a hard error),
      // but log the cause: swallowing it silently can leave a table without the primary key its
      // marker intended — no dedup, no constraint — with no diagnostic trail.
      LOG.warn(
          "Could not resolve data structure schema to derive its primary key: {}",
          Encode.forJava(String.valueOf(unresolvable.getMessage())));
      return List.of();
    }
    return markerColumns(properties);
  }

  /**
   * The class definition reached by walking {@code propertyPath} from the resolved root definition.
   * Each segment names a property of the current class; an {@code array} property is followed
   * through its {@code items}, and a bare or {@code allOf}-composed local {@code $ref} is merged
   * like any parent. Unlike {@link #primaryKeyColumns(Map)} this is strict: an unknown segment, an
   * item-less array, or a target without object properties (e.g. an external geometry ref) throws —
   * the caller asked for a specific class and a silent empty result would hide a mis-shaped schema.
   *
   * @throws IllegalArgumentException if the path does not resolve to an object definition
   */
  public static ResolvedDefinition resolveDefinitionAt(
      Map<String, Object> schema, List<String> propertyPath) {
    Map<String, Object> definitions = definitions(schema);
    ResolvedDefinition current = resolveDefinition(schema);
    for (String segment : propertyPath) {
      Map<String, Object> spec = mapValue(current.properties().get(segment));
      if (spec.isEmpty()) {
        throw new IllegalArgumentException(
            "dataStructure JSON Schema has no property '" + segment + "' on the resolved class");
      }
      if ("array".equals(stringValue(spec.get("type")))) {
        spec = mapValue(spec.get("items"));
        if (spec.isEmpty()) {
          throw new IllegalArgumentException(
              "dataStructure JSON Schema array property '" + segment + "' declares no items");
        }
      }
      current = mergeDefinition(spec, definitions);
      if (current.properties().isEmpty()) {
        throw new IllegalArgumentException(
            "dataStructure JSON Schema property '"
                + segment
                + "' does not resolve to an object definition");
      }
    }
    return current;
  }

  /**
   * The properties marked {@code x-core-primaryKey} on the class at {@code propertyPath} (see
   * {@link #resolveDefinitionAt(Map, List)}), in declaration order. Strict like the resolution it
   * builds on; an empty result only means the class marks no key.
   */
  public static List<String> primaryKeyColumnsAt(
      Map<String, Object> schema, List<String> propertyPath) {
    return markerColumns(resolveDefinitionAt(schema, propertyPath).properties());
  }

  @SuppressWarnings("unchecked")
  private static List<String> markerColumns(Map<String, Object> properties) {
    List<String> keys = new ArrayList<>();
    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      if (!(entry.getValue() instanceof Map<?, ?> map)
          || !Boolean.TRUE.equals(((Map<String, Object>) map).get(PRIMARY_KEY_MARKER))) {
        continue;
      }
      Map<String, Object> spec = (Map<String, Object>) map;
      if (!isScalar(spec)) {
        LOG.warn(
            "Ignoring x-core-primaryKey on property '{}': a non-scalar (geometry/JSONB) column"
                + " cannot back a primary key",
            Encode.forJava(entry.getKey()));
        continue;
      }
      keys.add(entry.getKey());
    }
    return List.copyOf(keys);
  }

  /**
   * Whether a property spec maps to a scalar value. A {@code $ref} (a GeoJSON geometry or a nested
   * object) and an {@code object}/{@code array} type are non-scalar; everything else is scalar.
   */
  public static boolean isScalar(Map<String, Object> spec) {
    if (spec.containsKey("$ref")) {
      return false;
    }
    String type = stringValue(spec.get("type"));
    return !"object".equals(type) && !"array".equals(type);
  }

  /**
   * The sink's primary-key columns from the single "explicit wins, else marker" rule shared by the
   * PostGIS and NiFi adapters, so the PostGIS table PRIMARY KEY and the NiFi UPSERT Update Keys are
   * always identical. A <b>non-empty</b> {@code explicitPrimaryKey} is used verbatim after
   * validating that every entry is a non-blank string; an absent/empty explicit list falls back to
   * the schema's {@code x-core-primaryKey} marker (empty if the schema is null or marks none).
   *
   * @param explicitPrimaryKey the raw {@code configuration.primaryKey} value (any type; only a
   *     non-empty {@code List} counts as explicit)
   * @param schema the data structure JSON Schema, or null
   * @throws IllegalArgumentException if an explicit entry is not a non-blank string
   */
  public static List<String> resolvePrimaryKey(
      Object explicitPrimaryKey, Map<String, Object> schema) {
    if (explicitPrimaryKey instanceof List<?> explicit && !explicit.isEmpty()) {
      List<String> keys = new ArrayList<>(explicit.size());
      for (Object entry : explicit) {
        if (!(entry instanceof String key) || key.isBlank()) {
          throw new IllegalArgumentException(
              "configuration.primaryKey must contain only non-blank strings");
        }
        keys.add(key);
      }
      return List.copyOf(keys);
    }
    return primaryKeyColumns(schema);
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
    if (node.get("allOf") instanceof List<?> branches) {
      for (Object branch : branches) {
        collectInto(mapValue(branch), definitions, visitedRefs, properties, required);
      }
    }

    String ref = stringValue(node.get("$ref"));
    if (ref != null && !isExternalSchemaUri(ref)) {
      String key = localDefName(ref);
      if (key == null) {
        // A relative/file $ref (e.g. common.json#/$defs/Base) is a real inheritance intent this
        // resolver cannot follow — silently skipping it would drop the parent's columns and build
        // an
        // incomplete table, so reject it rather than fail late at write time.
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

    properties.putAll(propertiesOf(node));
    if (node.get("required") instanceof List<?> list) {
      list.forEach(value -> required.add(String.valueOf(value)));
    }
  }

  /**
   * Whether {@code ref} is an absolute external schema URI (e.g. a GeoJSON geometry schema {@code
   * https://geojson.org/schema/Point.json}). Such a parent branch carries no table columns of its
   * own and is not part of this resolver's inheritance model, so it is skipped rather than
   * followed. A local {@code #/...} or relative/file {@code $ref} is NOT external and is handled by
   * the caller.
   */
  private static boolean isExternalSchemaUri(String ref) {
    return ref.startsWith("http://") || ref.startsWith("https://");
  }

  /**
   * Local definition key from {@code #/$defs/<Name>} or {@code #/definitions/<Name>}, else null
   * (including a null or non-local ref).
   */
  private static String localDefName(String ref) {
    if (ref == null) {
      return null;
    }
    for (String prefix : LOCAL_DEF_PREFIXES) {
      if (ref.startsWith(prefix)) {
        return ref.substring(prefix.length());
      }
    }
    return null;
  }

  /**
   * Named type definitions, merging draft 2020-12 {@code $defs} over the older {@code definitions}.
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
      if (!propertiesOf(definition).isEmpty()) {
        if (match != null) {
          return null;
        }
        match = definition;
      }
    }
    return match;
  }

  /** The {@code properties} map of a schema/definition object, or empty if absent. */
  private static Map<String, Object> propertiesOf(Object definition) {
    return definition instanceof Map<?, ?> map ? mapValue(map.get("properties")) : Map.of();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapValue(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  private static String stringValue(Object value) {
    return value instanceof String s ? s : null;
  }
}
