/**
 * Copyright (c) 2012 - 2025 Data In Motion and others. All rights reserved.
 *
 * <p>This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * <p>SPDX-License-Identifier: EPL-2.0
 *
 * <p>Contributors: Data In Motion - initial API and implementation
 */
package com.civitas.configadapter.model;

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

    // --- Client events ---
    CLIENT_CREATED("core.civitas.idm.client.created"),
    CLIENT_UPDATED("core.civitas.idm.client.updated"),
    CLIENT_DELETED("core.civitas.idm.client.deleted"),

    // --- Group events ---
    GROUP_CREATED("core.civitas.idm.group.created"),
    GROUP_UPDATED("core.civitas.idm.group.updated"),
    GROUP_DELETED("core.civitas.idm.group.deleted"),

    // --- Role events ---
    ROLE_CREATED("core.civitas.idm.role.created"),
    ROLE_UPDATED("core.civitas.idm.role.updated"),
    ROLE_DELETED("core.civitas.idm.role.deleted");

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

// public final class Topics {
//
//    private Topics() {
//        // Utility class, prevent instantiation
//    }
//
//    // User events
//    public static final String USER_CREATED = "core.civitas.idm.user.created";
//    public static final String USER_UPDATED = "core.civitas.idm.user.updated";
//    public static final String USER_DELETED = "core.civitas.idm.user.deleted";
//    public static final String USER_LOCKED = "core.civitas.idm.user.locked";
//    public static final String USER_UNLOCKED = "core.civitas.idm.user.unlocked";
//    public static final String USER_PASSWORD_CHANGED = "core.civitas.idm.user.password.changed";
//    public static final String USER_PASSWORD_RESET = "core.civitas.idm.user.password.reset";
//
//    // Realm events
//    public static final String REALM_CREATED = "core.civitas.idm.realm.created";
//    public static final String REALM_UPDATED = "core.civitas.idm.realm.updated";
//    public static final String REALM_DELETED = "core.civitas.idm.realm.deleted";
//
//    // Client events
//    public static final String CLIENT_CREATED = "core.civitas.idm.client.created";
//    public static final String CLIENT_UPDATED = "core.civitas.idm.client.updated";
//    public static final String CLIENT_DELETED = "core.civitas.idm.client.deleted";
//
//    // Group events
//    public static final String GROUP_CREATED = "core.civitas.idm.group.created";
//    public static final String GROUP_UPDATED = "core.civitas.idm.group.updated";
//    public static final String GROUP_DELETED = "core.civitas.idm.group.deleted";
//
//    // Role events
//    public static final String ROLE_CREATED = "core.civitas.idm.role.created";
//    public static final String ROLE_UPDATED = "core.civitas.idm.role.updated";
//    public static final String ROLE_DELETED = "core.civitas.idm.role.deleted";
//
//    /**
//     * Immutable list of all available topics.
//     * Used for validation of configured topics.
//     */
//    public static final List<String> ALL_TOPICS = List.of(
//        // User events
//        USER_CREATED,
//        USER_UPDATED,
//        USER_DELETED,
//        USER_LOCKED,
//        USER_UNLOCKED,
//        USER_PASSWORD_CHANGED,
//        USER_PASSWORD_RESET,
//        // Realm events
//        REALM_CREATED,
//        REALM_UPDATED,
//        REALM_DELETED,
//        // Client events
//        CLIENT_CREATED,
//        CLIENT_UPDATED,
//        CLIENT_DELETED,
//        // Group events
//        GROUP_CREATED,
//        GROUP_UPDATED,
//        GROUP_DELETED,
//        // Role events
//        ROLE_CREATED,
//        ROLE_UPDATED,
//        ROLE_DELETED
//    );
//
//    /**
//     * Validates if a topic is a known topic.
//     *
//     * @param topic the topic to validate
//     * @return true if the topic is valid, false otherwise
//     */
//    public static boolean isValidTopic(String topic) {
//        return ALL_TOPICS.contains(topic);
//    }
// }
