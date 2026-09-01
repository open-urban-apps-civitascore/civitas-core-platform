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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.idm.RealmConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.RealmRepresentation;

class KeycloakRealmIT extends KeycloakAdapterITBase {

  @Test
  void shouldCreateRealm() throws FatalAdapterException, RetryableAdapterException {
    RealmConfig realmConfig = new RealmConfig();
    realmConfig.setRealm("test-realm");
    realmConfig.setEnabled(true);
    realmConfig.setDisplayName("Test Realm");

    adapter.processConfigEvent(
        Topics.REALM_CREATED.toString(),
        createConfigEvent("test-realm", "realm", Operation.CREATE, realmConfig));

    RealmRepresentation createdRealm = keycloakClient.realm("test-realm").toRepresentation();
    assertNotNull(createdRealm);
    assertEquals("test-realm", createdRealm.getRealm());
    assertEquals("Test Realm", createdRealm.getDisplayName());
    assertTrue(createdRealm.isEnabled());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
    assertEquals("test-realm", resultEvent.resourceId());
  }

  @Test
  void shouldUpdateRealm() throws FatalAdapterException, RetryableAdapterException {
    createRealm("update-realm");

    RealmConfig updateConfig = new RealmConfig();
    updateConfig.setRealm("update-realm");
    updateConfig.setDisplayName("Updated Realm");
    updateConfig.setEnabled(false);

    adapter.processConfigEvent(
        Topics.REALM_UPDATED.toString(),
        createConfigEvent("update-realm", "realm", Operation.UPDATE, updateConfig));

    RealmRepresentation updatedRealm = keycloakClient.realm("update-realm").toRepresentation();
    assertEquals("Updated Realm", updatedRealm.getDisplayName());
    assertFalse(updatedRealm.isEnabled());
    assertSingleSuccessResult();
  }

  @Test
  void shouldDeleteRealm() throws FatalAdapterException, RetryableAdapterException {
    createRealm("delete-realm");

    RealmConfig realmConfig = new RealmConfig();
    realmConfig.setRealm("delete-realm");

    adapter.processConfigEvent(
        Topics.REALM_DELETED.toString(),
        createConfigEvent("delete-realm", "realm", Operation.DELETE, realmConfig));

    assertThrows(
        jakarta.ws.rs.NotFoundException.class,
        () -> keycloakClient.realm("delete-realm").toRepresentation());
    assertSingleSuccessResult();
  }

  @Test
  void updateRealm_whenRealmDoesNotExist_shouldThrowFatalException() {
    RealmConfig realmConfig = new RealmConfig();
    realmConfig.setRealm("non-existent-realm");

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () ->
                adapter.processConfigEvent(
                    Topics.REALM_UPDATED.toString(),
                    createConfigEvent(
                        "non-existent-realm", "realm", Operation.UPDATE, realmConfig)));

    assertEquals(AdapterErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    assertNotNull(exception.getSafeExternalMessage());
    assertFalse(exception.isRetryable());
  }

  @Test
  void shouldHandleCorrelationIdInResults()
      throws FatalAdapterException, RetryableAdapterException {
    String correlationId = UUID.randomUUID().toString();
    RealmConfig realmConfig = new RealmConfig();
    realmConfig.setRealm("correlation-realm");
    realmConfig.setEnabled(true);

    var event =
        createConfigEventWithCorrelation(
            "correlation-realm", "realm", Operation.CREATE, realmConfig, correlationId);

    adapter.processConfigEvent(Topics.REALM_CREATED.toString(), event);

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(correlationId, resultEvent.correlationId());
    assertEquals(event.metadata().messageId(), resultEvent.originalMessageId());
  }

  @Test
  void deleteRealm_whenRealmDoesNotExist_shouldPublishSuccessWithoutException()
      throws FatalAdapterException, RetryableAdapterException {
    RealmConfig realmConfig = new RealmConfig();
    realmConfig.setRealm("non-existent-delete-realm");

    adapter.processConfigEvent(
        Topics.REALM_DELETED.toString(),
        createConfigEvent("non-existent-delete-realm", "realm", Operation.DELETE, realmConfig));

    assertSingleSuccessResult();
  }

  @Test
  void createRealm_whenRealmAlreadyExists_shouldPublishSuccessWithoutException()
      throws FatalAdapterException, RetryableAdapterException {
    createRealm("duplicate-realm-create");
    eventPublisher.clear();

    RealmConfig realmConfig = new RealmConfig();
    realmConfig.setRealm("duplicate-realm-create");
    realmConfig.setEnabled(true);

    adapter.processConfigEvent(
        Topics.REALM_CREATED.toString(),
        createConfigEvent("duplicate-realm-create", "realm", Operation.CREATE, realmConfig));

    assertSingleSuccessResult();
  }
}
