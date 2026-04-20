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
import de.civitascore.configadapter.model.Config;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.Payload;
import de.civitascore.configadapter.model.idm.RoleConfig;
import jakarta.ws.rs.NotFoundException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.RoleRepresentation;

class RoleResourceHandlerTest {

  private Keycloak keycloakClient;
  private ResultPublisher resultPublisher;
  private RoleResourceHandler handler;

  @BeforeEach
  void setUp() {
    keycloakClient = mock(Keycloak.class);
    resultPublisher = mock(ResultPublisher.class);
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    handler = new RoleResourceHandler(keycloakClient, objectMapper, resultPublisher);
  }

  private ConfigEvent roleEvent(String roleName, Set<String> compositeRoles) {
    RoleConfig roleConfig = new RoleConfig();
    roleConfig.setName(roleName);
    roleConfig.setCompositeRoles(compositeRoles);
    return new ConfigEvent(
        new Metadata(null, null, null, null, null, null),
        new Payload("ROLE", "test-realm", Operation.CREATE, new Config(null, roleConfig)));
  }

  private ConfigEvent roleEvent(String roleName) {
    return roleEvent(roleName, null);
  }

  @Nested
  @DisplayName("create")
  class Create {

    @Test
    @DisplayName("creates role and publishes success")
    void shouldCreateAndPublish() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      RolesResource rolesResource = mock(RolesResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.roles()).thenReturn(rolesResource);

      ConfigEvent event = roleEvent("admin");
      handler.create("test-realm", event);

      verify(rolesResource).create(any(RoleRepresentation.class));
      verify(resultPublisher).publish(event, SuccessCode.ROLE_CREATE_SUCCESS, "admin");
    }

    @Test
    @DisplayName("treats 409 conflict as success (idempotent)")
    void shouldTreatConflictAsSuccess() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      RolesResource rolesResource = mock(RolesResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.roles()).thenReturn(rolesResource);
      doThrow(new jakarta.ws.rs.WebApplicationException(409))
          .when(rolesResource)
          .create(any(RoleRepresentation.class));

      ConfigEvent event = roleEvent("existing-role");
      handler.create("test-realm", event);

      verify(resultPublisher).publish(event, SuccessCode.ROLE_CREATE_SUCCESS, "existing-role");
    }
  }

  @Nested
  @DisplayName("update")
  class Update {

    @Test
    @DisplayName("updates role and publishes success")
    void shouldUpdateAndPublish() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      RolesResource rolesResource = mock(RolesResource.class);
      RoleResource roleResource = mock(RoleResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.roles()).thenReturn(rolesResource);
      when(rolesResource.get("admin")).thenReturn(roleResource);

      ConfigEvent event = roleEvent("admin");
      handler.update("test-realm", "admin", event);

      verify(roleResource).update(any(RoleRepresentation.class));
      verify(resultPublisher).publish(event, SuccessCode.ROLE_UPDATE_SUCCESS, "admin");
    }

    @Test
    @DisplayName("throws FatalAdapterException when role not found")
    void shouldThrowFatalWhenNotFound() {
      RealmResource realmResource = mock(RealmResource.class);
      RolesResource rolesResource = mock(RolesResource.class);
      RoleResource roleResource = mock(RoleResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.roles()).thenReturn(rolesResource);
      when(rolesResource.get("missing")).thenReturn(roleResource);
      doThrow(new NotFoundException()).when(roleResource).update(any(RoleRepresentation.class));

      ConfigEvent event = roleEvent("missing");
      assertThrows(
          FatalAdapterException.class, () -> handler.update("test-realm", "missing", event));
    }
  }

  @Nested
  @DisplayName("delete")
  class Delete {

    @Test
    @DisplayName("deletes role and publishes success")
    void shouldDeleteAndPublish() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      RolesResource rolesResource = mock(RolesResource.class);
      RoleResource roleResource = mock(RoleResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.roles()).thenReturn(rolesResource);
      when(rolesResource.get("admin")).thenReturn(roleResource);

      ConfigEvent event = roleEvent("admin");
      handler.delete("test-realm", "admin", event);

      verify(roleResource).remove();
      verify(resultPublisher).publish(event, SuccessCode.ROLE_DELETE_SUCCESS, "admin");
    }

    @Test
    @DisplayName("treats NotFoundException on delete as success (idempotent)")
    void shouldTreatNotFoundAsSuccess() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      RolesResource rolesResource = mock(RolesResource.class);
      RoleResource roleResource = mock(RoleResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.roles()).thenReturn(rolesResource);
      when(rolesResource.get("gone")).thenReturn(roleResource);
      doThrow(new NotFoundException()).when(roleResource).remove();

      ConfigEvent event = roleEvent("gone");
      handler.delete("test-realm", "gone", event);

      verify(resultPublisher).publish(event, SuccessCode.ROLE_DELETE_SUCCESS, "gone");
    }
  }

  @Nested
  @DisplayName("convertToRoleRepresentation")
  class CompositeRoles {

    @Test
    @DisplayName("creates role with composite roles")
    void shouldHandleCompositeRoles() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      RolesResource rolesResource = mock(RolesResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.roles()).thenReturn(rolesResource);

      ConfigEvent event = roleEvent("composite-role", Set.of("sub-role-1", "sub-role-2"));
      handler.create("test-realm", event);

      verify(rolesResource).create(any(RoleRepresentation.class));
      verify(resultPublisher).publish(event, SuccessCode.ROLE_CREATE_SUCCESS, "composite-role");
    }
  }
}
