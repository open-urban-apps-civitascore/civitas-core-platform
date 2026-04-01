/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.adapter.AbstractConfigAdapter;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.idm.ClientConfig;
import de.civitascore.configadapter.model.idm.GroupConfig;
import de.civitascore.configadapter.model.idm.IdmConfigValue;
import de.civitascore.configadapter.model.idm.RealmConfig;
import de.civitascore.configadapter.model.idm.RoleConfig;
import de.civitascore.configadapter.model.idm.UserConfig;
import de.civitascore.configadapter.util.PiiMaskingUtil;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keycloak adapter that processes configuration messages and manages Keycloak resources. Supports
 * CREATE, UPDATE, and DELETE operations for realms, clients, and users.
 *
 * <p>Error handling:
 *
 * <ul>
 *   <li>Network errors (ProcessingException) → RetryableAdapterException
 *   <li>HTTP 5xx errors → RetryableAdapterException
 *   <li>HTTP 4xx errors → FatalAdapterException
 *   <li>NotFoundException on DELETE → success (idempotent: resource already absent)
 *   <li>NotFoundException on UPDATE → FatalAdapterException (RESOURCE_NOT_FOUND)
 *   <li>HTTP 409 Conflict on CREATE → success (idempotent: resource already present)
 *   <li>HTTP 409 Conflict on UPDATE → FatalAdapterException (KEYCLOAK_CONFLICT)
 * </ul>
 */
public class KeycloakAdapter extends AbstractConfigAdapter {

  private static final String KEYCLOAK_SOURCE = "de.civitascore.config-adapter.keycloak";

  private static final String DEFAULT_SERVER_URL = "http://localhost:8080";

  private static final String CLIENT_ID_PROPERTY_KEY = "client.id";
  private static final String PASSWORD_PROPERTY_KEY = "password";
  private static final String USERNAME_PROPERTY_KEY = "username";

  // Properties for invitation email redirect (execute-actions-email with client_id + redirect_uri)
  private static final String INVITATION_CLIENT_ID_PROPERTY_KEY = "invitation.client.id";
  private static final String INVITATION_REDIRECT_URI_PROPERTY_KEY = "invitation.redirect.uri";

  private static final String REALM = "realm";
  private static final String URL_PROPERTY = "url";

  private static final Logger logger = LoggerFactory.getLogger(KeycloakAdapter.class);

  public static final String ADAPTER_NAME = "keycloak";

  private final ObjectMapper objectMapper;
  private Keycloak keycloakClient;

  // Optional invitation redirect configuration for execute-actions-email
  private String invitationClientId;
  private String invitationRedirectUri;

  public KeycloakAdapter() {
    this.objectMapper = new ObjectMapper();
    // Configure to ignore the "resourceType" field when converting to Keycloak representations
    this.objectMapper.configure(
        com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  /*
   * (non-Javadoc)
   * @see de.civitascore.configadapter.adapter.AbstractConfigAdapter#initialize(de.civitascore.configadapter.config.AppConfig)
   */
  @Override
  public void initialize(AdapterConfig config) {
    super.initialize(config);
    this.keycloakClient =
        KeycloakBuilder.builder()
            .serverUrl(getAdapterProperty(URL_PROPERTY, DEFAULT_SERVER_URL))
            .realm(getAdapterProperty(REALM, "master"))
            .username(getAdapterProperty(USERNAME_PROPERTY_KEY, "admin"))
            .password(getAdapterProperty(PASSWORD_PROPERTY_KEY, "admin"))
            .clientId(getAdapterProperty(CLIENT_ID_PROPERTY_KEY, "admin-cli"))
            .build();

    // Verify Keycloak credentials are valid by making a test API call.
    validateKeycloakConnection();

    this.invitationClientId = getAdapterProperty(INVITATION_CLIENT_ID_PROPERTY_KEY);
    this.invitationRedirectUri = getAdapterProperty(INVITATION_REDIRECT_URI_PROPERTY_KEY);
    validateInvitationConfig();

    logger.info(
        "Keycloak adapter '{}' initialized for: {}",
        Encode.forJava(getName()),
        Encode.forJava(getAdapterProperty(URL_PROPERTY, DEFAULT_SERVER_URL)));
    if (invitationClientId != null && invitationRedirectUri != null) {
      logger.info(
          "Invitation redirect configured: clientId={}, redirectUri={}",
          Encode.forJava(invitationClientId),
          Encode.forJava(invitationRedirectUri));
    }
    logger.info(
        "Subscribed to {} Kafka topics: {}",
        getSubscribedTopics().size(),
        Encode.forJava(String.valueOf(getSubscribedTopics())));
  }

  /**
   * Validates that the Keycloak admin credentials are correct by making a test API call. Fails fast
   * at startup if the adapter cannot authenticate, rather than silently failing on every event.
   *
   * @throws RuntimeException if the connection or authentication fails
   */
  private void validateKeycloakConnection() {
    try {
      keycloakClient.serverInfo().getInfo();
      logger.info("Keycloak connection validated successfully");
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to connect to Keycloak or authenticate. "
              + "Please verify the Keycloak URL and admin credentials are correct.",
          e);
    }
  }

  /**
   * Validates that the invitation redirect configuration is consistent. Both {@code
   * invitation.client.id} and {@code invitation.redirect.uri} must be set together or neither
   * should be set.
   *
   * @throws IllegalArgumentException if only one of the two properties is set
   */
  private void validateInvitationConfig() {
    boolean hasClientId = invitationClientId != null && !invitationClientId.isBlank();
    boolean hasRedirectUri = invitationRedirectUri != null && !invitationRedirectUri.isBlank();
    if (hasClientId != hasRedirectUri) {
      throw new IllegalArgumentException(
          "Both 'keycloak.invitation.client.id' and 'keycloak.invitation.redirect.uri' "
              + "must be set together or neither should be set. "
              + "invitation.client.id="
              + invitationClientId
              + ", invitation.redirect.uri="
              + invitationRedirectUri);
    }
  }

  /*
   * (non-Javadoc)
   * @see de.civitascore.configadapter.adapter.ConfigAdapter#getName()
   */
  @Override
  public String getName() {
    return ADAPTER_NAME;
  }

  @Override
  protected void doProcessConfigEvent(String topic, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    if (event == null) {
      logger.warn("Null event send to topic {}", Encode.forJava(topic));
      return;
    }
    Operation operation = event.payload().operation();
    String targetResource = event.payload().targetResource();
    String targetComponent = event.payload().targetComponent();

    logger.info(
        "Processing config event - Topic: {}, Operation: {}, TargetComponent: {}, TargetResource: {}",
        Encode.forJava(topic),
        operation,
        Encode.forJava(String.valueOf(targetComponent)),
        Encode.forJava(String.valueOf(targetResource)));

    // Use semantic fields: targetComponent is the resource type, targetResource is the realm
    String resourceId = extractResourceId(event);

    ResourceInfo resourceInfo =
        new ResourceInfo(ResourceType.fromString(targetComponent), targetResource, resourceId);

    logger.debug(
        "Resource info - Type: {}, Realm: {}, ID: {}",
        resourceInfo.type,
        Encode.forJava(String.valueOf(resourceInfo.realm)),
        Encode.forJava(String.valueOf(resourceInfo.id)));

    switch (operation) {
      case CREATE -> handleCreate(resourceInfo, event);
      case UPDATE -> handleUpdate(resourceInfo, event);
      case DELETE -> handleDelete(resourceInfo, event);
      default -> {
        logger.warn("Unknown operation: {}", operation);
        throw new FatalAdapterException(AdapterErrorCode.UNSUPPORTED_OPERATION, operation);
      }
    }
  }

  @Override
  protected String getResultType() {
    return IdmConfigValue.IDM_RESULT_TYPE;
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
  private String extractResourceId(ConfigEvent event) throws FatalAdapterException {
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
        throw new FatalAdapterException(
            AdapterErrorCode.INVALID_RESOURCE_TYPE, event.getClass().getSimpleName());
      }
    };
  }

  private void handleCreate(ResourceInfo resourceInfo, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    switch (resourceInfo.type) {
      case REALM -> createRealm(event);
      case CLIENT -> createClient(resourceInfo.realm, event);
      case USER -> createUser(resourceInfo.realm, event);
      case ROLE -> createRole(resourceInfo.realm, event);
      case GROUP -> createGroup(resourceInfo.realm, event);
      default -> {
        logger.warn("Unknown resource type for create: {}", resourceInfo.type);
        throw new FatalAdapterException(AdapterErrorCode.INVALID_RESOURCE_TYPE, resourceInfo.type);
      }
    }
  }

  private void handleUpdate(ResourceInfo resourceInfo, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    switch (resourceInfo.type) {
      case REALM -> updateRealm(resourceInfo.id, event);
      case CLIENT -> updateClient(resourceInfo.realm, resourceInfo.id, event);
      case USER -> updateUser(resourceInfo.realm, resourceInfo.id, event);
      case ROLE -> updateRole(resourceInfo.realm, resourceInfo.id, event);
      case GROUP -> updateGroup(resourceInfo.realm, resourceInfo.id, event);
      default -> {
        logger.warn("Unknown resource type for update: {}", resourceInfo.type);
        throw new FatalAdapterException(AdapterErrorCode.INVALID_RESOURCE_TYPE, resourceInfo.type);
      }
    }
  }

  private void handleDelete(ResourceInfo resourceInfo, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    switch (resourceInfo.type) {
      case REALM -> deleteRealm(resourceInfo.id, event);
      case CLIENT -> deleteClient(resourceInfo.realm, resourceInfo.id, event);
      case USER -> deleteUser(resourceInfo.realm, resourceInfo.id, event);
      case ROLE -> deleteRole(resourceInfo.realm, resourceInfo.id, event);
      case GROUP -> deleteGroup(resourceInfo.realm, resourceInfo.id, event);
      default -> {
        logger.warn("Unknown resource type for delete: {}", resourceInfo.type);
        throw new FatalAdapterException(AdapterErrorCode.INVALID_RESOURCE_TYPE, resourceInfo.type);
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
  private Object extractConfigData(de.civitascore.configadapter.model.ConfigValue configValue) {
    // With concrete POJOs, we can pass them directly to ObjectMapper
    // which will map the fields to Keycloak representations
    return configValue;
  }

  private void createRealm(ConfigEvent event)
      throws RetryableAdapterException, FatalAdapterException {
    try {
      Object configData = extractConfigData(event.payload().config().value());
      RealmRepresentation realmRep =
          objectMapper.convertValue(configData, RealmRepresentation.class);

      keycloakClient.realms().create(realmRep);

      String realmName = realmRep.getRealm();
      logger.info("Created realm: {}", Encode.forJava(realmName));

      publishSuccessResult(event, SuccessCode.REALM_CREATE_SUCCESS, realmName);

    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.REALM_CREATION);
    } catch (WebApplicationException e) {
      if (e.getResponse().getStatus() == 409) {
        String realmName = ((RealmConfig) event.payload().config().value()).getRealm();
        logger.info(
            "Realm {} already exists, treating create as success", Encode.forJava(realmName));
        publishSuccessResult(event, SuccessCode.REALM_CREATE_SUCCESS, realmName);
        return;
      }
      wrapWebException(e, KeycloakOperation.REALM_CREATION, AdapterErrorCode.KEYCLOAK_REALM_ERROR);
    }
  }

  private void updateRealm(String realmName, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      Object configData = extractConfigData(event.payload().config().value());
      RealmRepresentation realmRep =
          objectMapper.convertValue(configData, RealmRepresentation.class);

      RealmResource realmResource = keycloakClient.realm(realmName);
      realmResource.update(realmRep);

      logger.info("Updated realm: {}", Encode.forJava(realmName));
      publishSuccessResult(event, SuccessCode.REALM_UPDATE_SUCCESS, realmName);

    } catch (NotFoundException e) {
      throw new FatalAdapterException(AdapterErrorCode.RESOURCE_NOT_FOUND, e, "Realm " + realmName);
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.REALM_UPDATE);
    } catch (WebApplicationException e) {
      wrapWebException(e, KeycloakOperation.REALM_UPDATE, AdapterErrorCode.KEYCLOAK_REALM_ERROR);
    }
  }

  private void deleteRealm(String realmName, ConfigEvent event)
      throws RetryableAdapterException, FatalAdapterException {
    try {
      keycloakClient.realm(realmName).remove();
      logger.info("Deleted realm: {}", Encode.forJava(realmName));
      publishSuccessResult(event, SuccessCode.REALM_DELETE_SUCCESS, realmName);

    } catch (NotFoundException e) {
      logger.info(
          "Realm {} not found during delete, treating as success (already deleted)",
          Encode.forJava(realmName));
      publishSuccessResult(event, SuccessCode.REALM_DELETE_SUCCESS, realmName);
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.REALM_DELETION);
    } catch (WebApplicationException e) {
      wrapWebException(e, KeycloakOperation.REALM_DELETION, AdapterErrorCode.KEYCLOAK_REALM_ERROR);
    }
  }

  // ============== CLIENT OPERATIONS ==============

  private void createClient(String realm, ConfigEvent event)
      throws RetryableAdapterException, FatalAdapterException {
    try {
      Object configData = extractConfigData(event.payload().config().value());
      ClientRepresentation clientRep =
          objectMapper.convertValue(configData, ClientRepresentation.class);

      RealmResource realmResource = keycloakClient.realm(realm);

      try (Response response = realmResource.clients().create(clientRep)) {
        if (response.getStatus() == 409) {
          logger.info(
              "Client {} already exists, treating create as success",
              Encode.forJava(clientRep.getClientId()));
          publishSuccessResult(event, SuccessCode.CLIENT_CREATE_SUCCESS, clientRep.getClientId());
          return;
        }
        validateResponse(KeycloakOperation.CLIENT_CREATION, 201, response);
      }

      logger.info(
          "Created client: {} in realm: {}",
          Encode.forJava(clientRep.getClientId()),
          Encode.forJava(realm));
      publishSuccessResult(event, SuccessCode.CLIENT_CREATE_SUCCESS, clientRep.getClientId());

    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.CLIENT_CREATION);
    } catch (WebApplicationException e) {
      wrapWebException(
          e, KeycloakOperation.CLIENT_CREATION, AdapterErrorCode.KEYCLOAK_CLIENT_ERROR);
    } catch (KeycloakOperationException e) {
      throw new FatalAdapterException(AdapterErrorCode.KEYCLOAK_CLIENT_ERROR, e, e.getMessage());
    }
  }

  private void updateClient(String realm, String clientId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      Object configData = extractConfigData(event.payload().config().value());
      ClientRepresentation clientRep =
          objectMapper.convertValue(configData, ClientRepresentation.class);

      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.clients().get(clientId).update(clientRep);

      logger.info(
          "Updated client: {} in realm: {}", Encode.forJava(clientId), Encode.forJava(realm));
      publishSuccessResult(event, SuccessCode.CLIENT_UPDATE_SUCCESS, clientId);

    } catch (NotFoundException e) {
      throw new FatalAdapterException(
          AdapterErrorCode.RESOURCE_NOT_FOUND, e, "Client " + maskId(clientId));
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.CLIENT_UPDATE);
    } catch (WebApplicationException e) {
      wrapWebException(e, KeycloakOperation.CLIENT_UPDATE, AdapterErrorCode.KEYCLOAK_CLIENT_ERROR);
    }
  }

  private void deleteClient(String realm, String clientId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.clients().get(clientId).remove();

      logger.info(
          "Deleted client: {} from realm: {}", Encode.forJava(clientId), Encode.forJava(realm));
      publishSuccessResult(event, SuccessCode.USER_DELETE_SUCCESS, clientId);

    } catch (NotFoundException e) {
      logger.info(
          "Client {} not found during delete, treating as success (already deleted)",
          Encode.forJava(maskId(clientId)));
      publishSuccessResult(event, SuccessCode.USER_DELETE_SUCCESS, clientId);
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.CLIENT_DELETION);
    } catch (WebApplicationException e) {
      wrapWebException(
          e, KeycloakOperation.CLIENT_DELETION, AdapterErrorCode.KEYCLOAK_CLIENT_ERROR);
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

  private void createUser(String realm, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    executeUserFlow(
        realm,
        event,
        KeycloakOperation.USER_CREATION,
        SuccessCode.USER_CREATE_SUCCESS,
        AdapterErrorCode.KEYCLOAK_USER_ERROR,
        (realmResource, userRep) -> {
          try (Response response = realmResource.users().create(userRep)) {
            if (response.getStatus() == 409) {
              logger.info(
                  "User {} already exists, treating create as success and syncing roles",
                  Encode.forJava(userRep.getUsername()));
              List<UserRepresentation> existing =
                  realmResource.users().searchByUsername(userRep.getUsername(), true);
              return existing.isEmpty() ? null : existing.getFirst().getId();
            }
            validateResponse(KeycloakOperation.USER_CREATION, 201, response);
            String userId = CreatedResponseUtil.getCreatedId(response);
            sendActionsEmail(realmResource, userId, userRep.getRequiredActions());
            return userId;
          }
        });
  }

  /**
   * Sends an actions email to a newly created user if required actions are configured. The email
   * contains a link for the user to complete actions like email verification and password setup.
   *
   * @throws Exception if invitation redirect is configured and the email sending fails
   */
  private void sendActionsEmail(
      RealmResource realmResource, String userId, List<String> requiredActions) throws Exception {
    if (requiredActions == null || requiredActions.isEmpty()) {
      return;
    }
    if (invitationClientId != null && invitationRedirectUri != null) {
      realmResource
          .users()
          .get(userId)
          .executeActionsEmail(invitationClientId, invitationRedirectUri, requiredActions);
    } else {
      try {
        realmResource.users().get(userId).executeActionsEmail(requiredActions);
        logger.info(
            "Sent actions email to user {} for actions: {}",
            Encode.forJava(maskId(userId)),
            Encode.forJava(String.valueOf(requiredActions)));
      } catch (Exception e) {
        logger.warn(
            "Failed to send actions email for user {}: {}",
            Encode.forJava(maskId(userId)),
            Encode.forJava(String.valueOf(e.getMessage())));
      }
    }
  }

  private void updateUser(String realm, String userId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    executeUserFlow(
        realm,
        event,
        KeycloakOperation.USER_UPDATE,
        SuccessCode.USER_UPDATE_SUCCESS,
        AdapterErrorCode.KEYCLOAK_USER_ERROR,
        (realmResource, userRep) -> {
          realmResource.users().get(userId).update(userRep);
          return userId;
        });
  }

  private void executeUserFlow(
      String realm,
      ConfigEvent event,
      KeycloakOperation operation,
      SuccessCode successCode,
      AdapterErrorCode errorCode,
      UserAction action)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      Object configData = extractConfigData(event.payload().config().value());
      UserRepresentation userRep = objectMapper.convertValue(configData, UserRepresentation.class);

      List<String> realmRolesToAssign = userRep.getRealmRoles();
      Map<String, List<String>> clientRolesToAssign = userRep.getClientRoles();
      userRep.setRealmRoles(null);
      userRep.setClientRoles(null);

      RealmResource realmResource = keycloakClient.realm(realm);

      String userId = action.apply(realmResource, userRep);

      RoleMappingResource roleMapping = realmResource.users().get(userId).roles();
      Set<String> realmRolesSet =
          realmRolesToAssign != null ? new HashSet<>(realmRolesToAssign) : Collections.emptySet();

      syncRealmRoles(realmRolesSet, roleMapping, realmResource);
      syncClientRoles(clientRolesToAssign, roleMapping, realmResource);

      logger.info(
          "{} (ID: {}) in realm: {}",
          successCode.toString(),
          Encode.forJava(maskId(userId)),
          Encode.forJava(realm));
      publishSuccessResult(event, successCode, userId);

    } catch (NotFoundException e) {
      throw new FatalAdapterException(AdapterErrorCode.RESOURCE_NOT_FOUND, e, "User");
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, operation);
    } catch (WebApplicationException e) {
      wrapWebException(e, operation, errorCode);
    } catch (KeycloakOperationException e) {
      throw new FatalAdapterException(errorCode, e, e.getMessage());
    } catch (FatalAdapterException | RetryableAdapterException e) {
      // Re-throw adapter exceptions for the Kafka handler to process
      throw e;
    } catch (Exception e) {
      logger.error("Failed to process user in realm: {}", Encode.forJava(realm), e);
      throw new FatalAdapterException(errorCode, e, maskPII(e.getMessage()));
    }
  }

  private void deleteUser(String realm, String userId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.users().get(userId).remove();

      logger.info(
          "Deleted user: {} from realm: {}", Encode.forJava(maskId(userId)), Encode.forJava(realm));
      publishSuccessResult(event, SuccessCode.USER_DELETE_SUCCESS, userId);

    } catch (NotFoundException e) {
      logger.info(
          "User {} not found during delete, treating as success (already deleted)",
          Encode.forJava(maskId(userId)));
      publishSuccessResult(event, SuccessCode.USER_DELETE_SUCCESS, userId);
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.USER_DELETION);
    } catch (WebApplicationException e) {
      wrapWebException(e, KeycloakOperation.USER_DELETION, AdapterErrorCode.KEYCLOAK_USER_ERROR);
    }
  }

  // ============== ROLE OPERATIONS ==============

  private void createRole(String realm, ConfigEvent event)
      throws RetryableAdapterException, FatalAdapterException {
    try {
      Object configData = extractConfigData(event.payload().config().value());
      RoleRepresentation roleRep = convertToRoleRepresentation(configData);

      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.roles().create(roleRep);

      logger.info(
          "Created role: {} in realm: {}",
          Encode.forJava(roleRep.getName()),
          Encode.forJava(realm));
      publishSuccessResult(event, SuccessCode.ROLE_CREATE_SUCCESS, roleRep.getName());

    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.ROLE_CREATION);
    } catch (WebApplicationException e) {
      if (e.getResponse().getStatus() == 409) {
        String roleName = ((RoleConfig) event.payload().config().value()).getName();
        logger.info("Role {} already exists, treating create as success", Encode.forJava(roleName));
        publishSuccessResult(event, SuccessCode.ROLE_CREATE_SUCCESS, roleName);
        return;
      }
      wrapWebException(e, KeycloakOperation.ROLE_CREATION, AdapterErrorCode.KEYCLOAK_ROLE_ERROR);
    }
  }

  private void updateRole(String realm, String roleId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      Object configData = extractConfigData(event.payload().config().value());
      RoleRepresentation roleRep = convertToRoleRepresentation(configData);

      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.roles().get(roleId).update(roleRep);

      logger.info("Updated role: {} in realm: {}", Encode.forJava(roleId), Encode.forJava(realm));
      publishSuccessResult(event, SuccessCode.ROLE_UPDATE_SUCCESS, roleId);

    } catch (NotFoundException e) {
      throw new FatalAdapterException(AdapterErrorCode.RESOURCE_NOT_FOUND, e, "Role " + roleId);
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.ROLE_UPDATE);
    } catch (WebApplicationException e) {
      wrapWebException(e, KeycloakOperation.ROLE_UPDATE, AdapterErrorCode.KEYCLOAK_ROLE_ERROR);
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
    if (configData instanceof de.civitascore.configadapter.model.idm.RoleConfig roleConfig) {
      if (roleConfig.getCompositeRoles() != null && !roleConfig.getCompositeRoles().isEmpty()) {
        // Create Keycloak's Composites structure from our simple Set<String>
        RoleRepresentation.Composites composites = new RoleRepresentation.Composites();
        composites.setRealm(roleConfig.getCompositeRoles());
        roleRep.setComposites(composites);
      }
    }

    return roleRep;
  }

  private void deleteRole(String realm, String roleId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.roles().get(roleId).remove();

      logger.info("Deleted role: {} from realm: {}", Encode.forJava(roleId), Encode.forJava(realm));
      publishSuccessResult(event, SuccessCode.ROLE_DELETE_SUCCESS, roleId);

    } catch (NotFoundException e) {
      logger.info(
          "Role {} not found during delete, treating as success (already deleted)",
          Encode.forJava(roleId));
      publishSuccessResult(event, SuccessCode.ROLE_DELETE_SUCCESS, roleId);
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.ROLE_DELETION);
    } catch (WebApplicationException e) {
      wrapWebException(e, KeycloakOperation.ROLE_DELETION, AdapterErrorCode.KEYCLOAK_ROLE_ERROR);
    }
  }

  // ============== GROUP OPERATIONS ==============

  private void createGroup(String realm, ConfigEvent event)
      throws RetryableAdapterException, FatalAdapterException {
    try {
      GroupConfig groupConfig = (GroupConfig) event.payload().config().value();
      GroupRepresentation groupRep =
          objectMapper.convertValue(extractConfigData(groupConfig), GroupRepresentation.class);

      RealmResource realmResource = keycloakClient.realm(realm);

      String groupId = createGroupInKeycloak(realmResource, groupRep, groupConfig.getParentId());

      assignRolesToGroup(
          realmResource, groupId, groupConfig.getRealmRoles(), groupConfig.getClientRoles());

      logger.info(
          "Created group: {} (ID: {}) in realm: {}",
          Encode.forJava(groupRep.getName()),
          Encode.forJava(maskId(groupId)),
          Encode.forJava(realm));
      publishSuccessResult(event, SuccessCode.GROUP_CREATE_SUCCESS, groupId);

    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.GROUP_CREATION);
    } catch (WebApplicationException e) {
      wrapWebException(e, KeycloakOperation.GROUP_CREATION, AdapterErrorCode.KEYCLOAK_GROUP_ERROR);
    } catch (KeycloakOperationException e) {
      throw new FatalAdapterException(AdapterErrorCode.KEYCLOAK_GROUP_ERROR, e, e.getMessage());
    }
  }

  private String createGroupInKeycloak(
      RealmResource realmResource, GroupRepresentation groupRep, String parentId)
      throws KeycloakOperationException {
    if (parentId != null && !parentId.isEmpty()) {
      try (Response response = realmResource.groups().group(parentId).subGroup(groupRep)) {
        if (response.getStatus() == 409) {
          return findExistingGroupId(realmResource, groupRep.getName());
        }
        validateResponse(KeycloakOperation.GROUP_CREATION, 201, response);
        return CreatedResponseUtil.getCreatedId(response);
      }
    } else {
      try (Response response = realmResource.groups().add(groupRep)) {
        if (response.getStatus() == 409) {
          return findExistingGroupId(realmResource, groupRep.getName());
        }
        validateResponse(KeycloakOperation.GROUP_CREATION, 201, response);
        return CreatedResponseUtil.getCreatedId(response);
      }
    }
  }

  private String findExistingGroupId(RealmResource realmResource, String groupName) {
    logger.info("Group {} already exists, treating create as success", Encode.forJava(groupName));
    return realmResource.groups().groups(groupName, 0, 1).stream()
        .filter(g -> g.getName().equals(groupName))
        .map(GroupRepresentation::getId)
        .findFirst()
        .orElse(null);
  }

  private void assignRolesToGroup(
      RealmResource realmResource,
      String groupId,
      Set<String> realmRoles,
      Map<String, List<String>> clientRoles) {
    RoleMappingResource roleMapping = realmResource.groups().group(groupId).roles();
    syncRealmRoles(realmRoles, roleMapping, realmResource);
    syncClientRoles(clientRoles, roleMapping, realmResource);
  }

  private void updateGroup(String realm, String groupId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      GroupConfig groupConfig = (GroupConfig) event.payload().config().value();
      GroupRepresentation groupRep =
          objectMapper.convertValue(extractConfigData(groupConfig), GroupRepresentation.class);

      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.groups().group(groupId).update(groupRep);

      assignRolesToGroup(
          realmResource, groupId, groupConfig.getRealmRoles(), groupConfig.getClientRoles());

      logger.info(
          "Updated group: {} in realm: {}", Encode.forJava(maskId(groupId)), Encode.forJava(realm));
      publishSuccessResult(event, SuccessCode.GROUP_UPDATE_SUCCESS, groupId);

    } catch (NotFoundException e) {
      throw new FatalAdapterException(
          AdapterErrorCode.RESOURCE_NOT_FOUND, e, "Group " + maskId(groupId));
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.GROUP_UPDATE);
    } catch (WebApplicationException e) {
      wrapWebException(e, KeycloakOperation.GROUP_UPDATE, AdapterErrorCode.KEYCLOAK_GROUP_ERROR);
    }
  }

  private void deleteGroup(String realm, String groupId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.groups().group(groupId).remove();

      logger.info(
          "Deleted group: {} from realm: {}",
          Encode.forJava(maskId(groupId)),
          Encode.forJava(realm));
      publishSuccessResult(event, SuccessCode.GROUP_DELETE_SUCCESS, groupId);

    } catch (NotFoundException e) {
      logger.info(
          "Group {} not found during delete, treating as success (already deleted)",
          Encode.forJava(maskId(groupId)));
      publishSuccessResult(event, SuccessCode.GROUP_DELETE_SUCCESS, groupId);
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.GROUP_DELETION);
    } catch (WebApplicationException e) {
      wrapWebException(e, KeycloakOperation.GROUP_DELETION, AdapterErrorCode.KEYCLOAK_GROUP_ERROR);
    }
  }

  private void syncRealmRoles(
      Set<String> desiredRoleNames,
      RoleMappingResource roleMappingResource,
      RealmResource realmResource) {
    Set<String> desired = desiredRoleNames != null ? desiredRoleNames : Collections.emptySet();
    RoleScopeResource roleScope = roleMappingResource.realmLevel();
    List<RoleRepresentation> currentRoles = roleScope.listAll();

    RoleDiff diff = computeRealmRoleDiff(currentRoles, desired, realmResource);
    applyRoleChanges(roleScope, diff, "realm");
  }

  private RoleDiff computeRealmRoleDiff(
      List<RoleRepresentation> currentRoles,
      Set<String> desiredRoles,
      RealmResource realmResource) {
    Set<String> currentRoleNames =
        currentRoles.stream().map(RoleRepresentation::getName).collect(Collectors.toSet());

    List<RoleRepresentation> toRemove =
        currentRoles.stream().filter(r -> !desiredRoles.contains(r.getName())).toList();

    List<RoleRepresentation> toAdd = new ArrayList<>();
    for (String desiredName : desiredRoles) {
      if (!currentRoleNames.contains(desiredName)) {
        try {
          toAdd.add(realmResource.roles().get(desiredName).toRepresentation());
        } catch (NotFoundException e) {
          logger.warn(
              "Realm Role '{}' not found, skipping assignment.", Encode.forJava(desiredName));
        }
      }
    }
    return new RoleDiff(toAdd, toRemove);
  }

  private void syncClientRoles(
      Map<String, List<String>> clientRolesMap,
      RoleMappingResource roleMappingResource,
      RealmResource realmResource) {
    if (clientRolesMap == null || clientRolesMap.isEmpty()) {
      return;
    }

    for (Map.Entry<String, List<String>> entry : clientRolesMap.entrySet()) {
      syncRolesForClient(
          entry.getKey(), new HashSet<>(entry.getValue()), roleMappingResource, realmResource);
    }
  }

  private void syncRolesForClient(
      String clientId,
      Set<String> desiredRoles,
      RoleMappingResource roleMappingResource,
      RealmResource realmResource) {
    List<ClientRepresentation> clients = realmResource.clients().findByClientId(clientId);
    if (clients.isEmpty()) {
      logger.warn("Client '{}' not found, skipping role sync.", Encode.forJava(clientId));
      return;
    }

    String clientUuid = clients.getFirst().getId();
    RoleScopeResource clientRoleScope = roleMappingResource.clientLevel(clientUuid);
    List<RoleRepresentation> currentRoles = clientRoleScope.listAll();

    RoleDiff diff =
        computeClientRoleDiff(currentRoles, desiredRoles, clientUuid, clientId, realmResource);
    applyRoleChanges(clientRoleScope, diff, clientId);
  }

  private RoleDiff computeClientRoleDiff(
      List<RoleRepresentation> currentRoles,
      Set<String> desiredRoles,
      String clientUuid,
      String clientId,
      RealmResource realmResource) {
    Set<String> currentRoleNames =
        currentRoles.stream().map(RoleRepresentation::getName).collect(Collectors.toSet());

    List<RoleRepresentation> toRemove =
        currentRoles.stream().filter(r -> !desiredRoles.contains(r.getName())).toList();

    List<RoleRepresentation> toAdd = new ArrayList<>();
    for (String desiredName : desiredRoles) {
      if (!currentRoleNames.contains(desiredName)) {
        try {
          toAdd.add(
              realmResource.clients().get(clientUuid).roles().get(desiredName).toRepresentation());
        } catch (NotFoundException e) {
          logger.warn(
              "Role '{}' not found for client '{}'",
              Encode.forJava(desiredName),
              Encode.forJava(clientId));
        }
      }
    }
    return new RoleDiff(toAdd, toRemove);
  }

  private void applyRoleChanges(RoleScopeResource roleScope, RoleDiff diff, String context) {
    if (!diff.toRemove().isEmpty()) {
      roleScope.remove(diff.toRemove());
      logger.info(
          "Removed roles for {}: {}",
          Encode.forJava(context),
          Encode.forJava(
              String.valueOf(diff.toRemove().stream().map(RoleRepresentation::getName).toList())));
    }
    if (!diff.toAdd().isEmpty()) {
      roleScope.add(diff.toAdd());
      logger.info(
          "Added roles for {}: {}",
          Encode.forJava(context),
          Encode.forJava(
              String.valueOf(diff.toAdd().stream().map(RoleRepresentation::getName).toList())));
    }
  }

  private record RoleDiff(List<RoleRepresentation> toAdd, List<RoleRepresentation> toRemove) {}

  private void validateResponse(KeycloakOperation operation, int expectedStatus, Response response)
      throws KeycloakOperationException {
    if (response.getStatus() != expectedStatus) {
      String errorBody = response.readEntity(String.class);
      throw new KeycloakOperationException(
          operation.getDescription()
              + " failed with status "
              + response.getStatus()
              + ": "
              + errorBody);
    }
  }

  // ============== EXCEPTION WRAPPING ==============

  /**
   * Wraps network/processing exceptions as RetryableAdapterException.
   *
   * @param e the ProcessingException (network error)
   * @param operation the operation being performed
   * @return RetryableAdapterException
   */
  private RetryableAdapterException wrapNetworkException(
      ProcessingException e, KeycloakOperation operation) {
    logger.warn(
        "Network error during {}: {}", operation, Encode.forJava(String.valueOf(e.getMessage())));
    return new RetryableAdapterException(
        AdapterErrorCode.NETWORK_ERROR, e, "keycloak", maskPII(e.getMessage()));
  }

  /**
   * Wraps WebApplicationException based on HTTP status code. Throws RetryableAdapterException for
   * 5xx errors (server issues) and FatalAdapterException for 4xx errors (client issues).
   *
   * @param e the WebApplicationException
   * @param operation the operation being performed
   * @param defaultErrorCode the default error code if not a specific case
   * @throws RetryableAdapterException for HTTP 5xx errors
   * @throws FatalAdapterException for HTTP 4xx errors
   */
  private void wrapWebException(
      WebApplicationException e, KeycloakOperation operation, AdapterErrorCode defaultErrorCode)
      throws RetryableAdapterException, FatalAdapterException {
    int status = e.getResponse().getStatus();

    // HTTP 5xx - Server errors are retryable
    if (status >= 500) {
      logger.warn("Keycloak server error during {}: HTTP {}", operation, status);
      throw new RetryableAdapterException(
          AdapterErrorCode.SERVICE_UNAVAILABLE, e, "keycloak", status);
    }

    // HTTP 409 - Conflict (resource already exists)
    if (status == 409) {
      logger.warn("Keycloak conflict during {}: HTTP 409", operation);
      throw new FatalAdapterException(
          AdapterErrorCode.KEYCLOAK_CONFLICT, e, "Resource already exists");
    }

    // HTTP 4xx - Client errors are fatal
    logger.error("Keycloak client error during {}: HTTP {}", operation, status);
    throw new FatalAdapterException(defaultErrorCode, e, "HTTP " + status);
  }

  // ============== PII MASKING ==============

  private String maskId(String id) {
    return PiiMaskingUtil.maskId(id);
  }

  private String maskPII(String message) {
    return PiiMaskingUtil.maskPII(message);
  }

  public static class KeycloakOperationException extends Exception {
    /** serialVersionUID */
    private static final long serialVersionUID = -8977227116700940716L;

    public KeycloakOperationException(String message) {
      super(message);
    }
  }

  // ============== RESULT PUBLISHING ==============

  /**
   * Publishes a success result event to the result topic. Exceptions from the publisher are
   * propagated to be handled by the KafkaEventHandler's retry/DLQ logic.
   *
   * @param originalEvent the original config event
   * @param successCode the success code
   * @param resourceId the created/updated resource ID (may be null)
   */
  private void publishSuccessResult(
      ConfigEvent originalEvent, SuccessCode successCode, String resourceId)
      throws FatalAdapterException, RetryableAdapterException {
    if (getEventPublisher() == null || originalEvent.metadata().resultTopic() == null) {
      return;
    }

    ConfigResultEvent resultEvent =
        ConfigResultEvent.success(
            originalEvent.metadata().correlationId(),
            originalEvent.metadata().messageId(),
            successCode.toString(),
            resourceId,
            originalEvent.payload().operation(),
            originalEvent.payload().targetResource(),
            KEYCLOAK_SOURCE,
            IdmConfigValue.IDM_RESULT_TYPE);

    // Exceptions propagate to KafkaEventHandler for retry/DLQ handling
    getEventPublisher().publish(originalEvent.metadata().resultTopic(), resultEvent);
    logger.debug(
        "Published SUCCESS result to topic: {}",
        Encode.forJava(originalEvent.metadata().resultTopic()));
  }

  @Override
  public void close() {
    if (keycloakClient != null) {
      keycloakClient.close();
      logger.info("Keycloak adapter closed");
    }
  }

  /** Helper record to hold parsed resource information */
  private record ResourceInfo(ResourceType type, String realm, String id) {}
}

@FunctionalInterface
interface UserAction {
  String apply(RealmResource realmResource, UserRepresentation userRep) throws Exception;
}
