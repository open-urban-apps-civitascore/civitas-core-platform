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
import de.civitascore.configadapter.model.idm.GroupConfig;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.representations.idm.GroupRepresentation;

class GroupResourceHandlerTest {

  private Keycloak keycloakClient;
  private ResultPublisher resultPublisher;
  private RoleSyncHelper roleSyncHelper;
  private GroupResourceHandler handler;

  @BeforeEach
  void setUp() {
    keycloakClient = mock(Keycloak.class);
    resultPublisher = mock(ResultPublisher.class);
    roleSyncHelper = mock(RoleSyncHelper.class);
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    handler =
        new GroupResourceHandler(keycloakClient, objectMapper, resultPublisher, roleSyncHelper);
  }

  private ConfigEvent groupEvent(String groupName, String parentId) {
    GroupConfig groupConfig = new GroupConfig();
    groupConfig.setName(groupName);
    groupConfig.setParentId(parentId);
    return new ConfigEvent(
        new Metadata(null, null, null, null, null, null),
        new Payload("GROUP", "test-realm", Operation.CREATE, new Config(null, groupConfig)));
  }

  private ConfigEvent groupEvent(String groupName) {
    return groupEvent(groupName, null);
  }

  @Nested
  @DisplayName("create")
  class Create {

    private Response fakeCreatedResponse(String id) {
      return Response.created(URI.create("http://localhost/groups/" + id)).build();
    }

    @Test
    @DisplayName("creates root group and publishes success")
    void shouldCreateRootGroupAndPublish() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      GroupsResource groupsResource = mock(GroupsResource.class);
      GroupResource groupResource = mock(GroupResource.class);
      RoleMappingResource roleMapping = mock(RoleMappingResource.class);
      RoleScopeResource roleScopeResource = mock(RoleScopeResource.class);

      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.groups()).thenReturn(groupsResource);
      when(groupsResource.add(any(GroupRepresentation.class)))
          .thenReturn(fakeCreatedResponse("group-id"));
      when(groupsResource.group("group-id")).thenReturn(groupResource);
      when(groupResource.roles()).thenReturn(roleMapping);
      when(roleMapping.realmLevel()).thenReturn(roleScopeResource);
      when(roleScopeResource.listAll()).thenReturn(List.of());

      ConfigEvent event = groupEvent("test-group");
      handler.create("test-realm", event);

      verify(groupsResource).add(any(GroupRepresentation.class));
      verify(resultPublisher).publish(event, SuccessCode.GROUP_CREATE_SUCCESS, "group-id");
    }

    @Test
    @DisplayName("creates subgroup under parent")
    void shouldCreateSubgroup() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      GroupsResource groupsResource = mock(GroupsResource.class);
      GroupResource parentGroupResource = mock(GroupResource.class);
      GroupResource groupResource = mock(GroupResource.class);
      RoleMappingResource roleMapping = mock(RoleMappingResource.class);
      RoleScopeResource roleScopeResource = mock(RoleScopeResource.class);

      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.groups()).thenReturn(groupsResource);
      when(groupsResource.group("parent-id")).thenReturn(parentGroupResource);
      when(parentGroupResource.subGroup(any(GroupRepresentation.class)))
          .thenReturn(fakeCreatedResponse("child-id"));
      when(groupsResource.group("child-id")).thenReturn(groupResource);
      when(groupResource.roles()).thenReturn(roleMapping);
      when(roleMapping.realmLevel()).thenReturn(roleScopeResource);
      when(roleScopeResource.listAll()).thenReturn(List.of());

      ConfigEvent event = groupEvent("child-group", "parent-id");
      handler.create("test-realm", event);

      verify(parentGroupResource).subGroup(any(GroupRepresentation.class));
      verify(resultPublisher).publish(event, SuccessCode.GROUP_CREATE_SUCCESS, "child-id");
    }
  }

  @Nested
  @DisplayName("update")
  class Update {

    @Test
    @DisplayName("updates group and publishes success")
    void shouldUpdateAndPublish() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      GroupsResource groupsResource = mock(GroupsResource.class);
      GroupResource groupResource = mock(GroupResource.class);
      RoleMappingResource roleMapping = mock(RoleMappingResource.class);
      RoleScopeResource roleScopeResource = mock(RoleScopeResource.class);

      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.groups()).thenReturn(groupsResource);
      when(groupsResource.group("group-id")).thenReturn(groupResource);
      when(groupResource.roles()).thenReturn(roleMapping);
      when(roleMapping.realmLevel()).thenReturn(roleScopeResource);
      when(roleScopeResource.listAll()).thenReturn(List.of());

      ConfigEvent event = groupEvent("test-group");
      handler.update("test-realm", "group-id", event);

      verify(groupResource).update(any(GroupRepresentation.class));
      verify(resultPublisher).publish(event, SuccessCode.GROUP_UPDATE_SUCCESS, "group-id");
    }

    @Test
    @DisplayName("throws FatalAdapterException when group not found")
    void shouldThrowFatalWhenNotFound() {
      RealmResource realmResource = mock(RealmResource.class);
      GroupsResource groupsResource = mock(GroupsResource.class);
      GroupResource groupResource = mock(GroupResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.groups()).thenReturn(groupsResource);
      when(groupsResource.group("missing")).thenReturn(groupResource);
      doThrow(new NotFoundException()).when(groupResource).update(any(GroupRepresentation.class));

      ConfigEvent event = groupEvent("test-group");
      assertThrows(
          FatalAdapterException.class, () -> handler.update("test-realm", "missing", event));
    }
  }

  @Nested
  @DisplayName("delete")
  class Delete {

    @Test
    @DisplayName("deletes group and publishes success")
    void shouldDeleteAndPublish() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      GroupsResource groupsResource = mock(GroupsResource.class);
      GroupResource groupResource = mock(GroupResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.groups()).thenReturn(groupsResource);
      when(groupsResource.group("group-id")).thenReturn(groupResource);

      ConfigEvent event = groupEvent("test-group");
      handler.delete("test-realm", "group-id", event);

      verify(groupResource).remove();
      verify(resultPublisher).publish(event, SuccessCode.GROUP_DELETE_SUCCESS, "group-id");
    }

    @Test
    @DisplayName("treats NotFoundException on delete as success (idempotent)")
    void shouldTreatNotFoundAsSuccess() throws Exception {
      RealmResource realmResource = mock(RealmResource.class);
      GroupsResource groupsResource = mock(GroupsResource.class);
      GroupResource groupResource = mock(GroupResource.class);
      when(keycloakClient.realm("test-realm")).thenReturn(realmResource);
      when(realmResource.groups()).thenReturn(groupsResource);
      when(groupsResource.group("gone")).thenReturn(groupResource);
      doThrow(new NotFoundException()).when(groupResource).remove();

      ConfigEvent event = groupEvent("test-group");
      handler.delete("test-realm", "gone", event);

      verify(resultPublisher).publish(event, SuccessCode.GROUP_DELETE_SUCCESS, "gone");
    }
  }
}
