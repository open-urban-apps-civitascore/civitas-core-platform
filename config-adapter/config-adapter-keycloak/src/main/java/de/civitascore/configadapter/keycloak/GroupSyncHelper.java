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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared user-group membership synchronization logic used by {@link UserResourceHandler}. */
class GroupSyncHelper {

  private static final Logger logger = LoggerFactory.getLogger(GroupSyncHelper.class);

  /**
   * Page size for paginating a user's group memberships. Keycloak's default cap when pagination is
   * not requested is around 100; users with more memberships would silently lose the overflow.
   */
  private static final int GROUPS_PAGE_SIZE = 100;

  /**
   * Defensive cap on pagination iterations. Termination relies on Keycloak returning a partial or
   * empty last page; a misbehaving server returning a full page indefinitely would otherwise spin
   * forever. At {@code GROUPS_PAGE_SIZE} = 100 this caps the read at 100k memberships per user, far
   * above realistic usage.
   */
  private static final int MAX_PAGE_ITERATIONS = 1000;

  /**
   * Reconciles a user's group memberships with the desired set: removes memberships not in the
   * desired set, joins memberships that are missing. A {@code null} desired list is treated as an
   * empty set (remove all memberships). Membership sync keys on stable group externalIds (Keycloak
   * UUIDs), not on group names — concurrent portal-side renames cannot clobber memberships during
   * the rename window. Groups that don't exist in Keycloak (stale externalId) are logged and
   * skipped (warning), as are race-condition NotFoundExceptions on leaveGroup/joinGroup. Other
   * Keycloak failures (5xx, network) propagate so the event is DLQ'd rather than silently leaving
   * the user partially synced.
   */
  void syncUserGroups(List<String> desiredGroupIds, String userId, RealmResource realmResource) {
    Set<String> desired =
        desiredGroupIds != null ? new HashSet<>(desiredGroupIds) : Collections.emptySet();
    List<GroupRepresentation> currentGroups = fetchAllGroups(realmResource.users().get(userId));
    Set<String> currentGroupIds =
        currentGroups.stream().map(GroupRepresentation::getId).collect(Collectors.toSet());

    for (GroupRepresentation group : currentGroups) {
      if (!desired.contains(group.getId())) {
        try {
          realmResource.users().get(userId).leaveGroup(group.getId());
          logger.info(
              "Removed user {} from group {} ('{}')",
              Encode.forJava(KeycloakErrorHandler.maskId(userId)),
              Encode.forJava(KeycloakErrorHandler.maskId(group.getId())),
              Encode.forJava(group.getName()));
        } catch (NotFoundException e) {
          logger.warn(
              "Group {} ('{}') already gone for user {} during leave — skipping.",
              Encode.forJava(KeycloakErrorHandler.maskId(group.getId())),
              Encode.forJava(group.getName()),
              Encode.forJava(KeycloakErrorHandler.maskId(userId)));
        } catch (WebApplicationException e) {
          logger.error(
              "Failed to remove user {} from group {}: {}",
              Encode.forJava(KeycloakErrorHandler.maskId(userId)),
              Encode.forJava(KeycloakErrorHandler.maskId(group.getId())),
              e.getResponse().getStatus());
          throw e;
        }
      }
    }

    for (String groupId : desired) {
      if (!currentGroupIds.contains(groupId)) {
        try {
          realmResource.users().get(userId).joinGroup(groupId);
          logger.info(
              "Added user {} to group {}",
              Encode.forJava(KeycloakErrorHandler.maskId(userId)),
              Encode.forJava(KeycloakErrorHandler.maskId(groupId)));
        } catch (NotFoundException e) {
          logger.warn(
              "Group {} not found in Keycloak for user {} — skipping assignment. "
                  + "Group may not have been synced yet; will retry on next user update.",
              Encode.forJava(KeycloakErrorHandler.maskId(groupId)),
              Encode.forJava(KeycloakErrorHandler.maskId(userId)));
        } catch (WebApplicationException e) {
          logger.error(
              "Failed to add user {} to group {}: {}",
              Encode.forJava(KeycloakErrorHandler.maskId(userId)),
              Encode.forJava(KeycloakErrorHandler.maskId(groupId)),
              e.getResponse().getStatus());
          throw e;
        }
      }
    }
  }

  private List<GroupRepresentation> fetchAllGroups(UserResource userResource) {
    List<GroupRepresentation> all = new ArrayList<>();
    int first = 0;
    for (int iteration = 0; iteration < MAX_PAGE_ITERATIONS; iteration++) {
      List<GroupRepresentation> page = userResource.groups(first, GROUPS_PAGE_SIZE);
      if (page == null || page.isEmpty()) {
        return all;
      }
      all.addAll(page);
      if (page.size() < GROUPS_PAGE_SIZE) {
        return all;
      }
      first += GROUPS_PAGE_SIZE;
    }
    logger.error(
        "Pagination iteration cap ({}) reached while fetching user memberships — truncating at {} groups",
        MAX_PAGE_ITERATIONS,
        all.size());
    return all;
  }
}
