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
import de.civitascore.configadapter.model.idm.RealmConfig;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RealmsResource;
import org.keycloak.representations.idm.RealmRepresentation;

class RealmResourceHandlerTest {

  private Keycloak keycloakClient;
  private ResultPublisher resultPublisher;
  private RealmResourceHandler handler;

  @BeforeEach
  void setUp() {
    keycloakClient = mock(Keycloak.class);
    resultPublisher = mock(ResultPublisher.class);
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    handler = new RealmResourceHandler(keycloakClient, objectMapper, resultPublisher);
  }

  private ConfigEvent realmEvent(String realmName) {
    RealmConfig realmConfig = new RealmConfig();
    realmConfig.setRealm(realmName);
    return new ConfigEvent(
        new Metadata(null, null, null, null, null, null),
        new Payload("REALM", realmName, Operation.CREATE, new Config(null, realmConfig)));
  }

  @Nested
  @DisplayName("create")
  class Create {

    @Test
    @DisplayName("creates realm and publishes success")
    void shouldCreateAndPublish() throws Exception {
      RealmsResource realms = mock(RealmsResource.class);
      when(keycloakClient.realms()).thenReturn(realms);

      ConfigEvent event = realmEvent("test-realm");
      handler.create("test-realm", event);

      verify(realms).create(any(RealmRepresentation.class));
      verify(resultPublisher).publish(event, SuccessCode.REALM_CREATE_SUCCESS, "test-realm");
    }

    @Test
    @DisplayName("treats 409 conflict as success (idempotent)")
    void shouldTreatConflictAsSuccess() throws Exception {
      RealmsResource realms = mock(RealmsResource.class);
      when(keycloakClient.realms()).thenReturn(realms);
      doThrow(new jakarta.ws.rs.WebApplicationException(409))
          .when(realms)
          .create(any(RealmRepresentation.class));

      ConfigEvent event = realmEvent("existing-realm");
      handler.create("existing-realm", event);

      verify(resultPublisher).publish(event, SuccessCode.REALM_CREATE_SUCCESS, "existing-realm");
    }

    @Test
    @DisplayName("throws RetryableAdapterException on network error")
    void shouldThrowRetryableOnNetworkError() {
      RealmsResource realms = mock(RealmsResource.class);
      when(keycloakClient.realms()).thenReturn(realms);
      doThrow(new ProcessingException("Connection refused"))
          .when(realms)
          .create(any(RealmRepresentation.class));

      ConfigEvent event = realmEvent("test-realm");
      assertThrows(RetryableAdapterException.class, () -> handler.create("test-realm", event));
    }
  }

  @Nested
  @DisplayName("update")
  class Update {

    @Test
    @DisplayName("updates realm and publishes success")
    void shouldUpdateAndPublish() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);

      ConfigEvent event = realmEvent("test-realm");
      handler.update("test-realm", "test-realm", event);

      verify(realmResource).update(any(RealmRepresentation.class));
      verify(resultPublisher).publish(event, SuccessCode.REALM_UPDATE_SUCCESS, "test-realm");
    }

    @Test
    @DisplayName("throws FatalAdapterException when realm not found")
    void shouldThrowFatalWhenNotFound() {
      RealmResource realmResource = mock(RealmResource.class);
      when(keycloakClient.realm("missing")).thenReturn(realmResource);
      doThrow(new NotFoundException()).when(realmResource).update(any(RealmRepresentation.class));

      ConfigEvent event = realmEvent("missing");
      assertThrows(FatalAdapterException.class, () -> handler.update("missing", "missing", event));
    }
  }

  @Nested
  @DisplayName("delete")
  class Delete {

    @Test
    @DisplayName("deletes realm and publishes success")
    void shouldDeleteAndPublish() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);

      ConfigEvent event = realmEvent("test-realm");
      handler.delete("test-realm", "test-realm", event);

      verify(realmResource).remove();
      verify(resultPublisher).publish(event, SuccessCode.REALM_DELETE_SUCCESS, "test-realm");
    }

    @Test
    @DisplayName("treats NotFoundException on delete as success (idempotent)")
    void shouldTreatNotFoundAsSuccess() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      when(keycloakClient.realm("gone")).thenReturn(realmResource);
      doThrow(new NotFoundException()).when(realmResource).remove();

      ConfigEvent event = realmEvent("gone");
      handler.delete("gone", "gone", event);

      verify(resultPublisher).publish(event, SuccessCode.REALM_DELETE_SUCCESS, "gone");
    }
  }
}
