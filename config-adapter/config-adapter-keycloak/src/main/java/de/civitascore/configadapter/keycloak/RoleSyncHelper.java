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

import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared role synchronization logic used by {@link UserResourceHandler} and {@link
 * GroupResourceHandler}.
 */
class RoleSyncHelper {

  private static final Logger logger = LoggerFactory.getLogger(RoleSyncHelper.class);

  void syncRealmRoles(
      Set<String> desiredRoleNames,
      RoleMappingResource roleMappingResource,
      RealmResource realmResource) {
    Set<String> desired = desiredRoleNames != null ? desiredRoleNames : Collections.emptySet();
    RoleScopeResource roleScope = roleMappingResource.realmLevel();
    List<RoleRepresentation> currentRoles = roleScope.listAll();

    RoleDiff diff = computeRealmRoleDiff(currentRoles, desired, realmResource);
    applyRoleChanges(roleScope, diff, "realm");
  }

  void syncClientRoles(
      Map<String, List<String>> clientRolesMap,
      RoleMappingResource roleMappingResource,
      RealmResource realmResource) {
    if (clientRolesMap == null || clientRolesMap.isEmpty()) {
      return;
    }

    for (Map.Entry<String, List<String>> entry : clientRolesMap.entrySet()) {
      syncRolesForClient(
          entry.getKey(), new HashSet<>(entry.getValue()), roleMappingResource, realmResource);
    }
  }

  private RoleDiff computeRealmRoleDiff(
      List<RoleRepresentation> currentRoles,
      Set<String> desiredRoles,
      RealmResource realmResource) {
    Set<String> currentRoleNames =
        currentRoles.stream().map(RoleRepresentation::getName).collect(Collectors.toSet());

    List<RoleRepresentation> toRemove =
        currentRoles.stream().filter(r -> !desiredRoles.contains(r.getName())).toList();

    List<RoleRepresentation> toAdd = new ArrayList<>();
    for (String desiredName : desiredRoles) {
      if (!currentRoleNames.contains(desiredName)) {
        try {
          toAdd.add(realmResource.roles().get(desiredName).toRepresentation());
        } catch (NotFoundException e) {
          logger.warn(
              "Realm Role '{}' not found, skipping assignment.", Encode.forJava(desiredName));
        }
      }
    }
    return new RoleDiff(toAdd, toRemove);
  }

  private void syncRolesForClient(
      String clientId,
      Set<String> desiredRoles,
      RoleMappingResource roleMappingResource,
      RealmResource realmResource) {
    List<ClientRepresentation> clients = realmResource.clients().findByClientId(clientId);
    if (clients.isEmpty()) {
      logger.warn("Client '{}' not found, skipping role sync.", Encode.forJava(clientId));
      return;
    }

    String clientUuid = clients.getFirst().getId();
    RoleScopeResource clientRoleScope = roleMappingResource.clientLevel(clientUuid);
    List<RoleRepresentation> currentRoles = clientRoleScope.listAll();

    RoleDiff diff =
        computeClientRoleDiff(currentRoles, desiredRoles, clientUuid, clientId, realmResource);
    applyRoleChanges(clientRoleScope, diff, clientId);
  }

  private RoleDiff computeClientRoleDiff(
      List<RoleRepresentation> currentRoles,
      Set<String> desiredRoles,
      String clientUuid,
      String clientId,
      RealmResource realmResource) {
    Set<String> currentRoleNames =
        currentRoles.stream().map(RoleRepresentation::getName).collect(Collectors.toSet());

    List<RoleRepresentation> toRemove =
        currentRoles.stream().filter(r -> !desiredRoles.contains(r.getName())).toList();

    List<RoleRepresentation> toAdd = new ArrayList<>();
    for (String desiredName : desiredRoles) {
      if (!currentRoleNames.contains(desiredName)) {
        try {
          toAdd.add(
              realmResource.clients().get(clientUuid).roles().get(desiredName).toRepresentation());
        } catch (NotFoundException e) {
          logger.warn(
              "Role '{}' not found for client '{}'",
              Encode.forJava(desiredName),
              Encode.forJava(clientId));
        }
      }
    }
    return new RoleDiff(toAdd, toRemove);
  }

  private void applyRoleChanges(RoleScopeResource roleScope, RoleDiff diff, String context) {
    if (!diff.toRemove().isEmpty()) {
      roleScope.remove(diff.toRemove());
      logger.info(
          "Removed roles for {}: {}",
          Encode.forJava(context),
          Encode.forJava(
              String.valueOf(diff.toRemove().stream().map(RoleRepresentation::getName).toList())));
    }
    if (!diff.toAdd().isEmpty()) {
      roleScope.add(diff.toAdd());
      logger.info(
          "Added roles for {}: {}",
          Encode.forJava(context),
          Encode.forJava(
              String.valueOf(diff.toAdd().stream().map(RoleRepresentation::getName).toList())));
    }
  }

  record RoleDiff(List<RoleRepresentation> toAdd, List<RoleRepresentation> toRemove) {}
}
