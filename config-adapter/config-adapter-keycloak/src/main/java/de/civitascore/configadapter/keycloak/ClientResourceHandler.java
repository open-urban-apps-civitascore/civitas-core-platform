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
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles CREATE, UPDATE, and DELETE operations for Keycloak clients. */
class ClientResourceHandler implements KeycloakResourceHandler {

  private static final Logger logger = LoggerFactory.getLogger(ClientResourceHandler.class);

  private final Keycloak keycloakClient;
  private final ObjectMapper objectMapper;
  private final ResultPublisher resultPublisher;

  ClientResourceHandler(
      Keycloak keycloakClient, ObjectMapper objectMapper, ResultPublisher resultPublisher) {
    this.keycloakClient = keycloakClient;
    this.objectMapper = objectMapper;
    this.resultPublisher = resultPublisher;
  }

  @Override
  public void create(String realm, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      ClientRepresentation clientRep =
          objectMapper.convertValue(event.payload().config().value(), ClientRepresentation.class);

      RealmResource realmResource = keycloakClient.realm(realm);

      try (Response response = realmResource.clients().create(clientRep)) {
        if (response.getStatus() == 409) {
          logger.info(
              "Client {} already exists, treating create as success",
              Encode.forJava(clientRep.getClientId()));
          resultPublisher.publish(
              event, SuccessCode.CLIENT_CREATE_SUCCESS, clientRep.getClientId());
          return;
        }
        validateResponse(KeycloakOperation.CLIENT_CREATION, 201, response);
      }

      logger.info(
          "Created client: {} in realm: {}",
          Encode.forJava(clientRep.getClientId()),
          Encode.forJava(realm));
      resultPublisher.publish(event, SuccessCode.CLIENT_CREATE_SUCCESS, clientRep.getClientId());

    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.CLIENT_CREATION);
    } catch (WebApplicationException e) {
      wrapWebException(
          e, KeycloakOperation.CLIENT_CREATION, AdapterErrorCode.KEYCLOAK_CLIENT_ERROR);
    } catch (KeycloakOperationException e) {
      throw new FatalAdapterException(AdapterErrorCode.KEYCLOAK_CLIENT_ERROR, e, e.getMessage());
    }
  }

  @Override
  public void update(String realm, String clientId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      ClientRepresentation clientRep =
          objectMapper.convertValue(event.payload().config().value(), ClientRepresentation.class);

      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.clients().get(clientId).update(clientRep);

      logger.info(
          "Updated client: {} in realm: {}", Encode.forJava(clientId), Encode.forJava(realm));
      resultPublisher.publish(event, SuccessCode.CLIENT_UPDATE_SUCCESS, clientId);

    } catch (NotFoundException e) {
      throw new FatalAdapterException(
          AdapterErrorCode.RESOURCE_NOT_FOUND, e, "Client " + maskId(clientId));
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.CLIENT_UPDATE);
    } catch (WebApplicationException e) {
      wrapWebException(e, KeycloakOperation.CLIENT_UPDATE, AdapterErrorCode.KEYCLOAK_CLIENT_ERROR);
    }
  }

  @Override
  public void delete(String realm, String clientId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      RealmResource realmResource = keycloakClient.realm(realm);
      realmResource.clients().get(clientId).remove();

      logger.info(
          "Deleted client: {} from realm: {}", Encode.forJava(clientId), Encode.forJava(realm));
      resultPublisher.publish(event, SuccessCode.CLIENT_DELETE_SUCCESS, clientId);

    } catch (NotFoundException e) {
      logger.info(
          "Client {} not found during delete, treating as success (already deleted)",
          Encode.forJava(maskId(clientId)));
      resultPublisher.publish(event, SuccessCode.CLIENT_DELETE_SUCCESS, clientId);
    } catch (ProcessingException e) {
      throw wrapNetworkException(e, KeycloakOperation.CLIENT_DELETION);
    } catch (WebApplicationException e) {
      wrapWebException(
          e, KeycloakOperation.CLIENT_DELETION, AdapterErrorCode.KEYCLOAK_CLIENT_ERROR);
    }
  }
}
