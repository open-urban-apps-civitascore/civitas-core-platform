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

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;

class RoleSyncHelperTest {

  private RoleSyncHelper helper;

  @BeforeEach
  void setUp() {
    helper = new RoleSyncHelper();
  }

  private RoleRepresentation role(String name) {
    RoleRepresentation r = new RoleRepresentation();
    r.setName(name);
    return r;
  }

  @Nested
  @DisplayName("syncRealmRoles")
  class SyncRealmRoles {

    @Test
    @DisplayName("adds missing roles and removes extra roles")
    void shouldAddAndRemoveRoles() {
      RoleMappingResource roleMapping = mock(RoleMappingResource.class);
      RoleScopeResource roleScope = mock(RoleScopeResource.class);
      RealmResource realmResource = mock(RealmResource.class);
      RolesResource rolesResource = mock(RolesResource.class);
      RoleResource newRoleResource = mock(RoleResource.class);

      when(roleMapping.realmLevel()).thenReturn(roleScope);
      when(roleScope.listAll()).thenReturn(List.of(role("old-role"), role("keep-role")));
      when(realmResource.roles()).thenReturn(rolesResource);
      when(rolesResource.get("new-role")).thenReturn(newRoleResource);

      RoleRepresentation newRoleRep = role("new-role");
      when(newRoleResource.toRepresentation()).thenReturn(newRoleRep);

      helper.syncRealmRoles(Set.of("keep-role", "new-role"), roleMapping, realmResource);

      verify(roleScope).remove(anyList());
      verify(roleScope).add(anyList());
    }

    @Test
    @DisplayName("does nothing when roles match")
    void shouldDoNothingWhenRolesMatch() {
      RoleMappingResource roleMapping = mock(RoleMappingResource.class);
      RoleScopeResource roleScope = mock(RoleScopeResource.class);
      RealmResource realmResource = mock(RealmResource.class);

      when(roleMapping.realmLevel()).thenReturn(roleScope);
      when(roleScope.listAll()).thenReturn(List.of(role("admin")));

      helper.syncRealmRoles(Set.of("admin"), roleMapping, realmResource);

      verify(roleScope, never()).remove(anyList());
      verify(roleScope, never()).add(anyList());
    }

    @Test
    @DisplayName("handles null desired roles as empty set")
    void shouldHandleNullDesiredRoles() {
      RoleMappingResource roleMapping = mock(RoleMappingResource.class);
      RoleScopeResource roleScope = mock(RoleScopeResource.class);
      RealmResource realmResource = mock(RealmResource.class);

      when(roleMapping.realmLevel()).thenReturn(roleScope);
      when(roleScope.listAll()).thenReturn(List.of(role("to-remove")));

      helper.syncRealmRoles(null, roleMapping, realmResource);

      verify(roleScope).remove(anyList());
    }

    @Test
    @DisplayName("skips role that does not exist in Keycloak")
    void shouldSkipMissingRole() {
      RoleMappingResource roleMapping = mock(RoleMappingResource.class);
      RoleScopeResource roleScope = mock(RoleScopeResource.class);
      RealmResource realmResource = mock(RealmResource.class);
      RolesResource rolesResource = mock(RolesResource.class);
      RoleResource missingRole = mock(RoleResource.class);

      when(roleMapping.realmLevel()).thenReturn(roleScope);
      when(roleScope.listAll()).thenReturn(List.of());
      when(realmResource.roles()).thenReturn(rolesResource);
      when(rolesResource.get("nonexistent")).thenReturn(missingRole);
      when(missingRole.toRepresentation()).thenThrow(new NotFoundException());

      helper.syncRealmRoles(Set.of("nonexistent"), roleMapping, realmResource);

      verify(roleScope, never()).add(anyList());
    }
  }

  @Nested
  @DisplayName("syncClientRoles")
  class SyncClientRoles {

    @Test
    @DisplayName("does nothing when client roles map is null")
    void shouldDoNothingWhenNull() {
      RoleMappingResource roleMapping = mock(RoleMappingResource.class);
      RealmResource realmResource = mock(RealmResource.class);

      helper.syncClientRoles(null, roleMapping, realmResource);
      // No exceptions, no interactions
    }

    @Test
    @DisplayName("does nothing when client roles map is empty")
    void shouldDoNothingWhenEmpty() {
      RoleMappingResource roleMapping = mock(RoleMappingResource.class);
      RealmResource realmResource = mock(RealmResource.class);

      helper.syncClientRoles(Map.of(), roleMapping, realmResource);
      // No exceptions, no interactions
    }

    @Test
    @DisplayName("syncs roles for a specific client")
    void shouldSyncRolesForClient() {
      RoleMappingResource roleMapping = mock(RoleMappingResource.class);
      RealmResource realmResource = mock(RealmResource.class);
      ClientsResource clientsResource = mock(ClientsResource.class);
      ClientResource clientResource = mock(ClientResource.class);
      RolesResource clientRolesResource = mock(RolesResource.class);
      RoleResource roleResource = mock(RoleResource.class);
      RoleScopeResource clientRoleScope = mock(RoleScopeResource.class);

      ClientRepresentation clientRep = new ClientRepresentation();
      clientRep.setId("client-uuid");

      when(realmResource.clients()).thenReturn(clientsResource);
      when(clientsResource.findByClientId("my-client")).thenReturn(List.of(clientRep));
      when(clientsResource.get("client-uuid")).thenReturn(clientResource);
      when(roleMapping.clientLevel("client-uuid")).thenReturn(clientRoleScope);
      when(clientRoleScope.listAll()).thenReturn(List.of());
      when(clientResource.roles()).thenReturn(clientRolesResource);
      when(clientRolesResource.get("client-role")).thenReturn(roleResource);

      RoleRepresentation roleRep = role("client-role");
      when(roleResource.toRepresentation()).thenReturn(roleRep);

      helper.syncClientRoles(
          Map.of("my-client", List.of("client-role")), roleMapping, realmResource);

      verify(clientRoleScope).add(anyList());
    }

    @Test
    @DisplayName("skips client that does not exist")
    void shouldSkipMissingClient() {
      RoleMappingResource roleMapping = mock(RoleMappingResource.class);
      RealmResource realmResource = mock(RealmResource.class);
      ClientsResource clientsResource = mock(ClientsResource.class);

      when(realmResource.clients()).thenReturn(clientsResource);
      when(clientsResource.findByClientId("missing-client")).thenReturn(List.of());

      helper.syncClientRoles(
          Map.of("missing-client", List.of("some-role")), roleMapping, realmResource);
      // No exceptions, skipped silently
    }
  }
}
