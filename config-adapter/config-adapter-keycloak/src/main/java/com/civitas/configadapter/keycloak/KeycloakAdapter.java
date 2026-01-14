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
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
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
      } else if ("groups".equals(parts[i])) {
        resourceType = "group";
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
      case "group" -> createGroup(resourceInfo.realm, event);
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
      case "group" -> updateGroup(resourceInfo.realm, resourceInfo.id, event);
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
      case "group" -> deleteGroup(resourceInfo.realm, resourceInfo.id, event);
      default -> {
        logger.warn("Unknown resource type for delete: {}", resourceInfo.type);
        publishErrorResult(
                event, "UNKNOWN_RESOURCE_TYPE", "Unknown resource type: " + resourceInfo.type);
      }
    }
  }

  // ============== REALM OPERATIONS ==============

  private void createRealm(ConfigEvent event) {
    try {
      Object configValue = event.payload().config().value();
      RealmRepresentation realmRep =
              objectMapper.convertValue(configValue, RealmRepresentation.class);

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
      RealmRepresentation realmRep =
              objectMapper.convertValue(configValue, RealmRepresentation.class);

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
      ClientRepresentation clientRep =
              objectMapper.convertValue(configValue, ClientRepresentation.class);

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

  private void updateClient(String realm, String clientPublicId, ConfigEvent event) { // Variable umbenannt
    try {
      Object configValue = event.payload().config().value();
      ClientRepresentation clientRep =
              objectMapper.convertValue(configValue, ClientRepresentation.class);

      RealmResource realmResource = keycloakClient.realm(realm);

      String internalId = getClientUuidByName(realmResource, clientPublicId);

      realmResource.clients().get(internalId).update(clientRep);

      logger.info("Updated client: {} (UUID: {}) in realm: {}", clientPublicId, internalId, realm);
      publishSuccessResult(event, "Client updated successfully", clientPublicId);

    } catch (Exception e) {
      logger.error("Failed to update client: {} in realm: {}", clientPublicId, realm, e);
      publishErrorResult(event, "CLIENT_UPDATE_FAILED", e.getMessage());
    }
  }

  private void deleteClient(String realm, String clientPublicId, ConfigEvent event) {
    try {
      RealmResource realmResource = keycloakClient.realm(realm);

      String internalId = getClientUuidByName(realmResource, clientPublicId);

      realmResource.clients().get(internalId).remove();

      logger.info("Deleted client: {} from realm: {}", clientPublicId, realm);
      publishSuccessResult(event, "Client deleted successfully", clientPublicId);

    } catch (Exception e) {
      logger.error("Failed to delete client: {} from realm: {}", clientPublicId, realm, e);
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

      RoleMappingResource roleMapping = realmResource.users().get(userId).roles();
      syncRealmRoles(rolesToAssignNames, roleMapping, realmResource);
      syncClientRoles(clientRolesMap, roleMapping, realmResource);

      logger.info("Created user: {} in realm: {}", userRep.getUsername(), realm);
      publishSuccessResult(event, "User created successfully", userRep.getUsername());

    } catch (Exception e) {
      logger.error("Failed to create user in realm: {}", realm, e);
      publishErrorResult(event, "USER_CREATE_FAILED", e.getMessage());
    }
  }

  private void updateUser(String realm, String username, ConfigEvent event) { // Variable umbenannt: userId -> username
    try {
      Object configValue = event.payload().config().value();
      UserRepresentation userRep = objectMapper.convertValue(configValue, UserRepresentation.class);

      RealmResource realmResource = keycloakClient.realm(realm);

      String userId = getUserIdByName(realmResource, username);
      userRep.setId(userId);

      List<String> rolesToAssignNames = userRep.getRealmRoles();
      userRep.setRealmRoles(null);

      Map<String, List<String>> clientRolesMap = userRep.getClientRoles();
      userRep.setClientRoles(null);

      realmResource.users().get(userId).update(userRep);

      RoleMappingResource roleMapping = realmResource.users().get(userId).roles();
      syncRealmRoles(rolesToAssignNames, roleMapping, realmResource);
      syncClientRoles(clientRolesMap, roleMapping, realmResource);

      logger.info("Updated user: {} (ID: {}) in realm: {}", username, userId, realm);
      publishSuccessResult(event, "User updated successfully", userId);

    } catch (Exception e) {
      logger.error("Failed to update user: {} in realm: {}", username, realm, e);
      publishErrorResult(event, "USER_UPDATE_FAILED", e.getMessage());
    }
  }

  private void deleteUser(String realm, String username, ConfigEvent event) {
    try {
      RealmResource realmResource = keycloakClient.realm(realm);

      String userId = getUserIdByName(realmResource, username);

      realmResource.users().get(userId).remove();

      logger.info("Deleted user: {} from realm: {}", username, realm);
      publishSuccessResult(event, "User deleted successfully", userId);

    } catch (Exception e) {
      logger.error("Failed to delete user: {} from realm: {}", username, realm, e);
      publishErrorResult(event, "USER_DELETE_FAILED", e.getMessage());
    }
  }

  // ============== ROLE OPERATIONS ==============

  private void createRole(String realm, ConfigEvent event) {
    try {
      Object configValue = event.payload().config().value();
      RoleRepresentation roleRep = objectMapper.convertValue(configValue, RoleRepresentation.class);

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
      Object configValue = event.payload().config().value();
      RoleRepresentation roleRep = objectMapper.convertValue(configValue, RoleRepresentation.class);

      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.roles().get(roleId).update(roleRep);

      logger.info("Updated role: {} in realm: {}", roleId, realm);
      publishSuccessResult(event, "User updated successfully", roleId);

    } catch (Exception e) {
      logger.error("Failed to update role: {} in realm: {}", roleId, realm, e);
      publishErrorResult(event, "ROLE_UPDATE_FAILED", e.getMessage());
    }
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


  // ============== GROUP OPERATIONS ==============

  private void createGroup(String realm, ConfigEvent event) {
    try {
      Object configValue = event.payload().config().value();
      GroupRepresentation groupRep =
              objectMapper.convertValue(configValue, GroupRepresentation.class);

      List<String> realmRolesToAssign = groupRep.getRealmRoles();
      Map<String, List<String>> clientRolesToAssign = groupRep.getClientRoles();

      RealmResource realmResource = keycloakClient.realm(realm);
      String groupId;

      if (groupRep.getParentId() != null && !groupRep.getParentId().isEmpty()) {
        try (Response response =
                     realmResource.groups().group(groupRep.getParentId()).subGroup(groupRep)) {
          validateResponse("Group creation", 201, response);
          groupId = CreatedResponseUtil.getCreatedId(response);
        }
      } else {
        try (Response response = realmResource.groups().add(groupRep)) {
          validateResponse("Group creation", 201, response);
          groupId = CreatedResponseUtil.getCreatedId(response);
        }
      }

      RoleMappingResource roleMapping = realmResource.groups().group(groupId).roles();
      syncRealmRoles(realmRolesToAssign, roleMapping, realmResource);
      syncClientRoles(clientRolesToAssign, roleMapping, realmResource);

      logger.info("Created group: {} (ID: {}) in realm: {}", groupRep.getName(), groupId, realm);
      publishSuccessResult(event, "Group created successfully", groupId);

    } catch (Exception e) {
      logger.error("Failed to create group in realm: {}", realm, e);
      publishErrorResult(event, "GROUP_CREATE_FAILED", e.getMessage());
    }
  }

  private void updateGroup(String realm, String groupName, ConfigEvent event) {
    try {
      Object configValue = event.payload().config().value();
      GroupRepresentation groupRep =
              objectMapper.convertValue(configValue, GroupRepresentation.class);

      RealmResource realmResource = keycloakClient.realm(realm);

      String groupId = getGroupIdByName(realmResource, groupName);
      groupRep.setId(groupId);

      List<String> realmRolesToAssign = groupRep.getRealmRoles();
      Map<String, List<String>> clientRolesToAssign = groupRep.getClientRoles();

      realmResource.groups().group(groupId).update(groupRep);

      RoleMappingResource roleMapping = realmResource.groups().group(groupId).roles();
      syncRealmRoles(realmRolesToAssign, roleMapping, realmResource);
      syncClientRoles(clientRolesToAssign, roleMapping, realmResource);

      logger.info("Updated group: {} in realm: {}", groupId, realm);
      publishSuccessResult(event, "Group updated successfully", groupId);

    } catch (Exception e) {
      logger.error("Failed to update group: {} in realm: {}", groupName, realm, e);
      publishErrorResult(event, "GROUP_UPDATE_FAILED", e.getMessage());
    }
  }

  private void deleteGroup(String realm, String groupName, ConfigEvent event) {
    try {
      RealmResource realmResource = keycloakClient.realm(realm);
      String groupId = getGroupIdByName(realmResource, groupName);

      realmResource.groups().group(groupId).remove();

      logger.info("Deleted group: {} from realm: {}", groupId, realm);
      publishSuccessResult(event, "Group deleted successfully", groupId);

    } catch (Exception e) {
      logger.error("Failed to delete group: {} from realm: {}", groupName, realm, e);
      publishErrorResult(event, "GROUP_DELETE_FAILED", e.getMessage());
    }
  }

  // ============== HELPER METHODS FOR SYNC ==============

  private String getClientUuidByName(RealmResource realmResource, String publicClientId) {
    List<ClientRepresentation> clients = realmResource.clients().findByClientId(publicClientId);

    if (clients.isEmpty()) {
      throw new jakarta.ws.rs.NotFoundException("Client not found with clientId: " + publicClientId);
    }

    return clients.getFirst().getId();
  }

  private String getUserIdByName(RealmResource realmResource, String username) {
    // search(username, exact=true)
    List<UserRepresentation> users = realmResource.users().search(username, true);

    if (users.isEmpty()) {
      throw new jakarta.ws.rs.NotFoundException("User not found with username: " + username);
    }
    return users.getFirst().getId();
  }

  private String getGroupIdByName(RealmResource realmResource, String groupName) {
    List<GroupRepresentation> foundGroups = realmResource.groups()
            .groups(groupName, true, 0, 1, true);

    if (foundGroups.isEmpty()) {
      throw new jakarta.ws.rs.NotFoundException("Group not found with name: " + groupName);
    }
    return foundGroups.getFirst().getId();
  }

  private void syncRealmRoles(List<String> desiredRoleNames, RoleMappingResource roleMappingResource, RealmResource realmResource) {
    List<String> desired = desiredRoleNames != null ? desiredRoleNames : java.util.Collections.emptyList();
    var roleScope = roleMappingResource.realmLevel();

    List<RoleRepresentation> currentRoles = roleScope.listAll();

    List<RoleRepresentation> toRemove = currentRoles.stream()
            .filter(current -> !desired.contains(current.getName()))
            .toList();

    List<RoleRepresentation> toAdd = new java.util.ArrayList<>();
    for (String desiredName : desired) {
      boolean alreadyHas = currentRoles.stream().anyMatch(c -> c.getName().equals(desiredName));
      if (!alreadyHas) {
        try {
          toAdd.add(realmResource.roles().get(desiredName).toRepresentation());
        } catch (jakarta.ws.rs.NotFoundException e) {
          logger.warn("Realm Role '{}' not found, skipping assignment.", desiredName);
        }
      }
    }

    if (!toRemove.isEmpty()) {
      roleScope.remove(toRemove);
      logger.info("Removed realm roles: {}", toRemove.stream().map(RoleRepresentation::getName).toList());
    }
    if (!toAdd.isEmpty()) {
      roleScope.add(toAdd);
      logger.info("Added realm roles: {}", toAdd.stream().map(RoleRepresentation::getName).toList());
    }
  }

  private void syncClientRoles(Map<String, List<String>> clientRolesMap, RoleMappingResource roleMappingResource, RealmResource realmResource) {
    if (clientRolesMap == null || clientRolesMap.isEmpty()) {
      return;
    }

    for (Map.Entry<String, List<String>> entry : clientRolesMap.entrySet()) {
      String clientId = entry.getKey();
      List<String> desiredRoles = entry.getValue();

      List<ClientRepresentation> clients = realmResource.clients().findByClientId(clientId);
      if (clients.isEmpty()) {
        logger.warn("Client '{}' not found, skipping role sync.", clientId);
        continue;
      }
      String clientUuid = clients.getFirst().getId();
      var clientRoleScope = roleMappingResource.clientLevel(clientUuid);

      List<RoleRepresentation> currentRoles = clientRoleScope.listAll();

      List<RoleRepresentation> toRemove = currentRoles.stream()
              .filter(r -> !desiredRoles.contains(r.getName()))
              .toList();

      List<RoleRepresentation> toAdd = new java.util.ArrayList<>();
      for (String desiredName : desiredRoles) {
        boolean alreadyHas = currentRoles.stream().anyMatch(c -> c.getName().equals(desiredName));
        if (!alreadyHas) {
          try {
            toAdd.add(realmResource.clients().get(clientUuid).roles().get(desiredName).toRepresentation());
          } catch (jakarta.ws.rs.NotFoundException e) {
            logger.warn("Role '{}' not found for client '{}'", desiredName, clientId);
          }
        }
      }

      if (!toRemove.isEmpty()) {
        clientRoleScope.remove(toRemove);
        logger.info("Removed roles for client {}: {}", clientId, toRemove.stream().map(RoleRepresentation::getName).toList());
      }
      if (!toAdd.isEmpty()) {
        clientRoleScope.add(toAdd);
        logger.info("Added roles for client {}: {}", clientId, toAdd.stream().map(RoleRepresentation::getName).toList());
      }
    }
  }

  private void validateResponse(String operationDescription, int expectedStatus, Response response)
          throws KeycloakOperationException {
    if (response.getStatus() != expectedStatus) {
      String errorBody = response.readEntity(String.class);
      throw new KeycloakOperationException(
              operationDescription + " failed with status " + response.getStatus() + ": " + errorBody);
    }
  }

  /** Exception thrown when a Keycloak operation fails. */
  public static class KeycloakOperationException extends Exception {
    public KeycloakOperationException(String message) {
      super(message);
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
