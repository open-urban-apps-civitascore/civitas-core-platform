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
import de.civitascore.configadapter.model.idm.RoleConfig;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.representations.idm.RoleRepresentation;

class KeycloakRoleIntegrationTest extends KeycloakAdapterIntegrationTestBase {

  @Test
  void shouldCreateRole() throws FatalAdapterException, RetryableAdapterException {
    createRealm("role-create-realm");

    RoleConfig roleConfig = new RoleConfig();
    roleConfig.setName("testrole");

    adapter.processConfigEvent(
        Topics.ROLE_CREATED.toString(),
        createConfigEvent("role-create-realm", "role", Operation.CREATE, roleConfig));

    List<RoleRepresentation> roles = keycloakClient.realm("role-create-realm").roles().list();
    assertTrue(
        roles.stream().anyMatch(r -> r.getName().equals("testrole")), "Role 'testrole' not found");
    assertSingleSuccessResult();
  }

  @Test
  void shouldCreateRoleWithCompositeRole() throws FatalAdapterException, RetryableAdapterException {
    createRealm("role-nested-create-realm");

    RoleRepresentation nestedRoleRep = new RoleRepresentation();
    nestedRoleRep.setName("nestedtestrole");
    keycloakClient.realm("role-nested-create-realm").roles().create(nestedRoleRep);

    RoleConfig roleConfig = new RoleConfig();
    roleConfig.setName("testrole");
    roleConfig.setComposite(true);
    roleConfig.setCompositeRoles(Set.of("nestedtestrole"));

    adapter.processConfigEvent(
        Topics.ROLE_CREATED.toString(),
        createConfigEvent("role-nested-create-realm", "role", Operation.CREATE, roleConfig));

    RoleResource roleResource =
        keycloakClient.realm("role-nested-create-realm").roles().get("testrole");
    assertTrue(roleResource.toRepresentation().isComposite(), "'testrole' is not composite");
    Set<RoleRepresentation> compositeRoles = roleResource.getRealmRoleComposites();
    assertEquals(1, compositeRoles.size());
    assertTrue(
        compositeRoles.stream().anyMatch(r -> r.getName().equals("nestedtestrole")),
        "'nestedtestrole' not found in composite roles");
    assertSingleSuccessResult();
  }

  @Test
  void shouldUpdateRole() throws FatalAdapterException, RetryableAdapterException {
    createRealm("role-update-realm");

    RoleRepresentation initialRoleRep = new RoleRepresentation();
    initialRoleRep.setName("testrole");
    keycloakClient.realm("role-update-realm").roles().create(initialRoleRep);

    RoleConfig roleConfig = new RoleConfig();
    roleConfig.setName("testrole");
    roleConfig.setDescription("new description");

    adapter.processConfigEvent(
        Topics.ROLE_UPDATED.toString(),
        createConfigEvent("role-update-realm", "role", Operation.UPDATE, roleConfig));

    RoleRepresentation updatedRole =
        keycloakClient.realm("role-update-realm").roles().get("testrole").toRepresentation();
    assertEquals("new description", updatedRole.getDescription());
    assertSingleSuccessResult();
  }

  @Test
  void shouldDeleteRole() throws FatalAdapterException, RetryableAdapterException {
    createRealm("role-delete-realm");

    RoleRepresentation initialRoleRep = new RoleRepresentation();
    initialRoleRep.setName("testrole");
    keycloakClient.realm("role-delete-realm").roles().create(initialRoleRep);

    RoleConfig roleConfig = new RoleConfig();
    roleConfig.setName("testrole");

    adapter.processConfigEvent(
        Topics.ROLE_DELETED.toString(),
        createConfigEvent("role-delete-realm", "role", Operation.DELETE, roleConfig));

    List<RoleRepresentation> roles = keycloakClient.realm("role-delete-realm").roles().list();
    assertFalse(
        roles.stream().anyMatch(r -> r.getName().equals("testrole")), "Role should be deleted");
    assertSingleSuccessResult();
  }

  @Test
  void deleteRole_whenRoleDoesNotExist_shouldPublishSuccessWithoutException()
      throws FatalAdapterException, RetryableAdapterException {
    createRealm("idempotent-role-realm");

    RoleConfig roleConfig = new RoleConfig();
    roleConfig.setName("non-existent-role");

    adapter.processConfigEvent(
        Topics.ROLE_DELETED.toString(),
        createConfigEvent("idempotent-role-realm", "role", Operation.DELETE, roleConfig));

    assertSingleSuccessResult();
  }

  @Test
  void createRole_whenRoleAlreadyExists_shouldPublishSuccessWithoutException()
      throws FatalAdapterException, RetryableAdapterException {
    createRealm("duplicate-role-realm");

    RoleRepresentation existingRole = new RoleRepresentation();
    existingRole.setName("duplicate-role");
    keycloakClient.realm("duplicate-role-realm").roles().create(existingRole);
    eventPublisher.clear();

    RoleConfig roleConfig = new RoleConfig();
    roleConfig.setName("duplicate-role");

    adapter.processConfigEvent(
        Topics.ROLE_CREATED.toString(),
        createConfigEvent("duplicate-role-realm", "role", Operation.CREATE, roleConfig));

    assertSingleSuccessResult();
  }
}
