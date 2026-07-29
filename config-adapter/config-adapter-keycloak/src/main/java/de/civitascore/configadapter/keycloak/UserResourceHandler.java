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

import static de.civitascore.configadapter.keycloak.KeycloakErrorHandler.maskId;
import static de.civitascore.configadapter.keycloak.KeycloakErrorHandler.maskPII;
import static de.civitascore.configadapter.keycloak.KeycloakErrorHandler.validateResponse;
import static de.civitascore.configadapter.keycloak.KeycloakErrorHandler.wrapNetworkException;
import static de.civitascore.configadapter.keycloak.KeycloakErrorHandler.wrapWebException;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.keycloak.KeycloakErrorHandler.KeycloakOperationException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.ConfigEvent;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles CREATE, UPDATE, and DELETE operations for Keycloak users. */
class UserResourceHandler implements KeycloakResourceHandler {

  @FunctionalInterface
  interface UserAction {
    String apply(RealmResource realmResource, UserRepresentation userRep) throws Exception;
  }

  private static final Logger logger = LoggerFactory.getLogger(UserResourceHandler.class);

  private final Keycloak keycloakClient;
  private final ObjectMapper objectMapper;
  private final ResultPublisher resultPublisher;
  private final RoleSyncHelper roleSyncHelper;
  private final GroupSyncHelper groupSyncHelper;
  private final String invitationClientId;
  private final String invitationRedirectUri;

  UserResourceHandler(
      Keycloak keycloakClient,
      ObjectMapper objectMapper,
      ResultPublisher resultPublisher,
      RoleSyncHelper roleSyncHelper,
      GroupSyncHelper groupSyncHelper,
      String invitationClientId,
      String invitationRedirectUri) {
    this.keycloakClient = keycloakClient;
    this.objectMapper = objectMapper;
    this.resultPublisher = resultPublisher;
    this.roleSyncHelper = roleSyncHelper;
    this.groupSyncHelper = groupSyncHelper;
    this.invitationClientId = invitationClientId;
    this.invitationRedirectUri = invitationRedirectUri;
  }

  @Override
  public void create(String realm, ConfigEvent event)
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

  @Override
  public void update(String realm, String userId, ConfigEvent event)
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

  @Override
  public void delete(String realm, String userId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.users().get(userId).remove();

      logger.info(
          "Deleted user: {} from realm: {}", Encode.forJava(maskId(userId)), Encode.forJava(realm));
      resultPublisher.publish(event, SuccessCode.USER_DELETE_SUCCESS, userId);

    } catch (NotFoundException e) {
      logger.info(
          "User {} not found during delete, treating as success (already deleted)",
          Encode.forJava(maskId(userId)));
      resultPublisher.publish(event, SuccessCode.USER_DELETE_SUCCESS, userId);
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.USER_DELETION);
    } catch (WebApplicationException e) {
      wrapWebException(e, KeycloakOperation.USER_DELETION, AdapterErrorCode.KEYCLOAK_USER_ERROR);
    }
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
      UserRepresentation userRep =
          objectMapper.convertValue(event.payload().config().value(), UserRepresentation.class);

      List<String> realmRolesToAssign = userRep.getRealmRoles();
      Map<String, List<String>> clientRolesToAssign = userRep.getClientRoles();
      List<String> groupsToAssign = userRep.getGroups();
      userRep.setRealmRoles(null);
      userRep.setClientRoles(null);
      userRep.setGroups(null);

      RealmResource realmResource = keycloakClient.realm(realm);

      String userId = action.apply(realmResource, userRep);

      RoleMappingResource roleMapping = realmResource.users().get(userId).roles();
      Set<String> realmRolesSet =
          realmRolesToAssign != null ? new HashSet<>(realmRolesToAssign) : Collections.emptySet();

      roleSyncHelper.syncRealmRoles(realmRolesSet, roleMapping, realmResource);
      roleSyncHelper.syncClientRoles(clientRolesToAssign, roleMapping, realmResource);
      groupSyncHelper.syncUserGroups(groupsToAssign, userId, realmResource);

      logger.info(
          "{} (ID: {}) in realm: {}",
          successCode.toString(),
          Encode.forJava(maskId(userId)),
          Encode.forJava(realm));
      resultPublisher.publish(event, successCode, userId);

    } catch (NotFoundException e) {
      throw new FatalAdapterException(AdapterErrorCode.RESOURCE_NOT_FOUND, e, "User");
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, operation);
    } catch (WebApplicationException e) {
      wrapWebException(e, operation, errorCode);
    } catch (KeycloakOperationException e) {
      throw new FatalAdapterException(errorCode, e, e.getMessage());
    } catch (FatalAdapterException | RetryableAdapterException e) {
      throw e;
    } catch (Exception e) {
      logger.error(
          "Failed to process user in realm: {} ({})",
          Encode.forJava(realm),
          e.getClass().getSimpleName(),
          e);
      throw new FatalAdapterException(errorCode, e, maskPII(e.getMessage()));
    }
  }

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
}
