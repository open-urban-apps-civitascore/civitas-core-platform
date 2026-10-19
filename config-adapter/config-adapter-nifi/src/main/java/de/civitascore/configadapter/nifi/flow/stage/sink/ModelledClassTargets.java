/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.sink;

import de.civitascore.configadapter.model.dataset.DataStructureSchema;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.StaEntity;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.StaJsonType;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.StaTarget;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.TargetKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The targets of the classes a FROST target structure models under the free fields of SensorThings.
 *
 * <p>SensorThings leaves {@code properties}, {@code feature} and {@code resultQuality} free. The
 * platform has no type for a free object, so a structure that needs one of these fields models it
 * as a class, and each attribute of that class becomes a target under the field. A class inside a
 * class is followed, so the body carries the same nesting as the structure. A geometry is a target
 * of its own; an array is embedded as it comes.
 */
final class ModelledClassTargets {

  private static final String LOCAL_REFERENCE = "#/";

  private final Map<String, Object> schema;
  private final List<StaTarget> targets = new ArrayList<>();

  ModelledClassTargets(Map<String, Object> schema) {
    this.schema = schema;
  }

  /** The targets collected so far, in the order the structure declares them. */
  List<StaTarget> targets() {
    return List.copyOf(targets);
  }

  /**
   * Adds the targets of a free field. A field the structure does not declare adds nothing, and so
   * does one it declares as a plain value or a free object: neither has attributes to target.
   *
   * @param classPath the property names from the record root to the class that holds the field
   * @param field the field's name in that class
   * @param entity the entity the field belongs to
   * @param path the field's record path, in the vocabulary of the catalog
   * @throws IllegalArgumentException if the class is structurally broken or an attribute name is
   *     not a safe identifier
   */
  void field(List<String> classPath, String field, StaEntity entity, String path) {
    Map<String, Object> spec = specOf(classPath, field);
    if (spec == null) {
      return;
    }
    if (DataStructureSchema.isGeometry(spec)) {
      add(path, entity, StaJsonType.RAW_JSON);
    } else if (isClassAt(classPath, field)) {
      expand(append(classPath, field), entity, path, new HashSet<>(Set.of(referenceOf(spec))));
    }
  }

  /**
   * Whether a field holds a class: a local reference, or an inline object whose own {@code
   * properties} or {@code allOf} branches declare attributes. The scalar check alone does not tell:
   * an inline class needs no {@code type: object}, and an {@code allOf} of plain values declares no
   * attribute at all.
   */
  boolean isClassAt(List<String> classPath, String field) {
    Map<String, Object> spec = specOf(classPath, field);
    if (spec == null || DataStructureSchema.isGeometry(spec)) {
      return false;
    }
    if (referenceOf(spec).startsWith(LOCAL_REFERENCE)) {
      return true;
    }
    if (spec.get("properties") instanceof Map<?, ?> attributes && !attributes.isEmpty()) {
      return true;
    }
    if (!(spec.get("allOf") instanceof List<?>)) {
      return false;
    }
    try {
      return !DataStructureSchema.resolveDefinitionAt(schema, append(classPath, field))
          .properties()
          .isEmpty();
    } catch (IllegalArgumentException noAttributes) {
      return false;
    }
  }

  private void expand(List<String> classPath, StaEntity entity, String path, Set<String> open) {
    Map<String, Object> attributes =
        DataStructureSchema.resolveDefinitionAt(schema, classPath).properties();
    for (String name : attributes.keySet()) {
      if (!StaTargetCatalog.isSafeKeyName(name)) {
        // The name becomes a key of the generated body template.
        throw new IllegalArgumentException("unsafe attribute name in a modelled class: " + name);
      }
      Map<String, Object> spec = specOf(classPath, name);
      String attributePath = path + "." + name;
      if (DataStructureSchema.isGeometry(spec) || "array".equals(spec.get("type"))) {
        add(attributePath, entity, StaJsonType.RAW_JSON);
      } else if (isClassAt(classPath, name)) {
        String reference = referenceOf(spec);
        // A class already on the path is not followed again: a cycle would answer endless paths,
        // and the editor stops there too.
        if (reference.isEmpty() || !open.contains(reference)) {
          Set<String> nested = new HashSet<>(open);
          nested.add(reference);
          expand(append(classPath, name), entity, attributePath, nested);
        }
      } else {
        // A scalar, or a free object the editor shows as text.
        add(attributePath, entity, StaJsonType.ANY);
      }
    }
  }

  private void add(String path, StaEntity entity, StaJsonType type) {
    targets.add(new StaTarget(path, entity, type, TargetKind.OPTIONAL));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> specOf(List<String> classPath, String field) {
    Object spec =
        DataStructureSchema.resolveDefinitionAt(schema, classPath).properties().get(field);
    return spec instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
  }

  private static String referenceOf(Map<String, Object> spec) {
    return spec.get("$ref") instanceof String reference ? reference : "";
  }

  private static List<String> append(List<String> path, String name) {
    List<String> appended = new ArrayList<>(path);
    appended.add(name);
    return appended;
  }
}
