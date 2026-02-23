/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.frost;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parser for OGC SensorThings API resource paths.
 *
 * <p>Parses resource paths according to the OGC SensorThings API specification (OGC 18-088) and
 * FROST-Server extensions. Supports:
 *
 * <ul>
 *   <li>Simple entity collection paths: {@code Things}, {@code Locations}, {@code Sensors}
 *   <li>Entity paths with ID: {@code Things/123}, {@code Locations/456}
 *   <li>Navigation paths: {@code Things/1/Locations}, {@code Datastreams/2/Sensor}
 *   <li>FROST Projects extension: {@code Projects/1/Things}
 *   <li>Deep navigation: {@code Projects/1/Things/2/Datastreams}
 * </ul>
 *
 * <p>The parser extracts the target entity type, optional entity ID, and optional parent path for
 * nested resource creation.
 *
 * @see <a href="https://docs.ogc.org/is/18-088/18-088.html">OGC SensorThings API Part 1:
 *     Sensing</a>
 */
public final class StaResourcePathParser {

  private StaResourcePathParser() {}

  /**
   * Parses a SensorThings API resource path into its components.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code "Things"} → entityType=THING, id=null, parentPath=null
   *   <li>{@code "Things/123"} → entityType=THING, id="123", parentPath=null
   *   <li>{@code "Things(123)"} → entityType=THING, id="123", parentPath=null
   *   <li>{@code "Projects/1/Things"} → entityType=THING, id=null, parentPath="Projects(1)"
   *   <li>{@code "Things/1/Locations/2"} → entityType=LOCATION, id="2", parentPath="Things(1)"
   * </ul>
   *
   * @param targetResource the resource path to parse
   * @return parsed resource information
   * @throws IllegalArgumentException if targetResource is null or empty
   */
  public static ResourceInfo parse(String targetResource) {
    validateInput(targetResource);

    String normalizedPath = normalizePath(targetResource);
    List<String> pathParts = splitIntoNonEmptyParts(normalizedPath);
    List<PathSegment> segments = extractPathSegments(pathParts);

    return buildResourceInfo(segments);
  }

  private static void validateInput(String targetResource) {
    if (targetResource == null || targetResource.isBlank()) {
      throw new IllegalArgumentException("Target resource cannot be null or empty");
    }
  }

  /**
   * Normalizes path notation for uniform parsing.
   *
   * <p>Converts parentheses notation to slash notation and collapses multiple consecutive slashes.
   */
  private static String normalizePath(String path) {
    return path //
        .replaceAll("\\('([^']+)'\\)", "/$1") // Things('id') → Things/id
        .replaceAll("\\(([^)]+)\\)", "/$1") // Things(id) → Things/id
        .replaceAll("/+", "/"); // Things//id → Things/id
  }

  private static List<String> splitIntoNonEmptyParts(String path) {
    return Arrays.stream(path.split("/")) //
        .map(String::trim) //
        .filter(p -> !p.isEmpty()) //
        .toList();
  }

  /**
   * Extracts path segments (entity type + optional ID pairs) from path parts.
   *
   * <p>Each segment consists of an entity type and optionally an ID that follows it.
   */
  private static List<PathSegment> extractPathSegments(List<String> parts) {
    List<PathSegment> segments = new ArrayList<>();
    int index = 0;

    while (index < parts.size()) {
      String currentPart = parts.get(index);
      EntityType entityType = EntityType.fromPathSegment(currentPart);

      if (entityType != null) {
        String entityId = extractIdIfPresent(parts, index + 1);
        segments.add(new PathSegment(entityType, entityId));
        index += (entityId != null) ? 2 : 1;
      } else {
        index++;
      }
    }

    return segments;
  }

  /** Extracts an ID from the given index if it exists and is not an entity type. */
  private static String extractIdIfPresent(List<String> parts, int index) {
    if (index >= parts.size()) {
      return null;
    }

    String potentialId = parts.get(index);
    boolean isEntityType = EntityType.fromPathSegment(potentialId) != null;

    return isEntityType ? null : potentialId;
  }

  /**
   * Builds ResourceInfo from parsed segments.
   *
   * <p>The last segment becomes the target entity, all preceding segments form the parent path.
   */
  private static ResourceInfo buildResourceInfo(List<PathSegment> segments) {
    if (segments.isEmpty()) {
      return new ResourceInfo(null, null, null);
    }

    PathSegment targetSegment = segments.getLast();
    String parentPath = buildParentPath(segments.subList(0, segments.size() - 1));

    return new ResourceInfo(targetSegment.entityType(), targetSegment.id(), parentPath);
  }

  private static String buildParentPath(List<PathSegment> parentSegments) {
    if (parentSegments.isEmpty()) {
      return null;
    }

    StringBuilder pathBuilder = new StringBuilder();
    for (int i = 0; i < parentSegments.size(); i++) {
      if (i > 0) {
        pathBuilder.append("/");
      }
      pathBuilder.append(formatSegmentAsApiPath(parentSegments.get(i)));
    }

    return pathBuilder.toString();
  }

  private static String formatSegmentAsApiPath(PathSegment segment) {
    String path = segment.entityType().getApiPath();
    if (segment.id() != null) {
      return path + "(" + segment.id() + ")";
    }
    return path;
  }

  /** Internal representation of a path segment (entity type + optional ID). */
  private record PathSegment(EntityType entityType, String id) {}

  /**
   * Parsed resource information containing the entity type, optional ID, and optional parent path.
   *
   * @param entityType the type of SensorThings API entity
   * @param id the entity ID (null for collection operations like CREATE)
   * @param parentPath the parent path for nested resources (e.g., "Projects(1)" for
   *     "Projects/1/Things")
   */
  public record ResourceInfo(EntityType entityType, String id, String parentPath) {

    /**
     * Checks if this resource targets a specific entity (has an ID).
     *
     * @return true if an entity ID is present
     */
    public boolean hasId() {
      return id != null && !id.isBlank();
    }

    /**
     * Builds the collection API path (without entity ID) for this resource.
     *
     * @return the collection API path (e.g., "Projects(1)/Things" or "Things"), or null if no
     *     entity type
     */
    public String collectionPath() {
      if (entityType == null) {
        return null;
      }

      if (parentPath != null) {
        return parentPath + "/" + entityType.getApiPath();
      }
      return entityType.getApiPath();
    }
  }

  /**
   * OGC SensorThings API entity types plus FROST-Server extensions.
   *
   * <p>Covers all entity types defined in OGC 18-088 (SensorThings API Part 1: Sensing) and the
   * FROST Projects extension.
   */
  public enum EntityType {
    THING("Things"),
    LOCATION("Locations"),
    HISTORICAL_LOCATION("HistoricalLocations"),
    DATASTREAM("Datastreams"),
    SENSOR("Sensors"),
    OBSERVED_PROPERTY("ObservedProperties"),
    OBSERVATION("Observations"),
    FEATURE_OF_INTEREST("FeaturesOfInterest"),
    PROJECT("Projects");

    private final String apiPath;

    EntityType(String apiPath) {
      this.apiPath = apiPath;
    }

    /**
     * Returns the API path segment for this entity type.
     *
     * @return the path segment (e.g., "Things", "Locations")
     */
    public String getApiPath() {
      return apiPath;
    }

    /**
     * Resolves an entity type from a path segment.
     *
     * <p>Accepts both singular and plural forms, case-insensitive.
     *
     * @param segment the path segment to resolve
     * @return the matching EntityType, or null if not recognized
     */
    public static EntityType fromPathSegment(String segment) {
      if (segment == null || segment.isBlank()) {
        return null;
      }
      String normalized = segment.trim().toLowerCase();
      return switch (normalized) {
        case "things", "thing" -> THING;
        case "locations", "location" -> LOCATION;
        case "historicallocations", "historicallocation" -> HISTORICAL_LOCATION;
        case "datastreams", "datastream" -> DATASTREAM;
        case "sensors", "sensor" -> SENSOR;
        case "observedproperties", "observedproperty" -> OBSERVED_PROPERTY;
        case "observations", "observation" -> OBSERVATION;
        case "featuresofinterest", "featureofinterest" -> FEATURE_OF_INTEREST;
        case "projects", "project" -> PROJECT;
        default -> null;
      };
    }
  }
}
