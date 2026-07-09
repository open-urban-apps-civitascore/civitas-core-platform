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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.Config;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.Payload;
import de.civitascore.configadapter.model.idm.ClientConfig;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;

class ClientResourceHandlerTest {

  private Keycloak keycloakClient;
  private ResultPublisher resultPublisher;
  private ClientResourceHandler handler;

  @BeforeEach
  void setUp() {
    keycloakClient = mock(Keycloak.class);
    resultPublisher = mock(ResultPublisher.class);
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    handler = new ClientResourceHandler(keycloakClient, objectMapper, resultPublisher);
  }

  private ConfigEvent clientEvent(String clientId) {
    ClientConfig clientConfig = new ClientConfig();
    clientConfig.setClientId(clientId);
    return new ConfigEvent(
        new Metadata(null, null, null, null, null, null),
        new Payload("CLIENT", "test-realm", Operation.CREATE, new Config(null, clientConfig)));
  }

  @Nested
  @DisplayName("create")
  class Create {

    @Test
    @DisplayName("creates client and publishes success on 201")
    void shouldCreateAndPublish() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      ClientsResource clientsResource = mock(ClientsResource.class);
      Response response = mock(Response.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.clients()).thenReturn(clientsResource);
      when(clientsResource.create(any(ClientRepresentation.class))).thenReturn(response);
      when(response.getStatus()).thenReturn(201);

      ConfigEvent event = clientEvent("my-client");
      handler.create("test-realm", event);

      verify(resultPublisher).publish(event, SuccessCode.CLIENT_CREATE_SUCCESS, "my-client");
    }

    @Test
    @DisplayName("treats 409 conflict as success (idempotent)")
    void shouldTreatConflictAsSuccess() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      ClientsResource clientsResource = mock(ClientsResource.class);
      Response response = mock(Response.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.clients()).thenReturn(clientsResource);
      when(clientsResource.create(any(ClientRepresentation.class))).thenReturn(response);
      when(response.getStatus()).thenReturn(409);

      ConfigEvent event = clientEvent("existing-client");
      handler.create("test-realm", event);

      verify(resultPublisher).publish(event, SuccessCode.CLIENT_CREATE_SUCCESS, "existing-client");
    }
  }

  @Nested
  @DisplayName("update")
  class Update {

    @Test
    @DisplayName("updates client and publishes success")
    void shouldUpdateAndPublish() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      ClientsResource clientsResource = mock(ClientsResource.class);
      ClientResource clientResource = mock(ClientResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.clients()).thenReturn(clientsResource);
      when(clientsResource.get("client-uuid")).thenReturn(clientResource);

      ConfigEvent event = clientEvent("my-client");
      handler.update("test-realm", "client-uuid", event);

      verify(clientResource).update(any(ClientRepresentation.class));
      verify(resultPublisher).publish(event, SuccessCode.CLIENT_UPDATE_SUCCESS, "client-uuid");
    }

    @Test
    @DisplayName("throws FatalAdapterException when client not found")
    void shouldThrowFatalWhenNotFound() {
      RealmResource realmResource = mock(RealmResource.class);
      ClientsResource clientsResource = mock(ClientsResource.class);
      ClientResource clientResource = mock(ClientResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.clients()).thenReturn(clientsResource);
      when(clientsResource.get("missing")).thenReturn(clientResource);
      doThrow(new NotFoundException()).when(clientResource).update(any(ClientRepresentation.class));

      ConfigEvent event = clientEvent("my-client");
      assertThrows(
          FatalAdapterException.class, () -> handler.update("test-realm", "missing", event));
    }
  }

  @Nested
  @DisplayName("delete")
  class Delete {

    @Test
    @DisplayName("deletes client and publishes success")
    void shouldDeleteAndPublish() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      ClientsResource clientsResource = mock(ClientsResource.class);
      ClientResource clientResource = mock(ClientResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.clients()).thenReturn(clientsResource);
      when(clientsResource.get("client-uuid")).thenReturn(clientResource);

      ConfigEvent event = clientEvent("my-client");
      handler.delete("test-realm", "client-uuid", event);

      verify(clientResource).remove();
      verify(resultPublisher).publish(event, SuccessCode.CLIENT_DELETE_SUCCESS, "client-uuid");
    }

    @Test
    @DisplayName("treats NotFoundException on delete as success (idempotent)")
    void shouldTreatNotFoundAsSuccess() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      ClientsResource clientsResource = mock(ClientsResource.class);
      ClientResource clientResource = mock(ClientResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.clients()).thenReturn(clientsResource);
      when(clientsResource.get("gone")).thenReturn(clientResource);
      doThrow(new NotFoundException()).when(clientResource).remove();

      ConfigEvent event = clientEvent("my-client");
      handler.delete("test-realm", "gone", event);

      verify(resultPublisher).publish(event, SuccessCode.CLIENT_DELETE_SUCCESS, "gone");
    }

    @Test
    @DisplayName("throws RetryableAdapterException on network error")
    void shouldThrowRetryableOnNetworkError() {
      RealmResource realmResource = mock(RealmResource.class);
      ClientsResource clientsResource = mock(ClientsResource.class);
      ClientResource clientResource = mock(ClientResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.clients()).thenReturn(clientsResource);
      when(clientsResource.get("client-uuid")).thenReturn(clientResource);
      doThrow(new ProcessingException("timeout")).when(clientResource).remove();

      ConfigEvent event = clientEvent("my-client");
      assertThrows(
          RetryableAdapterException.class,
          () -> handler.delete("test-realm", "client-uuid", event));
    }
  }
}
