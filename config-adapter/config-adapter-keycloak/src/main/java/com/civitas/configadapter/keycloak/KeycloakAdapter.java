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
package com.civitas.configadapter.keycloak;

import com.civitas.configadapter.adapter.AbstractConfigAdapter;
import com.civitas.configadapter.configuration.AdapterConfig;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.Operation;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keycloak adapter that processes configuration messages and manages Keycloak resources. Supports
 * CREATE, UPDATE, and DELETE operations for realms, clients, and users.
 */
public class KeycloakAdapter extends AbstractConfigAdapter {

  private static final Logger logger = LoggerFactory.getLogger(KeycloakAdapter.class);

  public static final String ADAPTER_NAME = "keycloak";

  private final ObjectMapper objectMapper;
  private Keycloak keycloakClient;

  public KeycloakAdapter() {
    this.objectMapper = new ObjectMapper();
    // Configure to ignore the "resourceType" field when converting to Keycloak representations
    this.objectMapper.configure(
        com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  /*
   * (non-Javadoc)
   * @see com.civitas.configadapter.adapter.AbstractConfigAdapter#initialize(com.civitas.configadapter.config.AppConfig)
   */
  @Override
  public void initialize(AdapterConfig config) {
    super.initialize(config);
    this.keycloakClient =
        KeycloakBuilder.builder()
            .serverUrl(getAdapterProperty("url", "http://localhost:8080"))
            .realm(getAdapterProperty("realm", "master"))
            .username(getAdapterProperty("username", "admin"))
            .password(getAdapterProperty("password", "admin"))
            .clientId(getAdapterProperty("client.id", "admin-cli"))
            .build();

    logger.info(
        "Keycloak adapter '{}' initialized for: {}",
        getName(),
        getAdapterProperty("url", "http://localhost:8080"));
    logger.info(
        "Subscribed to {} Kafka topics: {}", getSubscribedTopics().size(), getSubscribedTopics());
  }

  /*
   * (non-Javadoc)
   * @see com.civitas.configadapter.adapter.ConfigAdapter#getName()
   */
  @Override
  public String getName() {
    return ADAPTER_NAME;
  }

  @Override
  public void processConfigEvent(String topic, ConfigEvent event) {
    Operation operation = event.payload().operation();
    String targetResource = event.payload().targetResource();
    String targetComponent = event.payload().targetComponent();

    logger.info(
        "Processing config event - Topic: {}, Operation: {}, TargetComponent: {}, TargetResource: {}",
        topic,
        operation,
        targetComponent,
        targetResource);

    try {
      // Parse the target resource to determine resource type and identifiers
      ResourceInfo resourceInfo = parseTargetResource(targetResource);

      switch (operation) {
        case CREATE -> handleCreate(resourceInfo, event);
        case UPDATE -> handleUpdate(resourceInfo, event);
        case DELETE -> handleDelete(resourceInfo, event);
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
   * Parses the targetResource string to extract resource type, realm, and resource ID. Expected
   * format: "realms/{realm}/users/{userId}" or "realms/{realm}" or "users/{userId}"
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
      } else if ("roles".equals(parts[i])) {
        resourceType = "role";
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
      case "role" -> createRole(resourceInfo.realm, event);
      default -> {
        logger.warn("Unknown resource type for create: {}", resourceInfo.type);
        publishErrorResult(
            event, "UNKNOWN_RESOURCE_TYPE", "Unknown resource type: " + resourceInfo.type);
      }
    }
  }

  private void handleUpdate(ResourceInfo resourceInfo, ConfigEvent event) {
    switch (resourceInfo.type) {
      case "realm" -> updateRealm(resourceInfo.id, event);
      case "client" -> updateClient(resourceInfo.realm, resourceInfo.id, event);
      case "user" -> updateUser(resourceInfo.realm, resourceInfo.id, event);
      case "role" -> updateRole(resourceInfo.realm, resourceInfo.id, event);
      default -> {
        logger.warn("Unknown resource type for update: {}", resourceInfo.type);
        publishErrorResult(
            event, "UNKNOWN_RESOURCE_TYPE", "Unknown resource type: " + resourceInfo.type);
      }
    }
  }

  private void handleDelete(ResourceInfo resourceInfo, ConfigEvent event) {
    switch (resourceInfo.type) {
      case "realm" -> deleteRealm(resourceInfo.id, event);
      case "client" -> deleteClient(resourceInfo.realm, resourceInfo.id, event);
      case "user" -> deleteUser(resourceInfo.realm, resourceInfo.id, event);
      case "role" -> deleteRole(resourceInfo.realm, resourceInfo.id, event);
      default -> {
        logger.warn("Unknown resource type for delete: {}", resourceInfo.type);
        publishErrorResult(
            event, "UNKNOWN_RESOURCE_TYPE", "Unknown resource type: " + resourceInfo.type);
      }
    }
  }

  // ============== REALM OPERATIONS ==============

  /**
   * Extracts the configuration data from a ConfigValue for ObjectMapper conversion.
   *
   * @param configValue the typed configuration value
   * @return the config value object for ObjectMapper conversion
   */
  private Object extractConfigData(com.civitas.configadapter.model.ConfigValue configValue) {
    // With concrete POJOs, we can pass them directly to ObjectMapper
    // which will map the fields to Keycloak representations
    return configValue;
  }

  private void createRealm(ConfigEvent event) {
    try {
      Object configData = extractConfigData(event.payload().config().value());
      RealmRepresentation realmRep =
          objectMapper.convertValue(configData, RealmRepresentation.class);

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
      Object configData = extractConfigData(event.payload().config().value());
      RealmRepresentation realmRep =
          objectMapper.convertValue(configData, RealmRepresentation.class);

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
      Object configData = extractConfigData(event.payload().config().value());
      ClientRepresentation clientRep =
          objectMapper.convertValue(configData, ClientRepresentation.class);

      RealmResource realmResource = keycloakClient.realm(realm);

      try (Response response = realmResource.clients().create(clientRep)) {
        validateResponse("Client creation", 201, response);
      }

      logger.info("Created client: {} in realm: {}", clientRep.getClientId(), realm);
      publishSuccessResult(event, "Client created successfully", clientRep.getClientId());

    } catch (Exception e) {
      logger.error("Failed to create client in realm: {}", realm, e);
      publishErrorResult(event, "CLIENT_CREATE_FAILED", e.getMessage());
    }
  }

  private void updateClient(String realm, String clientId, ConfigEvent event) {
    try {
      Object configData = extractConfigData(event.payload().config().value());
      ClientRepresentation clientRep =
          objectMapper.convertValue(configData, ClientRepresentation.class);

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
      Object configData = extractConfigData(event.payload().config().value());
      UserRepresentation userRep = objectMapper.convertValue(configData, UserRepresentation.class);

      List<String> rolesToAssignNames = userRep.getRealmRoles();
      userRep.setRealmRoles(null);

      Map<String, List<String>> clientRolesMap = userRep.getClientRoles();
      userRep.setClientRoles(null);

      RealmResource realmResource = keycloakClient.realm(realm);

      String userId;
      try (Response response = realmResource.users().create(userRep)) {
        validateResponse("User creation", 201, response);
        userId = CreatedResponseUtil.getCreatedId(response);
      }

      AssignRealmRolesToUser(rolesToAssignNames, realmResource, userId);
      AssignClientRolesToUser(clientRolesMap, realmResource, userId);

      logger.info("Created user: {} in realm: {}", userRep.getUsername(), realm);
      publishSuccessResult(event, "User created successfully", userRep.getUsername());

    } catch (Exception e) {
      logger.error("Failed to create user in realm: {}", realm, e);
      publishErrorResult(event, "USER_CREATE_FAILED", e.getMessage());
    }
  }

  private void updateUser(String realm, String userId, ConfigEvent event) {
    try {
      Object configData = extractConfigData(event.payload().config().value());
      UserRepresentation userRep = objectMapper.convertValue(configData, UserRepresentation.class);

      List<String> rolesToAssignNames = userRep.getRealmRoles();
      userRep.setRealmRoles(null);

      Map<String, List<String>> clientRolesMap = userRep.getClientRoles();
      userRep.setClientRoles(null);

      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.users().get(userId).update(userRep);

      AssignRealmRolesToUser(rolesToAssignNames, realmResource, userId);
      AssignClientRolesToUser(clientRolesMap, realmResource, userId);

      logger.info("Updated user: {} in realm: {}", userId, realm);
      publishSuccessResult(event, "User updated successfully", userId);

    } catch (Exception e) {
      logger.error("Failed to update user: {} in realm: {}", userId, realm, e);
      publishErrorResult(event, "USER_UPDATE_FAILED", e.getMessage());
    }
  }

  private static void AssignClientRolesToUser(
      Map<String, List<String>> clientRolesMap, RealmResource realmResource, String userId) {
    if (clientRolesMap != null && !clientRolesMap.isEmpty()) {
      UserResource userResource = realmResource.users().get(userId);

      for (Map.Entry<String, List<String>> entry : clientRolesMap.entrySet()) {
        String clientId = entry.getKey();
        List<String> roleNames = entry.getValue();

        try {
          List<ClientRepresentation> clients = realmResource.clients().findByClientId(clientId);
          if (clients.isEmpty()) {
            logger.warn("Client '{}' not found, skipping roles.", clientId);
            continue;
          }
          String clientUuid = clients.getFirst().getId();

          List<RoleRepresentation> rolesToAdd = new ArrayList<>();
          for (String roleName : roleNames) {
            try {
              RoleRepresentation role =
                  realmResource.clients().get(clientUuid).roles().get(roleName).toRepresentation();
              rolesToAdd.add(role);
            } catch (NotFoundException nfe) {
              logger.warn("Role '{}' not found for client '{}'", roleName, clientId);
            }
          }

          if (!rolesToAdd.isEmpty()) {
            userResource.roles().clientLevel(clientUuid).add(rolesToAdd);
          }

        } catch (Exception e) {
          logger.error("Failed to assign client roles for client '{}'", clientId, e);
        }
      }
    }
  }

  private static void AssignRealmRolesToUser(
      List<String> rolesToAssignNames, RealmResource realmResource, String userId) {
    if (rolesToAssignNames != null && !rolesToAssignNames.isEmpty()) {

      List<RoleRepresentation> rolesToAdd = new ArrayList<>();
      for (String roleName : rolesToAssignNames) {
        try {
          RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();
          rolesToAdd.add(role);
        } catch (NotFoundException nfe) {
          logger.warn("Role '{}' not found, cannot assign.", roleName);
        }
      }

      if (!rolesToAdd.isEmpty()) {
        realmResource.users().get(userId).roles().realmLevel().add(rolesToAdd);
      }
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

  // ============== ROLE OPERATIONS ==============

  private void createRole(String realm, ConfigEvent event) {
    try {
      Object configData = extractConfigData(event.payload().config().value());
      RoleRepresentation roleRep = convertToRoleRepresentation(configData);

      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.roles().create(roleRep);

      logger.info("Created role: {} in realm: {}", roleRep.getName(), realm);
      publishSuccessResult(event, "Role created successfully", roleRep.getName());

    } catch (Exception e) {
      logger.error("Failed to create role in realm: {}", realm, e);
      publishErrorResult(event, "ROLE_CREATE_FAILED", e.getMessage());
    }
  }

  private void updateRole(String realm, String roleId, ConfigEvent event) {
    try {
      Object configData = extractConfigData(event.payload().config().value());
      RoleRepresentation roleRep = convertToRoleRepresentation(configData);

      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.roles().get(roleId).update(roleRep);

      logger.info("Updated role: {} in realm: {}", roleId, realm);
      publishSuccessResult(event, "User updated successfully", roleId);

    } catch (Exception e) {
      logger.error("Failed to update role: {} in realm: {}", roleId, realm, e);
      publishErrorResult(event, "ROLE_UPDATE_FAILED", e.getMessage());
    }
  }

  /**
   * Converts RoleConfig to RoleRepresentation with special handling for composite roles. This
   * method handles the conversion from our simple Set&lt;String&gt; compositeRoles to Keycloak's
   * complex Composites structure.
   */
  private RoleRepresentation convertToRoleRepresentation(Object configData) {
    RoleRepresentation roleRep = objectMapper.convertValue(configData, RoleRepresentation.class);

    // Handle composite roles conversion if present
    if (configData instanceof com.civitas.configadapter.model.idm.RoleConfig roleConfig) {
      if (roleConfig.getCompositeRoles() != null && !roleConfig.getCompositeRoles().isEmpty()) {
        // Create Keycloak's Composites structure from our simple Set<String>
        RoleRepresentation.Composites composites = new RoleRepresentation.Composites();
        composites.setRealm(roleConfig.getCompositeRoles());
        roleRep.setComposites(composites);
      }
    }

    return roleRep;
  }

  private void deleteRole(String realm, String roleId, ConfigEvent event) {
    try {
      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.roles().get(roleId).remove();

      logger.info("Deleted role: {} from realm: {}", roleId, realm);
      publishSuccessResult(event, "Role deleted successfully", roleId);

    } catch (Exception e) {
      logger.error("Failed to delete role: {} from realm: {}", roleId, realm, e);
      publishErrorResult(event, "ROLE_DELETE_FAILED", e.getMessage());
    }
  }

  private void validateResponse(String operationDescription, int expectedStatus, Response response)
      throws Exception {
    if (response.getStatus() != expectedStatus) {
      String errorBody = response.readEntity(String.class);
      throw new Exception(
          operationDescription + " failed with status " + response.getStatus() + ": " + errorBody);
    }
  }

  // ============== RESULT PUBLISHING ==============

  private void publishSuccessResult(ConfigEvent originalEvent, String message, String resourceId) {
    if (getEventPublisher() == null || originalEvent.metadata().resultTopic() == null) {
      return;
    }

    try {
      ConfigResultEvent resultEvent =
          ConfigResultEvent.success(
              originalEvent.metadata().correlationId(),
              originalEvent.metadata().messageId(),
              message,
              resourceId,
              originalEvent.payload().operation(),
              originalEvent.payload().targetResource(),
              "civitas.config-adapter.keycloak");

      getEventPublisher().publish(originalEvent.metadata().resultTopic(), resultEvent);
      logger.debug("Published SUCCESS result to topic: {}", originalEvent.metadata().resultTopic());

    } catch (Exception e) {
      logger.error("Failed to publish success result", e);
    }
  }

  private void publishErrorResult(
      ConfigEvent originalEvent, String errorCode, String errorMessage) {
    if (getEventPublisher() == null || originalEvent.metadata().resultTopic() == null) {
      return;
    }

    try {
      ConfigResultEvent resultEvent =
          ConfigResultEvent.failure(
              originalEvent.metadata().correlationId(),
              originalEvent.metadata().messageId(),
              errorCode,
              errorMessage,
              originalEvent.payload().operation(),
              originalEvent.payload().targetResource(),
              "civitas.config-adapter.keycloak");

      getEventPublisher().publish(originalEvent.metadata().resultTopic(), resultEvent);
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

  /** Helper record to hold parsed resource information */
  private record ResourceInfo(String type, String realm, String id) {}
}
