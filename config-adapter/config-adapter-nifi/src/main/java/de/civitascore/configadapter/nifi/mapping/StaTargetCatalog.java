/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.mapping;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The catalog of paths a FROST mapping may target, anchored at the mapped record: the target data
 * structure is a normal, tenant-modelled Thing-shaped class (one record = one Thing with its
 * Locations, Datastreams and their Observations), so the paths here are exactly what the mapping
 * editor emits against that structure. The fixed vocabulary below is a reviewable constant — every
 * generated JSON key and FROST path of an entity body comes from here — with one schema-derived
 * extension: each entity's match key (its {@code x-core-primaryKey} attribute, fallback {@code
 * reference}) contributes {@code $.<key>} / {@code $.Datastreams[].<key>} paths whose names pass
 * {@link #isSafeKeyName(String)} before they may appear in a {@code $filter} or template.
 */
public final class StaTargetCatalog {

  /**
   * The shape a schema-derived key name must have before it is interpolated into an OData {@code
   * $filter} or a generated JSON template. Everything else is rejected at plan time — a key name is
   * tenant input, unlike the fixed vocabulary.
   */
  private static final Pattern SAFE_KEY_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

  private static final List<StaTarget> TARGETS =
      List.of(
          new StaTarget("$.name", StaEntity.THING, StaJsonType.STRING, TargetKind.CREATE),
          new StaTarget("$.description", StaEntity.THING, StaJsonType.STRING, TargetKind.CREATE),
          new StaTarget(
              "$.Locations[].name", StaEntity.LOCATION, StaJsonType.STRING, TargetKind.CREATE),
          new StaTarget(
              "$.Locations[].description",
              StaEntity.LOCATION,
              StaJsonType.STRING,
              TargetKind.CREATE),
          new StaTarget(
              "$.Locations[].encodingType",
              StaEntity.LOCATION,
              StaJsonType.STRING,
              TargetKind.CREATE),
          new StaTarget(
              "$.Locations[].location",
              StaEntity.LOCATION,
              StaJsonType.RAW_JSON,
              TargetKind.CREATE),
          new StaTarget(
              "$.Datastreams[].name", StaEntity.DATASTREAM, StaJsonType.STRING, TargetKind.CREATE),
          new StaTarget(
              "$.Datastreams[].description",
              StaEntity.DATASTREAM,
              StaJsonType.STRING,
              TargetKind.CREATE),
          new StaTarget(
              "$.Datastreams[].observationType",
              StaEntity.DATASTREAM,
              StaJsonType.STRING,
              TargetKind.CREATE),
          new StaTarget(
              "$.Datastreams[].unitOfMeasurement.name",
              StaEntity.DATASTREAM,
              StaJsonType.STRING,
              TargetKind.CREATE),
          new StaTarget(
              "$.Datastreams[].unitOfMeasurement.symbol",
              StaEntity.DATASTREAM,
              StaJsonType.STRING,
              TargetKind.CREATE),
          new StaTarget(
              "$.Datastreams[].unitOfMeasurement.definition",
              StaEntity.DATASTREAM,
              StaJsonType.STRING,
              TargetKind.CREATE),
          new StaTarget(
              "$.Datastreams[].Sensor.name",
              StaEntity.DATASTREAM,
              StaJsonType.STRING,
              TargetKind.CREATE),
          new StaTarget(
              "$.Datastreams[].Sensor.description",
              StaEntity.DATASTREAM,
              StaJsonType.STRING,
              TargetKind.CREATE),
          new StaTarget(
              "$.Datastreams[].Sensor.encodingType",
              StaEntity.DATASTREAM,
              StaJsonType.STRING,
              TargetKind.CREATE),
          new StaTarget(
              "$.Datastreams[].Sensor.metadata",
              StaEntity.DATASTREAM,
              StaJsonType.STRING,
              TargetKind.CREATE),
          new StaTarget(
              "$.Datastreams[].ObservedProperty.name",
              StaEntity.DATASTREAM,
              StaJsonType.STRING,
              TargetKind.CREATE),
          new StaTarget(
              "$.Datastreams[].ObservedProperty.definition",
              StaEntity.DATASTREAM,
              StaJsonType.STRING,
              TargetKind.CREATE),
          new StaTarget(
              "$.Datastreams[].ObservedProperty.description",
              StaEntity.DATASTREAM,
              StaJsonType.STRING,
              TargetKind.CREATE),
          new StaTarget(
              "$.Datastreams[].Observations[].result",
              StaEntity.OBSERVATION,
              StaJsonType.ANY,
              TargetKind.CREATE),
          new StaTarget(
              "$.Datastreams[].Observations[].phenomenonTime",
              StaEntity.OBSERVATION,
              StaJsonType.STRING,
              TargetKind.OPTIONAL),
          new StaTarget(
              "$.Datastreams[].Observations[].resultTime",
              StaEntity.OBSERVATION,
              StaJsonType.STRING,
              TargetKind.OPTIONAL));

  /**
   * The entities of the Thing-shaped record. Declaration order is the find-or-create order of the
   * deployed chain and the byte-deterministic template order.
   */
  public enum StaEntity {
    /** The record root — the Thing itself. Its paths anchor directly at {@code $}. */
    THING("$"),
    /** Nested locations, created only via the Thing's deep insert. */
    LOCATION("$.Locations[]"),
    /** Nested datastreams (with Sensor/ObservedProperty/unitOfMeasurement deep-inserted). */
    DATASTREAM("$.Datastreams[]"),
    /** Observations of the record's datastream; always appended, never deduplicated. */
    OBSERVATION("$.Datastreams[].Observations[]");

    private final String pathPrefix;

    StaEntity(String pathPrefix) {
      this.pathPrefix = pathPrefix;
    }

    /** The record path this entity's fields live under ({@code $} for the Thing itself). */
    public String pathPrefix() {
      return pathPrefix;
    }

    /** The full record path of a field of this entity. */
    public String pathOf(String fieldName) {
      return "$".equals(pathPrefix) ? "$." + fieldName : pathPrefix + "." + fieldName;
    }
  }

  /** The JSON type a target serializes as — drives the template placeholder form. */
  public enum StaJsonType {
    /** Always a JSON string (quoted, escaped). */
    STRING,
    /**
     * SensorThings {@code any}: the JSON type is inferred from the mapped value expression
     * (number/boolean render unquoted, everything else as a string).
     */
    ANY,
    /** Embedded verbatim (a GeoJSON object produced by a geometry transform). */
    RAW_JSON
  }

  /**
   * How a target participates in the entity's rules: {@code CREATE} paths are the all-or-nothing
   * create set, {@code KEY} marks a schema-derived match-key path (always required once the entity
   * is touched), {@code OPTIONAL} paths never gate anything.
   */
  public enum TargetKind {
    CREATE,
    KEY,
    OPTIONAL
  }

  /**
   * One fixed targetable record path.
   *
   * @param path the target path exactly as the mapping editor emits it
   * @param entity the entity the path belongs to
   * @param type the JSON type of the serialized value
   * @param kind how the path participates in the entity's rules (fixed catalog entries are {@code
   *     CREATE} or {@code OPTIONAL}; {@code KEY} targets are synthesized from the schema)
   */
  public record StaTarget(String path, StaEntity entity, StaJsonType type, TargetKind kind) {

    public StaTarget {
      // A path outside its entity's prefix would silently render under the wrong body anchor.
      if (!path.startsWith(prefixOf(entity))) {
        throw new IllegalArgumentException(
            "target path '" + path + "' does not start with the " + entity + " prefix");
      }
    }

    /** The path segments below the entity prefix (e.g. {@code unitOfMeasurement.name}). */
    public String relativePath() {
      return path.substring(prefixOf(entity).length());
    }

    private static String prefixOf(StaEntity entity) {
      return "$".equals(entity.pathPrefix()) ? "$." : entity.pathPrefix() + ".";
    }
  }

  static {
    // KEY targets exist only synthesized from a schema — a fixed KEY entry would claim a
    // reviewable constant is tenant-derived.
    for (StaTarget target : TARGETS) {
      if (target.kind() == TargetKind.KEY) {
        throw new IllegalStateException("the fixed catalog must not contain KEY targets");
      }
    }
  }

  private StaTargetCatalog() {}

  /** All fixed targetable paths in template key order. */
  public static List<StaTarget> targets() {
    return TARGETS;
  }

  /** The fixed catalog entry for a target path, or empty if the path is not a fixed target. */
  public static Optional<StaTarget> byPath(String path) {
    return TARGETS.stream().filter(target -> target.path().equals(path)).findFirst();
  }

  /** The fixed paths of one entity, in template key order. */
  public static List<StaTarget> targetsOf(StaEntity entity) {
    return TARGETS.stream().filter(target -> target.entity() == entity).toList();
  }

  /**
   * The record path of an entity's schema-derived match-key attribute ({@code $.<key>} for the
   * Thing, {@code $.Datastreams[].<key>} for the datastream).
   */
  public static String keyPath(StaEntity entity, String keyName) {
    return entity.pathOf(keyName);
  }

  /**
   * Whether a schema-derived key name is safe to interpolate into a {@code $filter} expression and
   * a generated JSON template.
   */
  public static boolean isSafeKeyName(String keyName) {
    return keyName != null && SAFE_KEY_NAME.matcher(keyName).matches();
  }

  /** The supported fixed paths as a comma-separated list (for rejection messages). */
  public static String supportedPaths() {
    return TARGETS.stream().map(StaTarget::path).collect(Collectors.joining(", "));
  }
}
