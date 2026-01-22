/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2026 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.keycloak;

/**
 * Enum representing Keycloak operations for exception wrapping and logging. Provides human-readable
 * descriptions for each resource type and action combination.
 */
public enum KeycloakOperation {
  REALM_CREATION("realm creation"),
  REALM_UPDATE("realm update"),
  REALM_DELETION("realm deletion"),

  CLIENT_CREATION("client creation"),
  CLIENT_UPDATE("client update"),
  CLIENT_DELETION("client deletion"),

  USER_CREATION("user creation"),
  USER_UPDATE("user update"),
  USER_DELETION("user deletion"),

  ROLE_CREATION("role creation"),
  ROLE_UPDATE("role update"),
  ROLE_DELETION("role deletion"),

  GROUP_CREATION("group creation"),
  GROUP_UPDATE("group update"),
  GROUP_DELETION("group deletion");

  private final String description;

  KeycloakOperation(String description) {
    this.description = description;
  }

  /**
   * Returns the human-readable description for logging and error messages.
   *
   * @return the operation description (e.g., "realm creation")
   */
  public String getDescription() {
    return description;
  }

  @Override
  public String toString() {
    return description;
  }
}
