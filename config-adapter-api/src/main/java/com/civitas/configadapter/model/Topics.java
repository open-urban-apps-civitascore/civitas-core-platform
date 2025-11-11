package com.civitas.configadapter.model;

import java.util.List;

public final class Topics {

    private Topics() {
        // Utility class, prevent instantiation
    }

    // User events   
    public static final String USER_CREATED = "core.civitas.idm.user.created";
    public static final String USER_UPDATED = "core.civitas.idm.user.updated";
    public static final String USER_DELETED = "core.civitas.idm.user.deleted";
    public static final String USER_LOCKED = "core.civitas.idm.user.locked";
    public static final String USER_UNLOCKED = "core.civitas.idm.user.unlocked";
    public static final String USER_PASSWORD_CHANGED = "core.civitas.idm.user.password.changed";
    public static final String USER_PASSWORD_RESET = "core.civitas.idm.user.password.reset";

    // Realm events
    public static final String REALM_CREATED = "core.civitas.idm.realm.created";
    public static final String REALM_UPDATED = "core.civitas.idm.realm.updated";
    public static final String REALM_DELETED = "core.civitas.idm.realm.deleted";

    // Client events
    public static final String CLIENT_CREATED = "core.civitas.idm.client.created";
    public static final String CLIENT_UPDATED = "core.civitas.idm.client.updated";
    public static final String CLIENT_DELETED = "core.civitas.idm.client.deleted";

    // Group events
    public static final String GROUP_CREATED = "core.civitas.idm.group.created";
    public static final String GROUP_UPDATED = "core.civitas.idm.group.updated";
    public static final String GROUP_DELETED = "core.civitas.idm.group.deleted";

    // Role events
    public static final String ROLE_CREATED = "core.civitas.idm.role.created";
    public static final String ROLE_UPDATED = "core.civitas.idm.role.updated";
    public static final String ROLE_DELETED = "core.civitas.idm.role.deleted";

    /**
     * Immutable list of all available topics.
     * Used for validation of configured topics.
     */
    public static final List<String> ALL_TOPICS = List.of(
        // User events
        USER_CREATED,
        USER_UPDATED,
        USER_DELETED,
        USER_LOCKED,
        USER_UNLOCKED,
        USER_PASSWORD_CHANGED,
        USER_PASSWORD_RESET,
        // Realm events
        REALM_CREATED,
        REALM_UPDATED,
        REALM_DELETED,
        // Client events
        CLIENT_CREATED,
        CLIENT_UPDATED,
        CLIENT_DELETED,
        // Group events
        GROUP_CREATED,
        GROUP_UPDATED,
        GROUP_DELETED,
        // Role events
        ROLE_CREATED,
        ROLE_UPDATED,
        ROLE_DELETED
    );

    /**
     * Validates if a topic is a known topic.
     *
     * @param topic the topic to validate
     * @return true if the topic is valid, false otherwise
     */
    public static boolean isValidTopic(String topic) {
        return ALL_TOPICS.contains(topic);
    }
}
