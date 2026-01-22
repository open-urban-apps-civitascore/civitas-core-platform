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

public enum ResourceType {
  ROLE,
  USER,
  CLIENT,
  REALM,
  GROUP,
  UNKNOWN; // Fallback

  public static ResourceType fromString(String value) {
    if (value == null) return UNKNOWN;
    try {
      return ResourceType.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return UNKNOWN;
    }
  }
}
