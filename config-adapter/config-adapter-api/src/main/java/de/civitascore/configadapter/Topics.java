/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter;

import java.util.Arrays;
import java.util.Optional;

public enum Topics {
  // --- User Events ---
  USER_CREATED("de.civitascore.idm.user.created"),
  USER_UPDATED("de.civitascore.idm.user.updated"),
  USER_DELETED("de.civitascore.idm.user.deleted"),
  USER_LOCKED("de.civitascore.idm.user.locked"),
  USER_UNLOCKED("de.civitascore.idm.user.unlocked"),
  USER_PASSWORD_CHANGED("de.civitascore.idm.user.password.changed"),
  USER_PASSWORD_RESET("de.civitascore.idm.user.password.reset"),

  // --- Realm Events ---
  REALM_CREATED("de.civitascore.idm.realm.created"),
  REALM_UPDATED("de.civitascore.idm.realm.updated"),
  REALM_DELETED("de.civitascore.idm.realm.deleted"),

  // Client events
  CLIENT_CREATED("de.civitascore.idm.client.created"),
  CLIENT_UPDATED("de.civitascore.idm.client.updated"),
  CLIENT_DELETED("de.civitascore.idm.client.deleted"),

  // Group events
  GROUP_CREATED("de.civitascore.idm.group.created"),
  GROUP_UPDATED("de.civitascore.idm.group.updated"),
  GROUP_DELETED("de.civitascore.idm.group.deleted"),

  // Role events
  ROLE_CREATED("de.civitascore.idm.role.created"),
  ROLE_UPDATED("de.civitascore.idm.role.updated"),
  ROLE_DELETED("de.civitascore.idm.role.deleted"),

  // --- Backend Events ---
  BACKEND_CREATED("de.civitascore.api.backend.created"),
  BACKEND_UPDATED("de.civitascore.api.backend.updated"),
  BACKEND_DELETED("de.civitascore.api.backend.deleted"),

  // --- Route Events ---
  ROUTE_CREATED("de.civitascore.api.route.created"),
  ROUTE_UPDATED("de.civitascore.api.route.updated"),
  ROUTE_DELETED("de.civitascore.api.route.deleted"),

  // --- FROST SensorThings API Events ---
  THING_CREATED("de.civitascore.data.thing.created"),
  THING_UPDATED("de.civitascore.data.thing.updated"),
  THING_DELETED("de.civitascore.data.thing.deleted"),
  LOCATION_CREATED("de.civitascore.data.location.created"),
  LOCATION_UPDATED("de.civitascore.data.location.updated"),
  LOCATION_DELETED("de.civitascore.data.location.deleted"),
  SENSOR_CREATED("de.civitascore.data.sensor.created"),
  SENSOR_UPDATED("de.civitascore.data.sensor.updated"),
  SENSOR_DELETED("de.civitascore.data.sensor.deleted"),
  OBSERVED_PROPERTY_CREATED("de.civitascore.data.observedproperty.created"),
  OBSERVED_PROPERTY_UPDATED("de.civitascore.data.observedproperty.updated"),
  OBSERVED_PROPERTY_DELETED("de.civitascore.data.observedproperty.deleted"),
  DATASTREAM_CREATED("de.civitascore.data.datastream.created"),
  DATASTREAM_UPDATED("de.civitascore.data.datastream.updated"),
  DATASTREAM_DELETED("de.civitascore.data.datastream.deleted"),

  // --- FROST Projects Events ---
  FROST_PROJECT_CREATED("de.civitascore.data.project.created"),
  FROST_PROJECT_UPDATED("de.civitascore.data.project.updated"),
  FROST_PROJECT_DELETED("de.civitascore.data.project.deleted"),

  // --- Pipeline Events ---
  PIPELINE_CREATED("de.civitascore.data.pipeline.created"),
  PIPELINE_UPDATED("de.civitascore.data.pipeline.updated"),
  PIPELINE_DELETED("de.civitascore.data.pipeline.deleted"),

  // --- GeoServer Events ---
  GEO_WORKSPACE_CREATED("de.civitascore.geo.workspace.created"),
  GEO_WORKSPACE_UPDATED("de.civitascore.geo.workspace.updated"),
  GEO_WORKSPACE_DELETED("de.civitascore.geo.workspace.deleted"),
  GEO_DATASTORE_CREATED("de.civitascore.geo.datastore.created"),
  GEO_DATASTORE_UPDATED("de.civitascore.geo.datastore.updated"),
  GEO_DATASTORE_DELETED("de.civitascore.geo.datastore.deleted"),
  GEO_FEATURE_TYPE_CREATED("de.civitascore.geo.featuretype.created"),
  GEO_FEATURE_TYPE_UPDATED("de.civitascore.geo.featuretype.updated"),
  GEO_FEATURE_TYPE_DELETED("de.civitascore.geo.featuretype.deleted"),
  GEO_STYLE_CREATED("de.civitascore.geo.style.created"),
  GEO_STYLE_UPDATED("de.civitascore.geo.style.updated"),
  GEO_STYLE_DELETED("de.civitascore.geo.style.deleted"),
  GEO_LAYER_UPDATED("de.civitascore.geo.layer.updated"),
  GEO_LAYER_DELETED("de.civitascore.geo.layer.deleted");

  // --- PostGIS Table Events ---
  TABLE_CREATED("de.civitascore.data.table.created"),
  TABLE_UPDATED("de.civitascore.data.table.updated"),
  TABLE_DELETED("de.civitascore.data.table.deleted"),

  // --- PostGIS Schema Events ---
  SCHEMA_CREATED("de.civitascore.data.schema.created"),
  SCHEMA_UPDATED("de.civitascore.data.schema.updated"),
  SCHEMA_DELETED("de.civitascore.data.schema.deleted"),

  // --- PostGIS Role Events (database roles / users) ---
  DB_ROLE_CREATED("de.civitascore.data.role.created"),
  DB_ROLE_UPDATED("de.civitascore.data.role.updated"),
  DB_ROLE_DELETED("de.civitascore.data.role.deleted");

  private final String value;

  Topics(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static boolean isValidTopic(String topicString) {
    if (topicString == null) {
      return false;
    }
    return Arrays.stream(values()).anyMatch(t -> t.value.equalsIgnoreCase(topicString.trim()));
  }

  public static Optional<Topics> fromString(String topicString) {
    if (topicString == null) {
      return Optional.empty();
    }
    return Arrays.stream(values())
        .filter(t -> t.value.equalsIgnoreCase(topicString.trim()))
        .findFirst();
  }

  @Override
  public String toString() {
    return value;
  }

  public static final java.util.List<String> ALL_TOPICS =
      java.util.Arrays.stream(values()).map(Topics::getValue).toList();
}
