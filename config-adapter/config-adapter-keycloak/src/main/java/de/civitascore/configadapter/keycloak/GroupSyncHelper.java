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
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared group-membership synchronization logic. Used from the user side by {@link
 * UserResourceHandler} (reconcile one user's groups) and from the group side by {@link
 * GroupResourceHandler} (reconcile one group's members).
 */
class GroupSyncHelper {

  private static final Logger logger = LoggerFactory.getLogger(GroupSyncHelper.class);

  /**
   * Page size for paginating membership listings. Keycloak's default cap when pagination is not
   * requested is around 100; listings beyond that would silently lose the overflow.
   */
  private static final int KEYCLOAK_PAGE_SIZE = 100;

  /**
   * Defensive cap on pagination iterations. Termination relies on Keycloak returning a partial or
   * empty last page; a misbehaving server returning a full page indefinitely would otherwise spin
   * forever. At {@code KEYCLOAK_PAGE_SIZE} = 100 this caps the read at 100k entries per listing,
   * far above realistic usage.
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
    List<GroupRepresentation> currentGroups =
        fetchAllPaged(
            first -> realmResource.users().get(userId).groups(first, KEYCLOAK_PAGE_SIZE),
            "user memberships");
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

  /**
   * Reconciles a group's members with the desired set from the group side: joins users in the
   * desired set that are not yet members, removes members not in the desired set. Keys on stable
   * user externalIds (Keycloak UUIDs).
   *
   * <p>A {@code null} desired set means "not provided" and reconciliation is skipped entirely — the
   * group payload omits members unless the caller populated them, so a null must not wipe existing
   * memberships. This differs from {@link #syncUserGroups} (which treats null as remove-all). An
   * empty set removes all members.
   *
   * <p>Users that don't exist in Keycloak (stale externalId, e.g. not synced yet) are logged and
   * skipped (warning), as are race-condition {@link NotFoundException}s on join/leave. Other
   * Keycloak failures propagate so the event fails (retried, then DLQ'd) and is re-reconciled
   * idempotently rather than reported as success — a mid-reconcile throw leaves the group partial
   * until that reprocessing.
   *
   * <p>This group-side reconcile and the user-side {@link #syncUserGroups} both derive from the
   * same portal {@code group_members} truth but are dispatched as independent events. A concurrent
   * group- and user-side update on the same relation may interleave, but each side is idempotent
   * and re-applies the full desired set, so the state converges once a subsequent event on either
   * side carries current truth.
   */
  void syncGroupMembers(Set<String> desiredUserIds, String groupId, RealmResource realmResource) {
    if (desiredUserIds == null) {
      return;
    }
    GroupResource groupResource = realmResource.groups().group(groupId);
    Set<String> currentMemberIds =
        fetchAllPaged(first -> groupResource.members(first, KEYCLOAK_PAGE_SIZE), "group members")
            .stream()
            .map(UserRepresentation::getId)
            .collect(Collectors.toSet());

    for (String userId : currentMemberIds) {
      if (!desiredUserIds.contains(userId)) {
        try {
          realmResource.users().get(userId).leaveGroup(groupId);
          logger.info(
              "Removed user {} from group {}",
              Encode.forJava(KeycloakErrorHandler.maskId(userId)),
              Encode.forJava(KeycloakErrorHandler.maskId(groupId)));
        } catch (NotFoundException e) {
          logger.warn(
              "User {} already gone during leave from group {} — skipping.",
              Encode.forJava(KeycloakErrorHandler.maskId(userId)),
              Encode.forJava(KeycloakErrorHandler.maskId(groupId)));
        } catch (WebApplicationException e) {
          logger.error(
              "Failed to remove user {} from group {}: {}",
              Encode.forJava(KeycloakErrorHandler.maskId(userId)),
              Encode.forJava(KeycloakErrorHandler.maskId(groupId)),
              e.getResponse().getStatus());
          throw e;
        }
      }
    }

    for (String userId : desiredUserIds) {
      if (!currentMemberIds.contains(userId)) {
        try {
          realmResource.users().get(userId).joinGroup(groupId);
          logger.info(
              "Added user {} to group {}",
              Encode.forJava(KeycloakErrorHandler.maskId(userId)),
              Encode.forJava(KeycloakErrorHandler.maskId(groupId)));
        } catch (NotFoundException e) {
          logger.warn(
              "User {} not found in Keycloak for group {} — skipping assignment. "
                  + "User may not have been synced yet; will retry on next update.",
              Encode.forJava(KeycloakErrorHandler.maskId(userId)),
              Encode.forJava(KeycloakErrorHandler.maskId(groupId)));
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

  /**
   * Reads all pages of a Keycloak listing via {@code pageAt}, which fetches the page starting at
   * the given offset. Termination relies on a partial/empty last page; {@link #MAX_PAGE_ITERATIONS}
   * guards against a server that never returns one. {@code listingLabel} names the listing for the
   * cap-reached log.
   */
  private <T> List<T> fetchAllPaged(IntFunction<List<T>> pageAt, String listingLabel) {
    List<T> all = new ArrayList<>();
    int first = 0;
    for (int iteration = 0; iteration < MAX_PAGE_ITERATIONS; iteration++) {
      List<T> page = pageAt.apply(first);
      if (page == null || page.isEmpty()) {
        return all;
      }
      all.addAll(page);
      if (page.size() < KEYCLOAK_PAGE_SIZE) {
        return all;
      }
      first += KEYCLOAK_PAGE_SIZE;
    }
    logger.error(
        "Pagination iteration cap ({}) reached while fetching {} — truncating at {} entries",
        MAX_PAGE_ITERATIONS,
        listingLabel,
        all.size());
    return all;
  }
}
