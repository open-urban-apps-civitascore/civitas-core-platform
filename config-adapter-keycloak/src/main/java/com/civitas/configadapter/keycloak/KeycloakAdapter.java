package com.civitas.configadapter.keycloak;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.config.AppConfig;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public class KeycloakAdapter implements ConfigAdapter {

	private static final Logger logger = LoggerFactory.getLogger(KeycloakAdapter.class);

	private static final String KEYCLOAK_URL = "keycloak.url";
	private static final String KEYCLOAK_REALM = "keycloak.realm";
	private static final String KEYCLOAK_USERNAME = "keycloak.username";
	private static final String KEYCLOAK_PASSWORD = "keycloak.password";
	private static final String KEYCLOAK_CLIENT_ID = "keycloak.client.id";

    private static final List<String> SUBSCRIBED_TOPICS = List.of(
        Topics.USER_CREATED,
        Topics.USER_UPDATED,
        Topics.USER_DELETED,
        Topics.USER_LOCKED,
        Topics.USER_UNLOCKED,
        Topics.USER_PASSWORD_CHANGED,
        Topics.USER_PASSWORD_RESET
    );

    private final Keycloak keycloakClient;
    private final ObjectMapper objectMapper;

    public KeycloakAdapter(AppConfig config) {
        this.keycloakClient = KeycloakBuilder.builder()
            .serverUrl(config.getProperty(KEYCLOAK_URL, "http://localhost:8080"))
            .realm(config.getProperty(KEYCLOAK_REALM, "master"))
            .username(config.getProperty(KEYCLOAK_USERNAME, "admin"))
            .password(config.getProperty(KEYCLOAK_PASSWORD, "admin"))
            .clientId(config.getProperty(KEYCLOAK_CLIENT_ID, "admin-cli"))
            .build();
        this.objectMapper = new ObjectMapper();

        logger.info("Keycloak adapter initialized for: {}", config.getProperty(KEYCLOAK_URL, "http://localhost:8080"));
        logger.info("Subscribed to {} Kafka topics: {}", SUBSCRIBED_TOPICS.size(), SUBSCRIBED_TOPICS);
    }

    @Override
    public List<String> getSubscribedTopics() {
        return SUBSCRIBED_TOPICS;
    }

    @Override
    public void processConfigEvent(ConfigEvent event) {
        logger.info("Processing config event - Action: {}, ResourceType: {}, Realm: {}",
            event.action(), event.resourceType(), event.realm());

        try {
            switch (event.action().toLowerCase()) {
                case "create" -> handleCreate(event);
                case "update" -> handleUpdate(event);
                case "delete" -> handleDelete(event);
                default -> logger.warn("Unknown action: {}", event.action());
            }
        } catch (Exception e) {
            logger.error("Failed to process config event", e);
        }
    }

    private void handleCreate(ConfigEvent event) {
        String resourceType = event.resourceType().toLowerCase();
        String realm = event.realm();

        switch (resourceType) {
            case "realm" -> createRealm(event);
            case "client" -> createClient(realm, event);
            case "user" -> createUser(realm, event);
            default -> logger.warn("Unknown resource type for create: {}", resourceType);
        }
    }

    private void handleUpdate(ConfigEvent event) {
        String resourceType = event.resourceType().toLowerCase();
        String realm = event.realm();

        switch (resourceType) {
            case "realm" -> updateRealm(event);
            case "client" -> updateClient(realm, event);
            case "user" -> updateUser(realm, event);
            default -> logger.warn("Unknown resource type for update: {}", resourceType);
        }
    }

    private void handleDelete(ConfigEvent event) {
        String resourceType = event.resourceType().toLowerCase();
        String realm = event.realm();
        String resourceId = event.resourceId();

        switch (resourceType) {
            case "realm" -> deleteRealm(realm);
            case "client" -> deleteClient(realm, resourceId);
            case "user" -> deleteUser(realm, resourceId);
            default -> logger.warn("Unknown resource type for delete: {}", resourceType);
        }
    }

    private void createRealm(ConfigEvent event) {
        try {
            RealmRepresentation realmRep = objectMapper.convertValue(event.data(), RealmRepresentation.class);
            keycloakClient.realms().create(realmRep);
            logger.info("Created realm: {}", realmRep.getRealm());
        } catch (Exception e) {
            logger.error("Failed to create realm", e);
        }
    }

    private void updateRealm(ConfigEvent event) {
        try {
            RealmRepresentation realmRep = objectMapper.convertValue(event.data(), RealmRepresentation.class);
            RealmResource realmResource = keycloakClient.realm(event.realm());
            realmResource.update(realmRep);
            logger.info("Updated realm: {}", event.realm());
        } catch (Exception e) {
            logger.error("Failed to update realm: {}", event.realm(), e);
        }
    }

    private void deleteRealm(String realm) {
        try {
            keycloakClient.realm(realm).remove();
            logger.info("Deleted realm: {}", realm);
        } catch (Exception e) {
            logger.error("Failed to delete realm: {}", realm, e);
        }
    }

    private void createClient(String realm, ConfigEvent event) {
        try {
            ClientRepresentation clientRep = objectMapper.convertValue(event.data(), ClientRepresentation.class);
            RealmResource realmResource = keycloakClient.realm(realm);
            realmResource.clients().create(clientRep);
            logger.info("Created client: {} in realm: {}", clientRep.getClientId(), realm);
        } catch (Exception e) {
            logger.error("Failed to create client in realm: {}", realm, e);
        }
    }

    private void updateClient(String realm, ConfigEvent event) {
        try {
            String clientId = event.resourceId();
            ClientRepresentation clientRep = objectMapper.convertValue(event.data(), ClientRepresentation.class);
            RealmResource realmResource = keycloakClient.realm(realm);
            realmResource.clients().get(clientId).update(clientRep);
            logger.info("Updated client: {} in realm: {}", clientId, realm);
        } catch (Exception e) {
            logger.error("Failed to update client: {} in realm: {}", event.resourceId(), realm, e);
        }
    }

    private void deleteClient(String realm, String clientId) {
        try {
            RealmResource realmResource = keycloakClient.realm(realm);
            realmResource.clients().get(clientId).remove();
            logger.info("Deleted client: {} from realm: {}", clientId, realm);
        } catch (Exception e) {
            logger.error("Failed to delete client: {} from realm: {}", clientId, realm, e);
        }
    }

    private void createUser(String realm, ConfigEvent event) {
        try {
            UserRepresentation userRep = objectMapper.convertValue(event.data(), UserRepresentation.class);
            RealmResource realmResource = keycloakClient.realm(realm);
            realmResource.users().create(userRep);
            logger.info("Created user: {} in realm: {}", userRep.getUsername(), realm);
        } catch (Exception e) {
            logger.error("Failed to create user in realm: {}", realm, e);
        }
    }

    private void updateUser(String realm, ConfigEvent event) {
        try {
            String userId = event.resourceId();
            UserRepresentation userRep = objectMapper.convertValue(event.data(), UserRepresentation.class);
            RealmResource realmResource = keycloakClient.realm(realm);
            realmResource.users().get(userId).update(userRep);
            logger.info("Updated user: {} in realm: {}", userId, realm);
        } catch (Exception e) {
            logger.error("Failed to update user: {} in realm: {}", event.resourceId(), realm, e);
        }
    }

    private void deleteUser(String realm, String userId) {
        try {
            RealmResource realmResource = keycloakClient.realm(realm);
            realmResource.users().get(userId).remove();
            logger.info("Deleted user: {} from realm: {}", userId, realm);
        } catch (Exception e) {
            logger.error("Failed to delete user: {} from realm: {}", userId, realm, e);
        }
    }

    @Override
    public void close() {
        if (keycloakClient != null) {
            keycloakClient.close();
            logger.info("Keycloak adapter closed");
        }
    }
}
