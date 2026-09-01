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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.idm.ClientConfig;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;

class KeycloakClientIT extends KeycloakAdapterITBase {

  @Test
  void shouldCreateClient() throws FatalAdapterException, RetryableAdapterException {
    createRealm("client-realm");

    ClientConfig clientConfig = new ClientConfig();
    clientConfig.setClientId("test-client");
    clientConfig.setEnabled(true);
    clientConfig.setPublicClient(false);
    clientConfig.setDirectAccessGrantsEnabled(true);

    adapter.processConfigEvent(
        Topics.CLIENT_CREATED.toString(),
        createConfigEvent("client-realm", "client", Operation.CREATE, clientConfig));

    List<ClientRepresentation> clients =
        keycloakClient.realm("client-realm").clients().findByClientId("test-client");
    assertEquals(1, clients.size());
    assertEquals("test-client", clients.getFirst().getClientId());
    assertTrue(clients.getFirst().isEnabled());
    assertSingleSuccessResult();
  }

  @Test
  void updateClient_whenValidConfig_shouldUpdateClientAttributes()
      throws FatalAdapterException, RetryableAdapterException {
    createRealm("client-update-realm");

    ClientRepresentation initialClient = new ClientRepresentation();
    initialClient.setClientId("my-app-client");
    initialClient.setEnabled(true);
    String clientId;
    try (var response =
        keycloakClient.realm("client-update-realm").clients().create(initialClient)) {
      clientId = CreatedResponseUtil.getCreatedId(response);
      assertEquals(201, response.getStatus());
    }

    ClientConfig updateClientConfig = new ClientConfig();
    updateClientConfig.setId(clientId);
    updateClientConfig.setDescription("New Description via Adapter");
    updateClientConfig.setEnabled(false);

    adapter.processConfigEvent(
        Topics.CLIENT_UPDATED.toString(),
        createConfigEvent("client-update-realm", "client", Operation.UPDATE, updateClientConfig));

    List<ClientRepresentation> clients =
        keycloakClient.realm("client-update-realm").clients().findByClientId("my-app-client");
    assertFalse(clients.isEmpty(), "Client should still exist");
    ClientRepresentation updatedClient = clients.getFirst();
    assertEquals("New Description via Adapter", updatedClient.getDescription());
    assertFalse(updatedClient.isEnabled(), "Client should be disabled");
    assertSingleSuccessResult();
  }

  @Test
  void deleteClient_whenValidConfig_shouldRemoveClientFromRealm()
      throws FatalAdapterException, RetryableAdapterException {
    createRealm("client-delete-realm");

    ClientRepresentation initialClient = new ClientRepresentation();
    initialClient.setClientId("delete-me-client");
    initialClient.setEnabled(true);
    String clientId;
    RealmResource realmResource = keycloakClient.realm("client-delete-realm");
    try (Response response = realmResource.clients().create(initialClient)) {
      clientId = CreatedResponseUtil.getCreatedId(response);
      assertEquals(201, response.getStatus());
    }

    ClientConfig deleteClientConfig = new ClientConfig();
    deleteClientConfig.setId(clientId);

    adapter.processConfigEvent(
        Topics.CLIENT_DELETED.toString(),
        createConfigEvent("client-delete-realm", "client", Operation.DELETE, deleteClientConfig));

    assertTrue(
        realmResource.clients().findByClientId("delete-me-client").isEmpty(),
        "Client should be deleted");
    assertSingleSuccessResult();
  }

  @Test
  void deleteClient_whenClientDoesNotExist_shouldPublishSuccessWithoutException()
      throws FatalAdapterException, RetryableAdapterException {
    createRealm("idempotent-client-realm");

    ClientConfig clientConfig = new ClientConfig();
    clientConfig.setId(UUID.randomUUID().toString());

    adapter.processConfigEvent(
        Topics.CLIENT_DELETED.toString(),
        createConfigEvent("idempotent-client-realm", "client", Operation.DELETE, clientConfig));

    assertSingleSuccessResult();
  }

  @Test
  void createClient_whenClientAlreadyExists_shouldPublishSuccessWithoutException()
      throws FatalAdapterException, RetryableAdapterException {
    createRealm("duplicate-client-realm");

    ClientRepresentation existingClient = new ClientRepresentation();
    existingClient.setClientId("duplicate-client");
    existingClient.setEnabled(true);
    try (Response response =
        keycloakClient.realm("duplicate-client-realm").clients().create(existingClient)) {
      assertEquals(201, response.getStatus());
    }
    eventPublisher.clear();

    ClientConfig clientConfig = new ClientConfig();
    clientConfig.setClientId("duplicate-client");
    clientConfig.setEnabled(true);

    adapter.processConfigEvent(
        Topics.CLIENT_CREATED.toString(),
        createConfigEvent("duplicate-client-realm", "client", Operation.CREATE, clientConfig));

    assertSingleSuccessResult();
  }
}
