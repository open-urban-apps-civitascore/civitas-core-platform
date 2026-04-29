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
import jakarta.ws.rs.WebApplicationException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared user-group membership synchronization logic used by {@link UserResourceHandler}. */
class GroupSyncHelper {

  private static final Logger logger = LoggerFactory.getLogger(GroupSyncHelper.class);

  /**
   * Reconciles a user's group memberships with the desired set: removes memberships not in the
   * desired set, joins memberships that are missing. A {@code null} desired list is treated as an
   * empty set (remove all memberships). Group lookup uses exact-name matching. Groups that don't
   * exist in Keycloak are logged and skipped (warning), as are race-condition NotFoundExceptions on
   * leaveGroup/joinGroup. Other Keycloak failures (5xx, network) propagate so the event is DLQ'd
   * rather than silently leaving the user partially synced.
   */
  void syncUserGroups(List<String> desiredGroupNames, String userId, RealmResource realmResource) {
    Set<String> desired =
        desiredGroupNames != null ? new HashSet<>(desiredGroupNames) : Collections.emptySet();
    List<GroupRepresentation> currentGroups = realmResource.users().get(userId).groups();
    Set<String> currentGroupNames =
        currentGroups.stream().map(GroupRepresentation::getName).collect(Collectors.toSet());

    for (GroupRepresentation group : currentGroups) {
      if (!desired.contains(group.getName())) {
        try {
          realmResource.users().get(userId).leaveGroup(group.getId());
          logger.info(
              "Removed user {} from group '{}'",
              Encode.forJava(KeycloakErrorHandler.maskId(userId)),
              Encode.forJava(group.getName()));
        } catch (NotFoundException e) {
          logger.warn(
              "Group '{}' (id={}) already gone for user {} during leave — skipping.",
              Encode.forJava(group.getName()),
              Encode.forJava(KeycloakErrorHandler.maskId(group.getId())),
              Encode.forJava(KeycloakErrorHandler.maskId(userId)));
        } catch (WebApplicationException e) {
          logger.error(
              "Failed to remove user {} from group '{}': {}",
              Encode.forJava(KeycloakErrorHandler.maskId(userId)),
              Encode.forJava(group.getName()),
              e.getResponse().getStatus());
          throw e;
        }
      }
    }

    for (String groupName : desired) {
      if (!currentGroupNames.contains(groupName)) {
        List<GroupRepresentation> found =
            realmResource.groups().groups(groupName, true, 0, 1, true);
        if (found.isEmpty()) {
          logger.warn(
              "Group '{}' not found in Keycloak, skipping assignment.", Encode.forJava(groupName));
          continue;
        }
        try {
          realmResource.users().get(userId).joinGroup(found.getFirst().getId());
          logger.info(
              "Added user {} to group '{}'",
              Encode.forJava(KeycloakErrorHandler.maskId(userId)),
              Encode.forJava(groupName));
        } catch (NotFoundException e) {
          logger.warn(
              "Group '{}' disappeared between lookup and join for user {} — skipping.",
              Encode.forJava(groupName),
              Encode.forJava(KeycloakErrorHandler.maskId(userId)));
        } catch (WebApplicationException e) {
          logger.error(
              "Failed to add user {} to group '{}': {}",
              Encode.forJava(KeycloakErrorHandler.maskId(userId)),
              Encode.forJava(groupName),
              e.getResponse().getStatus());
          throw e;
        }
      }
    }
  }
}
