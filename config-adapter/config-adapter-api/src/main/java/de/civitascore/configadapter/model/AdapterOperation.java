/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model;

/**
 * Enum for adapter operation names to avoid magic strings in exception messages. Each operation has
 * a human-readable description for use in log messages and error reporting.
 *
 * <p>Categories:
 *
 * <ul>
 *   <li>Keycloak operations (realm, user, group, client, role)
 *   <li>APISIX operations (upstream, route)
 *   <li>FROST operations (entity CRUD)
 *   <li>DummyLog operations (event processing)
 * </ul>
 */
public enum AdapterOperation {

  // Keycloak operations
  REALM_CREATE("realm creation"),
  REALM_UPDATE("realm update"),
  REALM_DELETE("realm deletion"),
  USER_CREATE("user creation"),
  USER_UPDATE("user update"),
  USER_DELETE("user deletion"),
  GROUP_CREATE("group creation"),
  GROUP_UPDATE("group update"),
  GROUP_DELETE("group deletion"),
  CLIENT_CREATE("client creation"),
  CLIENT_UPDATE("client update"),
  CLIENT_DELETE("client deletion"),
  ROLE_CREATE("role creation"),
  ROLE_UPDATE("role update"),
  ROLE_DELETE("role deletion"),

  // APISIX operations
  UPSTREAM_CREATE("upstream creation"),
  UPSTREAM_UPDATE("upstream update"),
  UPSTREAM_DELETE("upstream deletion"),
  ROUTE_CREATE("route creation"),
  ROUTE_UPDATE("route update"),
  ROUTE_DELETE("route deletion"),

  // FROST operations
  FROST_ENTITY_CREATE("FROST entity creation"),
  FROST_ENTITY_UPDATE("FROST entity update"),
  FROST_ENTITY_DELETE("FROST entity deletion"),

  // RedPanda Connect operations
  PIPELINE_CREATE("pipeline creation"),
  PIPELINE_UPDATE("pipeline update"),
  PIPELINE_DELETE("pipeline deletion"),

  // GeoServer operations
  GEOSERVER_RESOURCE_CREATE("GeoServer resource creation"),
  GEOSERVER_RESOURCE_UPDATE("GeoServer resource update"),
  GEOSERVER_RESOURCE_DELETE("GeoServer resource deletion"),

  // PostGIS / SQL operations
  TABLE_CREATE("table creation"),
  TABLE_UPDATE("table update"),
  TABLE_DELETE("table deletion"),

  // DummyLog operations
  EVENT_PROCESSING("event processing");

  private final String description;

  AdapterOperation(String description) {
    this.description = description;
  }

  /**
   * Returns the human-readable description of this operation.
   *
   * @return the operation description
   */
  public String getDescription() {
    return description;
  }
}
