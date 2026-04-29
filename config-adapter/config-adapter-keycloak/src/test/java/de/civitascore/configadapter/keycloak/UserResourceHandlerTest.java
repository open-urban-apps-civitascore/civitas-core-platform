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
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.Config;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.Payload;
import de.civitascore.configadapter.model.idm.UserConfig;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;

class UserResourceHandlerTest {

  private Keycloak keycloakClient;
  private ResultPublisher resultPublisher;
  private RoleSyncHelper roleSyncHelper;
  private GroupSyncHelper groupSyncHelper;
  private UserResourceHandler handler;

  @BeforeEach
  void setUp() {
    keycloakClient = mock(Keycloak.class);
    resultPublisher = mock(ResultPublisher.class);
    roleSyncHelper = mock(RoleSyncHelper.class);
    groupSyncHelper = mock(GroupSyncHelper.class);
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    handler =
        new UserResourceHandler(
            keycloakClient,
            objectMapper,
            resultPublisher,
            roleSyncHelper,
            groupSyncHelper,
            null,
            null);
  }

  private ConfigEvent userEvent(String username) {
    UserConfig userConfig = new UserConfig();
    userConfig.setUsername(username);
    return new ConfigEvent(
        new Metadata(null, null, null, null, null, null),
        new Payload("USER", "test-realm", Operation.CREATE, new Config(null, userConfig)));
  }

  @Nested
  @DisplayName("update")
  class Update {

    @Test
    @DisplayName("updates user and syncs roles")
    void shouldUpdateAndSyncRoles() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      UsersResource usersResource = mock(UsersResource.class);
      UserResource userResource = mock(UserResource.class);
      RoleMappingResource roleMapping = mock(RoleMappingResource.class);
      RoleScopeResource roleScopeResource = mock(RoleScopeResource.class);

      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.users()).thenReturn(usersResource);
      when(usersResource.get("user-id")).thenReturn(userResource);
      when(userResource.roles()).thenReturn(roleMapping);
      when(roleMapping.realmLevel()).thenReturn(roleScopeResource);
      when(roleScopeResource.listAll()).thenReturn(List.of());

      ConfigEvent event = userEvent("testuser");
      handler.update("test-realm", "user-id", event);

      verify(userResource).update(any(UserRepresentation.class));
      verify(roleSyncHelper).syncRealmRoles(any(), any(), any());
      verify(resultPublisher).publish(event, SuccessCode.USER_UPDATE_SUCCESS, "user-id");
    }
  }

  @Nested
  @DisplayName("delete")
  class Delete {

    @Test
    @DisplayName("deletes user and publishes success")
    void shouldDeleteAndPublish() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      UsersResource usersResource = mock(UsersResource.class);
      UserResource userResource = mock(UserResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.users()).thenReturn(usersResource);
      when(usersResource.get("user-id")).thenReturn(userResource);

      ConfigEvent event = userEvent("testuser");
      handler.delete("test-realm", "user-id", event);

      verify(userResource).remove();
      verify(resultPublisher).publish(event, SuccessCode.USER_DELETE_SUCCESS, "user-id");
    }

    @Test
    @DisplayName("treats NotFoundException on delete as success (idempotent)")
    void shouldTreatNotFoundAsSuccess() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      UsersResource usersResource = mock(UsersResource.class);
      UserResource userResource = mock(UserResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.users()).thenReturn(usersResource);
      when(usersResource.get("gone")).thenReturn(userResource);
      doThrow(new NotFoundException()).when(userResource).remove();

      ConfigEvent event = userEvent("testuser");
      handler.delete("test-realm", "gone", event);

      verify(resultPublisher).publish(event, SuccessCode.USER_DELETE_SUCCESS, "gone");
    }

    @Test
    @DisplayName("throws RetryableAdapterException on network error")
    void shouldThrowRetryableOnNetworkError() {
      RealmResource realmResource = mock(RealmResource.class);
      UsersResource usersResource = mock(UsersResource.class);
      UserResource userResource = mock(UserResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.users()).thenReturn(usersResource);
      when(usersResource.get("user-id")).thenReturn(userResource);
      doThrow(new ProcessingException("timeout")).when(userResource).remove();

      ConfigEvent event = userEvent("testuser");
      assertThrows(
          RetryableAdapterException.class, () -> handler.delete("test-realm", "user-id", event));
    }
  }
}
