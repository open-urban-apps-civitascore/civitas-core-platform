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
package com.civitas.configadapter.keycloak;

import com.civitas.configadapter.adapter.AbstractConfigAdapter;
import com.civitas.configadapter.configuration.AdapterConfig;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.Operation;
import com.civitas.configadapter.model.idm.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.representations.idm.*;
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
    if (event == null) {
      logger.warn("Null event send to topic {}", topic);
      return;
    }
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
      // Use semantic fields: targetComponent is the resource type, targetResource is the realm
      String realm = targetResource;
      String resourceType = targetComponent;
      String resourceId = extractResourceId(event, realm);

      ResourceInfo resourceInfo = new ResourceInfo(resourceType, realm, resourceId);

      logger.debug(
          "Resource info - Type: {}, Realm: {}, ID: {}",
          resourceInfo.type,
          resourceInfo.realm,
          resourceInfo.id);

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
   * Extracts the resource ID from the config value for UPDATE/DELETE operations. For CREATE
   * operations, the ID may be null and will be assigned by Keycloak.
   *
   * <p>Different Keycloak resources use different identifiers:
   *
   * <ul>
   *   <li>Users/Clients: UUID (e.g., "06702f15-1439-4958-b069-2ac5716c7a5c")
   *   <li>Roles: Name (e.g., "admin") - roles are accessed by name in Keycloak API
   *   <li>Realms: Realm name (e.g., "civitas-core")
   * </ul>
   *
   * @param event the configuration value containing the resource data
   * @return the resource ID if present, null otherwise
   */
  private String extractResourceId(ConfigEvent event, String realm) {
    // Extract ID from IDM config values (UserConfig, ClientConfig, RoleConfig, RealmConfig,
    // GroupConfig)
    return switch (event.payload().config().value()) {
      case null -> null;
      case UserConfig userConfig -> userConfig.getId(); // UUID for user
      case ClientConfig clientConfig -> clientConfig.getId(); // UUID for clients
      case RoleConfig roleConfig -> roleConfig.getName(); // Name for roles
      case RealmConfig realmConfig -> realmConfig.getRealm(); // Realm name
      case GroupConfig groupConfig -> groupConfig.getId(); // UUID for groups
      default -> {
        logger.warn("Unknown ConfigValue class: {}", event.getClass());
        publishErrorResult(
            event, "UNKNOWN_RESOURCE_TYPE", "Unknown ConfigValue class: " + event.getClass());
        yield null;
      }
    };
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

      RoleMappingResource roleMapping = realmResource.users().get(userId).roles();
      Set<String> realmRolesSet = rolesToAssignNames != null
              ? new HashSet<>(rolesToAssignNames)
              : Collections.emptySet();
      syncRealmRoles(realmRolesSet, roleMapping, realmResource);
      syncClientRoles(clientRolesMap, roleMapping, realmResource);

      logger.info("Created user: {} (ID: {}) in realm: {}", userRep.getUsername(), userId, realm);
      publishSuccessResult(event, "User created successfully", userId);

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

      RoleMappingResource roleMapping = realmResource.users().get(userId).roles();
      Set<String> realmRolesSet = rolesToAssignNames != null
              ? new HashSet<>(rolesToAssignNames)
              : Collections.emptySet();
      syncRealmRoles(realmRolesSet, roleMapping, realmResource);
      syncClientRoles(clientRolesMap, roleMapping, realmResource);

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

  // ============== GROUP OPERATIONS ==============

  private void createGroup(String realm, ConfigEvent event) {
    try {
      Object configData = extractConfigData(event.payload().config().value());
      GroupRepresentation groupRep =
          objectMapper.convertValue(configData, GroupRepresentation.class);

      // Extract roles to assign after group creation
      GroupConfig groupConfig = (GroupConfig) event.payload().config().value();
      Set<String> realmRolesToAssign = groupConfig.getRealmRoles();
      Map<String, List<String>> clientRolesToAssign = groupConfig.getClientRoles();

      RealmResource realmResource = keycloakClient.realm(realm);

      String groupId;
      // Check if this is a subgroup (has parentId)
      if (groupConfig.getParentId() != null && !groupConfig.getParentId().isEmpty()) {
        // Create as subgroup under parent
        try (Response response =
            realmResource.groups().group(groupConfig.getParentId()).subGroup(groupRep)) {
          validateResponse("Group creation", 201, response);
          groupId = CreatedResponseUtil.getCreatedId(response);
        }
      } else {
        // Create as top-level group
        try (Response response = realmResource.groups().add(groupRep)) {
          validateResponse("Group creation", 201, response);
          groupId = CreatedResponseUtil.getCreatedId(response);
        }
      }

      RoleMappingResource roleMapping = realmResource.groups().group(groupId).roles();

      // Assign realm roles to the group
      syncRealmRoles(realmRolesToAssign, roleMapping, realmResource);

      // Assign client roles to the group
      syncClientRoles(clientRolesToAssign, roleMapping, realmResource);

      logger.info("Created group: {} (ID: {}) in realm: {}", groupRep.getName(), groupId, realm);
      publishSuccessResult(event, "Group created successfully", groupId);

    } catch (Exception e) {
      logger.error("Failed to create group in realm: {}", realm, e);
      publishErrorResult(event, "GROUP_CREATE_FAILED", e.getMessage());
    }
  }

  private void updateGroup(String realm, String groupId, ConfigEvent event) {
    try {
      Object configData = extractConfigData(event.payload().config().value());
      GroupRepresentation groupRep =
          objectMapper.convertValue(configData, GroupRepresentation.class);

      // Extract roles to assign after group update
      GroupConfig groupConfig = (GroupConfig) event.payload().config().value();
      Set<String> realmRolesToAssign = groupConfig.getRealmRoles();
      Map<String, List<String>> clientRolesToAssign = groupConfig.getClientRoles();

      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.groups().group(groupId).update(groupRep);
      RoleMappingResource roleMapping = realmResource.groups().group(groupId).roles();

      // Update realm roles for the group
      syncRealmRoles(realmRolesToAssign, roleMapping, realmResource);

      // Update client roles for the group
      syncClientRoles(clientRolesToAssign, roleMapping, realmResource);

      logger.info("Updated group: {} in realm: {}", groupId, realm);
      publishSuccessResult(event, "Group updated successfully", groupId);

    } catch (Exception e) {
      logger.error("Failed to update group: {} in realm: {}", groupId, realm, e);
      publishErrorResult(event, "GROUP_UPDATE_FAILED", e.getMessage());
    }
  }

  private void deleteGroup(String realm, String groupId, ConfigEvent event) {
    try {
      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.groups().group(groupId).remove();

      logger.info("Deleted group: {} from realm: {}", groupId, realm);
      publishSuccessResult(event, "Group deleted successfully", groupId);

    } catch (Exception e) {
      logger.error("Failed to delete group: {} from realm: {}", groupId, realm, e);
      publishErrorResult(event, "GROUP_DELETE_FAILED", e.getMessage());
    }
  }

  private void syncRealmRoles(
      Set<String> desiredRoleNames,
      RoleMappingResource roleMappingResource,
      RealmResource realmResource) {
    Set<String> desired =
        desiredRoleNames != null ? desiredRoleNames : java.util.Collections.emptySet();
    var roleScope = roleMappingResource.realmLevel();

    List<RoleRepresentation> currentRoles = roleScope.listAll();

    Set<String> currentRoleNames =
        currentRoles.stream().map(RoleRepresentation::getName).collect(Collectors.toSet());

    List<RoleRepresentation> toRemove =
        currentRoles.stream().filter(current -> !desired.contains(current.getName())).toList();

    List<RoleRepresentation> toAdd = new ArrayList<>();
    for (String desiredName : desired) {
      if (!currentRoleNames.contains(desiredName)) {
        try {
          toAdd.add(realmResource.roles().get(desiredName).toRepresentation());
        } catch (jakarta.ws.rs.NotFoundException e) {
          logger.warn("Realm Role '{}' not found, skipping assignment.", desiredName);
        }
      }
    }

    if (!toRemove.isEmpty()) {
      roleScope.remove(toRemove);
      logger.info(
          "Removed realm roles: {}", toRemove.stream().map(RoleRepresentation::getName).toList());
    }
    if (!toAdd.isEmpty()) {
      roleScope.add(toAdd);
      logger.info(
          "Added realm roles: {}", toAdd.stream().map(RoleRepresentation::getName).toList());
    }
  }

  private void syncClientRoles(
      Map<String, List<String>> clientRolesMap,
      RoleMappingResource roleMappingResource,
      RealmResource realmResource) {
    if (clientRolesMap == null || clientRolesMap.isEmpty()) {
      return;
    }

    for (Map.Entry<String, List<String>> entry : clientRolesMap.entrySet()) {
      String clientId = entry.getKey();
      Set<String> desiredRolesSet = new java.util.HashSet<>(entry.getValue());

      List<ClientRepresentation> clients = realmResource.clients().findByClientId(clientId);
      if (clients.isEmpty()) {
        logger.warn("Client '{}' not found, skipping role sync.", clientId);
        continue;
      }
      String clientUuid = clients.getFirst().getId();
      var clientRoleScope = roleMappingResource.clientLevel(clientUuid);

      List<RoleRepresentation> currentRoles = clientRoleScope.listAll();

      Set<String> currentRoleNames =
          currentRoles.stream()
              .map(RoleRepresentation::getName)
              .collect(java.util.stream.Collectors.toSet());

      List<RoleRepresentation> toRemove =
          currentRoles.stream().filter(r -> !desiredRolesSet.contains(r.getName())).toList();

      List<RoleRepresentation> toAdd = new java.util.ArrayList<>();
      for (String desiredName : desiredRolesSet) {
        if (!currentRoleNames.contains(desiredName)) {
          try {
            toAdd.add(
                realmResource
                    .clients()
                    .get(clientUuid)
                    .roles()
                    .get(desiredName)
                    .toRepresentation());
          } catch (jakarta.ws.rs.NotFoundException e) {
            logger.warn("Role '{}' not found for client '{}'", desiredName, clientId);
          }
        }
      }

      if (!toRemove.isEmpty()) {
        clientRoleScope.remove(toRemove);
        logger.info(
            "Removed roles for client {}: {}",
            clientId,
            toRemove.stream().map(RoleRepresentation::getName).toList());
      }
      if (!toAdd.isEmpty()) {
        clientRoleScope.add(toAdd);
        logger.info(
            "Added roles for client {}: {}",
            clientId,
            toAdd.stream().map(RoleRepresentation::getName).toList());
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
