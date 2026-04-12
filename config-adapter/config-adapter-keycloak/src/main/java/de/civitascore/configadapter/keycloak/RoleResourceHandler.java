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

import static de.civitascore.configadapter.keycloak.KeycloakErrorHandler.wrapNetworkException;
import static de.civitascore.configadapter.keycloak.KeycloakErrorHandler.wrapWebException;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.idm.RoleConfig;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles CREATE, UPDATE, and DELETE operations for Keycloak realm roles. */
class RoleResourceHandler implements KeycloakResourceHandler {

  private static final Logger logger = LoggerFactory.getLogger(RoleResourceHandler.class);

  private final Keycloak keycloakClient;
  private final ObjectMapper objectMapper;
  private final ResultPublisher resultPublisher;

  RoleResourceHandler(
      Keycloak keycloakClient, ObjectMapper objectMapper, ResultPublisher resultPublisher) {
    this.keycloakClient = keycloakClient;
    this.objectMapper = objectMapper;
    this.resultPublisher = resultPublisher;
  }

  @Override
  public void create(String realm, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      RoleRepresentation roleRep = convertToRoleRepresentation(event.payload().config().value());

      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.roles().create(roleRep);

      logger.info(
          "Created role: {} in realm: {}",
          Encode.forJava(roleRep.getName()),
          Encode.forJava(realm));
      resultPublisher.publish(event, SuccessCode.ROLE_CREATE_SUCCESS, roleRep.getName());

    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.ROLE_CREATION);
    } catch (WebApplicationException e) {
      if (e.getResponse().getStatus() == 409) {
        String roleName = ((RoleConfig) event.payload().config().value()).getName();
        logger.info("Role {} already exists, treating create as success", Encode.forJava(roleName));
        resultPublisher.publish(event, SuccessCode.ROLE_CREATE_SUCCESS, roleName);
        return;
      }
      wrapWebException(e, KeycloakOperation.ROLE_CREATION, AdapterErrorCode.KEYCLOAK_ROLE_ERROR);
    }
  }

  @Override
  public void update(String realm, String roleId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      RoleRepresentation roleRep = convertToRoleRepresentation(event.payload().config().value());

      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.roles().get(roleId).update(roleRep);

      logger.info("Updated role: {} in realm: {}", Encode.forJava(roleId), Encode.forJava(realm));
      resultPublisher.publish(event, SuccessCode.ROLE_UPDATE_SUCCESS, roleId);

    } catch (NotFoundException e) {
      throw new FatalAdapterException(AdapterErrorCode.RESOURCE_NOT_FOUND, e, "Role " + roleId);
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.ROLE_UPDATE);
    } catch (WebApplicationException e) {
      wrapWebException(e, KeycloakOperation.ROLE_UPDATE, AdapterErrorCode.KEYCLOAK_ROLE_ERROR);
    }
  }

  @Override
  public void delete(String realm, String roleId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.roles().get(roleId).remove();

      logger.info("Deleted role: {} from realm: {}", Encode.forJava(roleId), Encode.forJava(realm));
      resultPublisher.publish(event, SuccessCode.ROLE_DELETE_SUCCESS, roleId);

    } catch (NotFoundException e) {
      logger.info(
          "Role {} not found during delete, treating as success (already deleted)",
          Encode.forJava(roleId));
      resultPublisher.publish(event, SuccessCode.ROLE_DELETE_SUCCESS, roleId);
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.ROLE_DELETION);
    } catch (WebApplicationException e) {
      wrapWebException(e, KeycloakOperation.ROLE_DELETION, AdapterErrorCode.KEYCLOAK_ROLE_ERROR);
    }
  }

  private RoleRepresentation convertToRoleRepresentation(Object configData) {
    RoleRepresentation roleRep = objectMapper.convertValue(configData, RoleRepresentation.class);

    if (configData instanceof RoleConfig roleConfig) {
      if (roleConfig.getCompositeRoles() != null && !roleConfig.getCompositeRoles().isEmpty()) {
        RoleRepresentation.Composites composites = new RoleRepresentation.Composites();
        composites.setRealm(roleConfig.getCompositeRoles());
        roleRep.setComposites(composites);
      }
    }

    return roleRep;
  }
}
