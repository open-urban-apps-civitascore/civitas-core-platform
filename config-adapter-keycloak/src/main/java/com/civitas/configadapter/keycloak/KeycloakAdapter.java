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
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Keycloak adapter that processes configuration messages and manages Keycloak resources.
 * Supports CREATE, UPDATE, and DELETE operations for realms, clients, and users.
 */
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
        Topics.USER_PASSWORD_RESET,
        Topics.REALM_CREATED,
        Topics.REALM_UPDATED,
        Topics.REALM_DELETED,
        Topics.CLIENT_CREATED,
        Topics.CLIENT_UPDATED,
        Topics.CLIENT_DELETED
    );

    private final Keycloak keycloakClient;
    private final ObjectMapper objectMapper;
    private EventPublisher eventPublisher;

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
    public void setEventPublisher(EventPublisher publisher) {
        this.eventPublisher = publisher;
        logger.info("EventPublisher injected into KeycloakAdapter");
    }

    @Override
    public List<String> getSubscribedTopics() {
        return SUBSCRIBED_TOPICS;
    }

    @Override
    public void processConfigEvent(String topic, ConfigEvent event) {
        String operation = event.payload().operation();
        String targetResource = event.payload().targetResource();
        String targetComponent = event.payload().targetComponent();

        logger.info("Processing config event - Topic: {}, Operation: {}, TargetComponent: {}, TargetResource: {}",
            topic, operation, targetComponent, targetResource);

        try {
            // Parse the target resource to determine resource type and identifiers
            ResourceInfo resourceInfo = parseTargetResource(targetResource);

            switch (operation.toUpperCase()) {
                case "CREATE" -> handleCreate(resourceInfo, event);
                case "UPDATE" -> handleUpdate(resourceInfo, event);
                case "DELETE" -> handleDelete(resourceInfo, event);
                default -> {
                    logger.warn("Unknown operation: {}", operation);
                    publishErrorResult(event, "UNSUPPORTED_OPERATION", "Unknown operation: " + operation);
                }
            }

        } catch (Exception e) {
            logger.error("Failed to process config event", e);
            publishErrorResult(event, "PROCESSING_ERROR", e.getMessage());
        }
    }

    /**
     * Parses the targetResource string to extract resource type, realm, and resource ID.
     * Expected format: "realms/{realm}/users/{userId}" or "realms/{realm}" or "users/{userId}"
     */
    private ResourceInfo parseTargetResource(String targetResource) {
        String[] parts = targetResource.split("/");

        String realm = null;
        String resourceType = null;
        String resourceId = null;

        for (int i = 0; i < parts.length; i++) {
            if ("realms".equals(parts[i]) && i + 1 < parts.length) {
                realm = parts[i + 1];
            } else if ("users".equals(parts[i])) {
                resourceType = "user";
                if (i + 1 < parts.length) {
                    resourceId = parts[i + 1];
                }
            } else if ("clients".equals(parts[i])) {
                resourceType = "client";
                if (i + 1 < parts.length) {
                    resourceId = parts[i + 1];
                }
            }
        }

        // If only "realms/{realm}" then it's a realm operation
        if (resourceType == null && realm != null) {
            resourceType = "realm";
            resourceId = realm;
        }

        logger.debug("Parsed resource - Type: {}, Realm: {}, ID: {}", resourceType, realm, resourceId);
        return new ResourceInfo(resourceType, realm, resourceId);
    }

    private void handleCreate(ResourceInfo resourceInfo, ConfigEvent event) {
        switch (resourceInfo.type) {
            case "realm" -> createRealm(event);
            case "client" -> createClient(resourceInfo.realm, event);
            case "user" -> createUser(resourceInfo.realm, event);
            default -> {
                logger.warn("Unknown resource type for create: {}", resourceInfo.type);
                publishErrorResult(event, "UNKNOWN_RESOURCE_TYPE", "Unknown resource type: " + resourceInfo.type);
            }
        }
    }

    private void handleUpdate(ResourceInfo resourceInfo, ConfigEvent event) {
        switch (resourceInfo.type) {
            case "realm" -> updateRealm(resourceInfo.id, event);
            case "client" -> updateClient(resourceInfo.realm, resourceInfo.id, event);
            case "user" -> updateUser(resourceInfo.realm, resourceInfo.id, event);
            default -> {
                logger.warn("Unknown resource type for update: {}", resourceInfo.type);
                publishErrorResult(event, "UNKNOWN_RESOURCE_TYPE", "Unknown resource type: " + resourceInfo.type);
            }
        }
    }

    private void handleDelete(ResourceInfo resourceInfo, ConfigEvent event) {
        switch (resourceInfo.type) {
            case "realm" -> deleteRealm(resourceInfo.id, event);
            case "client" -> deleteClient(resourceInfo.realm, resourceInfo.id, event);
            case "user" -> deleteUser(resourceInfo.realm, resourceInfo.id, event);
            default -> {
                logger.warn("Unknown resource type for delete: {}", resourceInfo.type);
                publishErrorResult(event, "UNKNOWN_RESOURCE_TYPE", "Unknown resource type: " + resourceInfo.type);
            }
        }
    }

    // ============== REALM OPERATIONS ==============

    private void createRealm(ConfigEvent event) {
        try {
            Object configValue = event.payload().config().value();
            RealmRepresentation realmRep = objectMapper.convertValue(configValue, RealmRepresentation.class);

            keycloakClient.realms().create(realmRep);

            String realmName = realmRep.getRealm();
            logger.info("Created realm: {}", realmName);

            publishSuccessResult(event, "Realm created successfully", realmName);

        } catch (Exception e) {
            logger.error("Failed to create realm", e);
            publishErrorResult(event, "REALM_CREATE_FAILED", e.getMessage());
        }
    }

    private void updateRealm(String realmName, ConfigEvent event) {
        try {
            Object configValue = event.payload().config().value();
            RealmRepresentation realmRep = objectMapper.convertValue(configValue, RealmRepresentation.class);

            RealmResource realmResource = keycloakClient.realm(realmName);
            realmResource.update(realmRep);

            logger.info("Updated realm: {}", realmName);
            publishSuccessResult(event, "Realm updated successfully", realmName);

        } catch (Exception e) {
            logger.error("Failed to update realm: {}", realmName, e);
            publishErrorResult(event, "REALM_UPDATE_FAILED", e.getMessage());
        }
    }

    private void deleteRealm(String realmName, ConfigEvent event) {
        try {
            keycloakClient.realm(realmName).remove();
            logger.info("Deleted realm: {}", realmName);
            publishSuccessResult(event, "Realm deleted successfully", realmName);

        } catch (Exception e) {
            logger.error("Failed to delete realm: {}", realmName, e);
            publishErrorResult(event, "REALM_DELETE_FAILED", e.getMessage());
        }
    }

    // ============== CLIENT OPERATIONS ==============

    private void createClient(String realm, ConfigEvent event) {
        try {
            Object configValue = event.payload().config().value();
            ClientRepresentation clientRep = objectMapper.convertValue(configValue, ClientRepresentation.class);

            RealmResource realmResource = keycloakClient.realm(realm);
            realmResource.clients().create(clientRep);

            logger.info("Created client: {} in realm: {}", clientRep.getClientId(), realm);
            publishSuccessResult(event, "Client created successfully", clientRep.getClientId());

        } catch (Exception e) {
            logger.error("Failed to create client in realm: {}", realm, e);
            publishErrorResult(event, "CLIENT_CREATE_FAILED", e.getMessage());
        }
    }

    private void updateClient(String realm, String clientId, ConfigEvent event) {
        try {
            Object configValue = event.payload().config().value();
            ClientRepresentation clientRep = objectMapper.convertValue(configValue, ClientRepresentation.class);

            RealmResource realmResource = keycloakClient.realm(realm);
            realmResource.clients().get(clientId).update(clientRep);

            logger.info("Updated client: {} in realm: {}", clientId, realm);
            publishSuccessResult(event, "Client updated successfully", clientId);

        } catch (Exception e) {
            logger.error("Failed to update client: {} in realm: {}", clientId, realm, e);
            publishErrorResult(event, "CLIENT_UPDATE_FAILED", e.getMessage());
        }
    }

    private void deleteClient(String realm, String clientId, ConfigEvent event) {
        try {
            RealmResource realmResource = keycloakClient.realm(realm);
            realmResource.clients().get(clientId).remove();

            logger.info("Deleted client: {} from realm: {}", clientId, realm);
            publishSuccessResult(event, "Client deleted successfully", clientId);

        } catch (Exception e) {
            logger.error("Failed to delete client: {} from realm: {}", clientId, realm, e);
            publishErrorResult(event, "CLIENT_DELETE_FAILED", e.getMessage());
        }
    }

    // ============== USER OPERATIONS ==============
    /*
     * {
  "metadata": {
    "messageId": "uuid-v4-of-this-message",
    "timestamp": "2025-09-30T15:31:50Z",
    "source": "onboarding-service",
    "correlationId": "corr-id-abc",
    "configVersion": "v1.2.3-commit-hash"
  },
  "payload": {
    "targetComponent": "keycloak",
    "targetResource": "realms/my-app-realm",
    "operation": "CREATE",
    "config": {
      "path": null,
      "value": {
        "clientId": "my-new-app",
        "protocol": "openid-connect",
        "publicClient": false,
        "redirectUris": ["https://myapp.com/*"],
        "enabled": true
      }
    }
  }
}	

     */

    private void createUser(String realm, ConfigEvent event) {
        try {
            Object configValue = event.payload().config().value();
            UserRepresentation userRep = objectMapper.convertValue(configValue, UserRepresentation.class);

            RealmResource realmResource = keycloakClient.realm(realm);
            realmResource.users().create(userRep);

            logger.info("Created user: {} in realm: {}", userRep.getUsername(), realm);
            publishSuccessResult(event, "User created successfully", userRep.getUsername());

        } catch (Exception e) {
            logger.error("Failed to create user in realm: {}", realm, e);
            publishErrorResult(event, "USER_CREATE_FAILED", e.getMessage());
        }
    }

    private void updateUser(String realm, String userId, ConfigEvent event) {
        try {
            Object configValue = event.payload().config().value();
            UserRepresentation userRep = objectMapper.convertValue(configValue, UserRepresentation.class);

            RealmResource realmResource = keycloakClient.realm(realm);
            realmResource.users().get(userId).update(userRep);

            logger.info("Updated user: {} in realm: {}", userId, realm);
            publishSuccessResult(event, "User updated successfully", userId);

        } catch (Exception e) {
            logger.error("Failed to update user: {} in realm: {}", userId, realm, e);
            publishErrorResult(event, "USER_UPDATE_FAILED", e.getMessage());
        }
    }

    private void deleteUser(String realm, String userId, ConfigEvent event) {
        try {
            RealmResource realmResource = keycloakClient.realm(realm);
            realmResource.users().get(userId).remove();

            logger.info("Deleted user: {} from realm: {}", userId, realm);
            publishSuccessResult(event, "User deleted successfully", userId);

        } catch (Exception e) {
            logger.error("Failed to delete user: {} from realm: {}", userId, realm, e);
            publishErrorResult(event, "USER_DELETE_FAILED", e.getMessage());
        }
    }

    // ============== RESULT PUBLISHING ==============

    private void publishSuccessResult(ConfigEvent originalEvent, String message, String resourceId) {
        if (eventPublisher == null || originalEvent.metadata().resultTopic() == null) {
            return;
        }

        try {
            // Include minimal data body - Kafka treats NULL values as tombstones
            // which causes the deserializer to return NULL
            CloudEvent resultEvent = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("civitas.config-adapter.keycloak"))
                .withType("core.civitas.idm.processing.result")
                .withTime(OffsetDateTime.now())
                .withData("application/json", "{}".getBytes())
                .withExtension("correlationid", originalEvent.metadata().correlationId())
                .withExtension("originalmessageid", originalEvent.metadata().messageId())
                .withExtension("status", "SUCCESS")
                .withExtension("message", message)
                .withExtension("resourceid", resourceId)
                .withExtension("operation", originalEvent.payload().operation())
                .withExtension("targetresource", originalEvent.payload().targetResource())
                .build();

            eventPublisher.publish(originalEvent.metadata().resultTopic(), resultEvent);
            logger.debug("Published SUCCESS result to topic: {}", originalEvent.metadata().resultTopic());

        } catch (Exception e) {
            logger.error("Failed to publish success result", e);
        }
    }

    private void publishErrorResult(ConfigEvent originalEvent, String errorCode, String errorMessage) {
        if (eventPublisher == null || originalEvent.metadata().resultTopic() == null) {
            return;
        }

        try {
            // Include minimal data body - Kafka treats NULL values as tombstones
            // which causes the deserializer to return NULL
            CloudEvent resultEvent = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("civitas.config-adapter.keycloak"))
                .withType("core.civitas.idm.processing.result")
                .withTime(OffsetDateTime.now())
                .withData("application/json", "{}".getBytes())
                .withExtension("correlationid", originalEvent.metadata().correlationId())
                .withExtension("originalmessageid", originalEvent.metadata().messageId())
                .withExtension("status", "FAILURE")
                .withExtension("errorcode", errorCode)
                .withExtension("errormessage", errorMessage)
                .withExtension("operation", originalEvent.payload().operation())
                .withExtension("targetresource", originalEvent.payload().targetResource())
                .build();

            eventPublisher.publish(originalEvent.metadata().resultTopic(), resultEvent);
            logger.debug("Published FAILURE result to topic: {}", originalEvent.metadata().resultTopic());

        } catch (Exception e) {
            logger.error("Failed to publish error result", e);
        }
    }

    @Override
    public void close() {
        if (keycloakClient != null) {
            keycloakClient.close();
            logger.info("Keycloak adapter closed");
        }
    }

    /**
     * Helper record to hold parsed resource information
     */
    private record ResourceInfo(String type, String realm, String id) {}
}
