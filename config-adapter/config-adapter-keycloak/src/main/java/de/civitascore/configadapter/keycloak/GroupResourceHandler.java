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
import static de.civitascore.configadapter.keycloak.KeycloakErrorHandler.validateResponse;
import static de.civitascore.configadapter.keycloak.KeycloakErrorHandler.wrapNetworkException;
import static de.civitascore.configadapter.keycloak.KeycloakErrorHandler.wrapWebException;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.keycloak.KeycloakErrorHandler.KeycloakOperationException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.idm.GroupConfig;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles CREATE, UPDATE, and DELETE operations for Keycloak groups. */
class GroupResourceHandler implements KeycloakResourceHandler {

  private static final Logger logger = LoggerFactory.getLogger(GroupResourceHandler.class);

  private final Keycloak keycloakClient;
  private final ObjectMapper objectMapper;
  private final ResultPublisher resultPublisher;
  private final RoleSyncHelper roleSyncHelper;
  private final GroupSyncHelper groupSyncHelper;

  GroupResourceHandler(
      Keycloak keycloakClient,
      ObjectMapper objectMapper,
      ResultPublisher resultPublisher,
      RoleSyncHelper roleSyncHelper,
      GroupSyncHelper groupSyncHelper) {
    this.keycloakClient = keycloakClient;
    this.objectMapper = objectMapper;
    this.resultPublisher = resultPublisher;
    this.roleSyncHelper = roleSyncHelper;
    this.groupSyncHelper = groupSyncHelper;
  }

  @Override
  public void create(String realm, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      GroupConfig groupConfig = (GroupConfig) event.payload().config().value();
      GroupRepresentation groupRep =
          objectMapper.convertValue(groupConfig, GroupRepresentation.class);

      RealmResource realmResource = keycloakClient.realm(realm);

      String groupId = createGroupInKeycloak(realmResource, groupRep, groupConfig.getParentId());

      assignRolesToGroup(
          realmResource, groupId, groupConfig.getRealmRoles(), groupConfig.getClientRoles());

      groupSyncHelper.syncGroupMembers(groupConfig.getMembers(), groupId, realmResource);

      logger.info(
          "Created group: {} (ID: {}) in realm: {}",
          Encode.forJava(groupRep.getName()),
          Encode.forJava(maskId(groupId)),
          Encode.forJava(realm));
      resultPublisher.publish(event, SuccessCode.GROUP_CREATE_SUCCESS, groupId);

    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.GROUP_CREATION);
    } catch (WebApplicationException e) {
      wrapWebException(e, KeycloakOperation.GROUP_CREATION, AdapterErrorCode.KEYCLOAK_GROUP_ERROR);
    } catch (KeycloakOperationException e) {
      throw new FatalAdapterException(AdapterErrorCode.KEYCLOAK_GROUP_ERROR, e, e.getMessage());
    }
  }

  @Override
  public void update(String realm, String groupId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      GroupConfig groupConfig = (GroupConfig) event.payload().config().value();
      GroupRepresentation groupRep =
          objectMapper.convertValue(groupConfig, GroupRepresentation.class);

      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.groups().group(groupId).update(groupRep);

      assignRolesToGroup(
          realmResource, groupId, groupConfig.getRealmRoles(), groupConfig.getClientRoles());

      groupSyncHelper.syncGroupMembers(groupConfig.getMembers(), groupId, realmResource);

      logger.info(
          "Updated group: {} in realm: {}", Encode.forJava(maskId(groupId)), Encode.forJava(realm));
      resultPublisher.publish(event, SuccessCode.GROUP_UPDATE_SUCCESS, groupId);

    } catch (NotFoundException e) {
      throw new FatalAdapterException(
          AdapterErrorCode.RESOURCE_NOT_FOUND, e, "Group " + maskId(groupId));
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.GROUP_UPDATE);
    } catch (WebApplicationException e) {
      wrapWebException(e, KeycloakOperation.GROUP_UPDATE, AdapterErrorCode.KEYCLOAK_GROUP_ERROR);
    }
  }

  @Override
  public void delete(String realm, String groupId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.groups().group(groupId).remove();

      logger.info(
          "Deleted group: {} from realm: {}",
          Encode.forJava(maskId(groupId)),
          Encode.forJava(realm));
      resultPublisher.publish(event, SuccessCode.GROUP_DELETE_SUCCESS, groupId);

    } catch (NotFoundException e) {
      logger.info(
          "Group {} not found during delete, treating as success (already deleted)",
          Encode.forJava(maskId(groupId)));
      resultPublisher.publish(event, SuccessCode.GROUP_DELETE_SUCCESS, groupId);
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.GROUP_DELETION);
    } catch (WebApplicationException e) {
      wrapWebException(e, KeycloakOperation.GROUP_DELETION, AdapterErrorCode.KEYCLOAK_GROUP_ERROR);
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

  private String findExistingGroupId(RealmResource realmResource, String groupName)
      throws KeycloakOperationException {
    logger.info("Group {} already exists, treating create as success", Encode.forJava(groupName));
    return realmResource.groups().groups(groupName, 0, 1).stream()
        .filter(g -> g.getName().equals(groupName))
        .map(GroupRepresentation::getId)
        .findFirst()
        .orElseThrow(
            () ->
                new KeycloakOperationException(
                    "Group '" + groupName + "' returned 409 but could not be found by name"));
  }

  private void assignRolesToGroup(
      RealmResource realmResource,
      String groupId,
      Set<String> realmRoles,
      Map<String, List<String>> clientRoles) {
    RoleMappingResource roleMapping = realmResource.groups().group(groupId).roles();
    roleSyncHelper.syncRealmRoles(realmRoles, roleMapping, realmResource);
    roleSyncHelper.syncClientRoles(clientRoles, roleMapping, realmResource);
  }
}
