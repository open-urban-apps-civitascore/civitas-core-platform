/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter;

import java.util.Arrays;
import java.util.Optional;

public enum Topics {
  // --- User Events ---
  USER_CREATED("core.civitas.idm.user.created"),
  USER_UPDATED("core.civitas.idm.user.updated"),
  USER_DELETED("core.civitas.idm.user.deleted"),
  USER_LOCKED("core.civitas.idm.user.locked"),
  USER_UNLOCKED("core.civitas.idm.user.unlocked"),
  USER_PASSWORD_CHANGED("core.civitas.idm.user.password.changed"),
  USER_PASSWORD_RESET("core.civitas.idm.user.password.reset"),

  // --- Realm Events ---
  REALM_CREATED("core.civitas.idm.realm.created"),
  REALM_UPDATED("core.civitas.idm.realm.updated"),
  REALM_DELETED("core.civitas.idm.realm.deleted"),

  // Client events
  CLIENT_CREATED("core.civitas.idm.client.created"),
  CLIENT_UPDATED("core.civitas.idm.client.updated"),
  CLIENT_DELETED("core.civitas.idm.client.deleted"),

  // Group events
  GROUP_CREATED("core.civitas.idm.group.created"),
  GROUP_UPDATED("core.civitas.idm.group.updated"),
  GROUP_DELETED("core.civitas.idm.group.deleted"),

  // Role events
  ROLE_CREATED("core.civitas.idm.role.created"),
  ROLE_UPDATED("core.civitas.idm.role.updated"),
  ROLE_DELETED("core.civitas.idm.role.deleted"),

  // --- Backend Events ---
  BACKEND_CREATED("core.civitas.api.backend.created"),
  BACKEND_UPDATED("core.civitas.api.backend.updated"),
  BACKEND_DELETED("core.civitas.api.backend.deleted");

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
