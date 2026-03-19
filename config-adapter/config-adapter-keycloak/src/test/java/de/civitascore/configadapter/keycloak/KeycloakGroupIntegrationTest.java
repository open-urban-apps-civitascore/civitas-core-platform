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
import de.civitascore.configadapter.model.idm.GroupConfig;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;

class KeycloakGroupIntegrationTest extends KeycloakAdapterIntegrationTestBase {

  @Test
  void createGroup_whenValidConfig_shouldCreateGroupInRealm()
      throws FatalAdapterException, RetryableAdapterException {
    createRealm("group-create-realm");

    GroupConfig groupConfig = new GroupConfig();
    groupConfig.setName("testgroup");

    adapter.processConfigEvent(
        Topics.GROUP_CREATED.toString(),
        createConfigEvent("group-create-realm", "group", Operation.CREATE, groupConfig));

    List<GroupRepresentation> groups = keycloakClient.realm("group-create-realm").groups().groups();
    assertTrue(groups.stream().anyMatch(g -> g.getName().equals("testgroup")), "Group not found");
    assertSingleSuccessResult();
  }

  @Test
  void createGroup_whenConfigHasRealmRoles_shouldAssignRolesToGroup()
      throws FatalAdapterException, RetryableAdapterException {
    createRealm("group-roles-realm");

    RealmResource realmResource = keycloakClient.realm("group-roles-realm");
    RoleRepresentation roleRep = new RoleRepresentation();
    roleRep.setName("grouprole");
    realmResource.roles().create(roleRep);

    GroupConfig groupConfig = new GroupConfig();
    groupConfig.setName("testgroup-with-roles");
    groupConfig.setRealmRoles(Set.of("grouprole"));

    adapter.processConfigEvent(
        Topics.GROUP_CREATED.toString(),
        createConfigEvent("group-roles-realm", "group", Operation.CREATE, groupConfig));

    GroupRepresentation addedGroup =
        realmResource.groups().groups().stream()
            .filter(g -> g.getName().equals("testgroup-with-roles"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Group not found"));
    List<RoleRepresentation> groupRoles =
        realmResource.groups().group(addedGroup.getId()).roles().realmLevel().listAll();
    assertTrue(
        groupRoles.stream().anyMatch(r -> r.getName().equals("grouprole")),
        "Role not assigned to group");
    assertSingleSuccessResult();
  }

  @Test
  void createGroup_whenConfigHasParentId_shouldCreateAsSubgroup()
      throws FatalAdapterException, RetryableAdapterException {
    createRealm("subgroup-realm");

    RealmResource realmResource = keycloakClient.realm("subgroup-realm");
    GroupRepresentation parentGroupRep = new GroupRepresentation();
    parentGroupRep.setName("parent-group");
    String parentGroupId;
    try (Response response = realmResource.groups().add(parentGroupRep)) {
      assertEquals(201, response.getStatus(), "Creation of parent group failed");
      parentGroupId = CreatedResponseUtil.getCreatedId(response);
    }

    GroupConfig subGroupConfig = new GroupConfig();
    subGroupConfig.setName("child-group");
    subGroupConfig.setParentId(parentGroupId);

    adapter.processConfigEvent(
        Topics.GROUP_CREATED.toString(),
        createConfigEvent("subgroup-realm", "group", Operation.CREATE, subGroupConfig));

    List<GroupRepresentation> subGroups =
        realmResource.groups().group(parentGroupId).getSubGroups(0, 100, false);
    assertTrue(
        subGroups.stream().anyMatch(g -> g.getName().equals("child-group")),
        "Subgroup not created under parent");
    assertSingleSuccessResult();
  }

  @Test
  void updateGroup_whenValidConfig_shouldUpdateGroupAndAssignRoles()
      throws FatalAdapterException, RetryableAdapterException {
    createRealm("group-update-realm");

    RealmResource realmResource = keycloakClient.realm("group-update-realm");
    GroupRepresentation initialGroupRep = new GroupRepresentation();
    initialGroupRep.setName("update-group");
    String groupId;
    try (Response response = realmResource.groups().add(initialGroupRep)) {
      assertEquals(201, response.getStatus(), "Group creation failed");
      groupId = CreatedResponseUtil.getCreatedId(response);
    }

    RoleRepresentation roleRep = new RoleRepresentation();
    roleRep.setName("updatedrole");
    realmResource.roles().create(roleRep);

    GroupConfig updateConfig = new GroupConfig();
    updateConfig.setId(groupId);
    updateConfig.setName("update-group");
    updateConfig.setRealmRoles(Set.of("updatedrole"));

    adapter.processConfigEvent(
        Topics.GROUP_UPDATED.toString(),
        createConfigEvent("group-update-realm", "group", Operation.UPDATE, updateConfig));

    List<RoleRepresentation> groupRoles =
        realmResource.groups().group(groupId).roles().realmLevel().listAll();
    assertTrue(
        groupRoles.stream().anyMatch(r -> r.getName().equals("updatedrole")),
        "Role not assigned to group after update");
    assertSingleSuccessResult();
  }

  @Test
  void deleteGroup_whenValidConfig_shouldRemoveGroupFromRealm()
      throws FatalAdapterException, RetryableAdapterException {
    createRealm("group-delete-realm");

    GroupRepresentation initialGroupRep = new GroupRepresentation();
    initialGroupRep.setName("delete-group");
    String groupId;
    try (var response = keycloakClient.realm("group-delete-realm").groups().add(initialGroupRep)) {
      assertEquals(201, response.getStatus(), "Group creation failed");
      groupId = CreatedResponseUtil.getCreatedId(response);
    }

    GroupConfig deleteConfig = new GroupConfig();
    deleteConfig.setId(groupId);
    deleteConfig.setName("delete-group");

    adapter.processConfigEvent(
        Topics.GROUP_DELETED.toString(),
        createConfigEvent("group-delete-realm", "group", Operation.DELETE, deleteConfig));

    assertFalse(
        keycloakClient.realm("group-delete-realm").groups().groups().stream()
            .anyMatch(g -> g.getName().equals("delete-group")),
        "Group should be deleted");
    assertSingleSuccessResult();
  }

  @Test
  void updateGroup_whenRoleMissingInConfig_shouldRemoveRoleFromGroup()
      throws FatalAdapterException, RetryableAdapterException {
    createRealm("group-sync-realm");

    RealmResource realmResource = keycloakClient.realm("group-sync-realm");
    RolesResource rolesResource = realmResource.roles();
    RoleRepresentation roleX = new RoleRepresentation();
    roleX.setName("role-x");
    RoleRepresentation roleY = new RoleRepresentation();
    roleY.setName("role-y");
    rolesResource.create(roleX);
    rolesResource.create(roleY);

    GroupRepresentation groupRep = new GroupRepresentation();
    groupRep.setName("sync-group");
    String groupId;
    try (Response response = keycloakClient.realm("group-sync-realm").groups().add(groupRep)) {
      groupId = CreatedResponseUtil.getCreatedId(response);
      realmResource
          .groups()
          .group(groupId)
          .roles()
          .realmLevel()
          .add(
              List.of(
                  rolesResource.get("role-x").toRepresentation(),
                  rolesResource.get("role-y").toRepresentation()));
    }

    GroupConfig groupUpdateConfig = new GroupConfig();
    groupUpdateConfig.setId(groupId);
    groupUpdateConfig.setName("sync-group");
    groupUpdateConfig.setRealmRoles(Set.of("role-x"));

    adapter.processConfigEvent(
        Topics.GROUP_UPDATED.toString(),
        createConfigEvent("group-sync-realm", "group", Operation.UPDATE, groupUpdateConfig));

    List<RoleRepresentation> currentRoles =
        realmResource.groups().group(groupId).roles().realmLevel().listAll();
    assertTrue(
        currentRoles.stream().anyMatch(r -> r.getName().equals("role-x")),
        "role-x should still be assigned");
    assertFalse(
        currentRoles.stream().anyMatch(r -> r.getName().equals("role-y")),
        "role-y should have been removed");
  }

  @Test
  void deleteGroup_whenGroupDoesNotExist_shouldPublishSuccessWithoutException()
      throws FatalAdapterException, RetryableAdapterException {
    createRealm("idempotent-group-realm");

    GroupConfig groupConfig = new GroupConfig();
    groupConfig.setId(UUID.randomUUID().toString());
    groupConfig.setName("ghost-group");

    adapter.processConfigEvent(
        Topics.GROUP_DELETED.toString(),
        createConfigEvent("idempotent-group-realm", "group", Operation.DELETE, groupConfig));

    assertSingleSuccessResult();
  }

  @Test
  void createGroup_whenGroupAlreadyExists_shouldPublishSuccessWithoutException()
      throws FatalAdapterException, RetryableAdapterException {
    createRealm("duplicate-group-realm");

    GroupRepresentation existingGroup = new GroupRepresentation();
    existingGroup.setName("duplicate-group");
    try (Response response =
        keycloakClient.realm("duplicate-group-realm").groups().add(existingGroup)) {
      assertEquals(201, response.getStatus());
    }
    eventPublisher.clear();

    GroupConfig groupConfig = new GroupConfig();
    groupConfig.setName("duplicate-group");

    adapter.processConfigEvent(
        Topics.GROUP_CREATED.toString(),
        createConfigEvent("duplicate-group-realm", "group", Operation.CREATE, groupConfig));

    assertSingleSuccessResult();
  }
}
