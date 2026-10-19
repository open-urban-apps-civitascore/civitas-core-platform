/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Entity names, navigation members and the body shapes the ports build from a record. */
public final class StaEntities {

  public static final String THING = "Thing";
  public static final String LOCATION = "Location";
  public static final String DATASTREAM = "Datastream";
  public static final String SENSOR = "Sensor";
  public static final String OBSERVED_PROPERTY = "ObservedProperty";
  public static final String OBSERVATION = "Observation";

  /**
   * The navigation members of the SensorThings entities, as a closed set. A PATCH carrying one is
   * rejected by FROST, and a port that builds its own sub-request for a nested entity must not also
   * deep-insert it. The set is written out rather than derived from the capitalised name, so that a
   * free attribute named like an entity cannot silently disappear from a body.
   */
  private static final Set<String> NAVIGATION =
      Set.of(
          "Thing",
          "Things",
          "Location",
          "Locations",
          "HistoricalLocations",
          "Datastream",
          "Datastreams",
          "MultiDatastream",
          "MultiDatastreams",
          "Sensor",
          "Sensors",
          "ObservedProperty",
          "ObservedProperties",
          "Observations",
          "FeatureOfInterest",
          "FeaturesOfInterest");

  private static final String PROJECTS = "Projects";

  /**
   * The navigation members whose entities a FROST project can hold. Datastreams, Observations and
   * ObservedProperties it cannot: the Projects plugin reaches them through the Thing only.
   */
  private static final Set<String> PROJECT_MEMBERS =
      Set.of(
          "Thing",
          "Things",
          "Location",
          "Locations",
          "Sensor",
          "Sensors",
          "FeatureOfInterest",
          "FeaturesOfInterest");

  private StaEntities() {}

  /** A copy of the entity without its navigation members, ready for a PATCH or a plain create. */
  public static ObjectNode withoutNavigation(ObjectNode entity) {
    ObjectNode body = entity.deepCopy();
    body.remove(NAVIGATION);
    return body;
  }

  /** Links a single-valued navigation to an entity of the same batch, for example a Datastream. */
  public static void link(ObjectNode body, String navigation, String reference) {
    body.putObject(navigation).put("@iot.id", reference);
  }

  /** Links a collection-valued navigation to an entity of the same batch, for example a Thing. */
  public static void linkMany(ObjectNode body, String navigation, String reference) {
    ArrayNode entities = body.putArray(navigation);
    entities.addObject().put("@iot.id", reference);
  }

  /**
   * Assigns an entity to the Dataset's project. A Dataset's API serves the project, so a Location,
   * Sensor or FeatureOfInterest without the assignment is written but missing from its lists.
   *
   * <p>The member is allowed in a PATCH, unlike the navigation members above: FROST adds the
   * project to the ones the entity already has, so an update also assigns an entity written before.
   */
  public static void assignToProject(ObjectNode body, String projectId) {
    body.putArray(PROJECTS).addObject().put("@iot.id", Long.parseLong(projectId));
  }

  /**
   * Assigns every Thing, Location, Sensor and FeatureOfInterest a deep insert creates below the
   * body to the project, at any depth. The walk passes through Datastreams, Observations and
   * ObservedProperties without assigning them, because a project cannot hold them. A member that
   * names an existing entity by its identifier is a link rather than a create, and stays as it is.
   */
  public static void assignNestedToProject(ObjectNode body, String projectId) {
    body.properties()
        .forEach(
            member -> {
              if (!NAVIGATION.contains(member.getKey())) {
                return;
              }
              JsonNode value = member.getValue();
              List<JsonNode> nested = value.isArray() ? toList((ArrayNode) value) : List.of(value);
              for (JsonNode entity : nested) {
                if (!(entity instanceof ObjectNode object) || object.has("@iot.id")) {
                  continue;
                }
                if (PROJECT_MEMBERS.contains(member.getKey())) {
                  assignToProject(object, projectId);
                }
                assignNestedToProject(object, projectId);
              }
            });
  }

  /**
   * The members of a collection-valued navigation of the record, in record order, or an empty list
   * when the record has none. A single object stands for a collection of one.
   */
  public static List<ObjectNode> membersOf(JsonNode entity, String navigation, String entityName) {
    JsonNode collection = entity.get(navigation);
    if (collection == null || collection.isNull()) {
      return List.of();
    }
    List<JsonNode> members =
        collection.isArray() ? toList((ArrayNode) collection) : List.of(collection);
    List<ObjectNode> objects = new ArrayList<>(members.size());
    for (JsonNode member : members) {
      if (!(member instanceof ObjectNode object)) {
        throw new RecordRejectedException(entityName, navigation + " is not an object");
      }
      objects.add(object);
    }
    return objects;
  }

  private static List<JsonNode> toList(ArrayNode array) {
    List<JsonNode> members = new ArrayList<>(array.size());
    array.forEach(members::add);
    return members;
  }
}
