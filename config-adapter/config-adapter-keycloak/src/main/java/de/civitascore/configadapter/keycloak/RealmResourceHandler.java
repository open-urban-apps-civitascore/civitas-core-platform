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
import de.civitascore.configadapter.model.idm.RealmConfig;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RealmRepresentation;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles CREATE, UPDATE, and DELETE operations for Keycloak realms. */
class RealmResourceHandler implements KeycloakResourceHandler {

  private static final Logger logger = LoggerFactory.getLogger(RealmResourceHandler.class);

  private final Keycloak keycloakClient;
  private final ObjectMapper objectMapper;
  private final ResultPublisher resultPublisher;

  RealmResourceHandler(
      Keycloak keycloakClient, ObjectMapper objectMapper, ResultPublisher resultPublisher) {
    this.keycloakClient = keycloakClient;
    this.objectMapper = objectMapper;
    this.resultPublisher = resultPublisher;
  }

  @Override
  public void create(String realm, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      RealmRepresentation realmRep =
          objectMapper.convertValue(event.payload().config().value(), RealmRepresentation.class);

      keycloakClient.realms().create(realmRep);

      String realmName = realmRep.getRealm();
      logger.info("Created realm: {}", Encode.forJava(realmName));
      resultPublisher.publish(event, SuccessCode.REALM_CREATE_SUCCESS, realmName);

    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.REALM_CREATION);
    } catch (WebApplicationException e) {
      if (e.getResponse().getStatus() == 409) {
        String realmName = ((RealmConfig) event.payload().config().value()).getRealm();
        logger.info(
            "Realm {} already exists, treating create as success", Encode.forJava(realmName));
        resultPublisher.publish(event, SuccessCode.REALM_CREATE_SUCCESS, realmName);
        return;
      }
      wrapWebException(e, KeycloakOperation.REALM_CREATION, AdapterErrorCode.KEYCLOAK_REALM_ERROR);
    }
  }

  @Override
  public void update(String realm, String realmName, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      RealmRepresentation realmRep =
          objectMapper.convertValue(event.payload().config().value(), RealmRepresentation.class);

      RealmResource realmResource = keycloakClient.realm(realmName);
      realmResource.update(realmRep);

      logger.info("Updated realm: {}", Encode.forJava(realmName));
      resultPublisher.publish(event, SuccessCode.REALM_UPDATE_SUCCESS, realmName);

    } catch (NotFoundException e) {
      throw new FatalAdapterException(AdapterErrorCode.RESOURCE_NOT_FOUND, e, "Realm " + realmName);
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.REALM_UPDATE);
    } catch (WebApplicationException e) {
      wrapWebException(e, KeycloakOperation.REALM_UPDATE, AdapterErrorCode.KEYCLOAK_REALM_ERROR);
    }
  }

  @Override
  public void delete(String realm, String realmName, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      keycloakClient.realm(realmName).remove();
      logger.info("Deleted realm: {}", Encode.forJava(realmName));
      resultPublisher.publish(event, SuccessCode.REALM_DELETE_SUCCESS, realmName);

    } catch (NotFoundException e) {
      logger.info(
          "Realm {} not found during delete, treating as success (already deleted)",
          Encode.forJava(realmName));
      resultPublisher.publish(event, SuccessCode.REALM_DELETE_SUCCESS, realmName);
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.REALM_DELETION);
    } catch (WebApplicationException e) {
      wrapWebException(e, KeycloakOperation.REALM_DELETION, AdapterErrorCode.KEYCLOAK_REALM_ERROR);
    }
  }
}
