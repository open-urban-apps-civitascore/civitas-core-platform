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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

class GroupSyncHelperTest {

  private GroupSyncHelper helper;
  private RealmResource realmResource;
  private UsersResource usersResource;
  private UserResource userResource;

  private static final String USER_ID = "user-1";

  @BeforeEach
  void setUp() {
    helper = new GroupSyncHelper();
    realmResource = mock(RealmResource.class);
    usersResource = mock(UsersResource.class);
    userResource = mock(UserResource.class);

    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.get(USER_ID)).thenReturn(userResource);
  }

  private GroupRepresentation group(String id, String name) {
    GroupRepresentation g = new GroupRepresentation();
    g.setId(id);
    g.setName(name);
    return g;
  }

  @Nested
  @DisplayName("syncGroupMembers (group side)")
  class SyncGroupMembers {

    private static final String GROUP_ID = "group-1";

    private GroupResource groupResource;

    @BeforeEach
    void setUpGroup() {
      GroupsResource groupsResource = mock(GroupsResource.class);
      groupResource = mock(GroupResource.class);
      when(realmResource.groups()).thenReturn(groupsResource);
      when(groupsResource.group(GROUP_ID)).thenReturn(groupResource);
    }

    private UserRepresentation user(String id) {
      UserRepresentation u = new UserRepresentation();
      u.setId(id);
      return u;
    }

    private UserResource userResourceFor(String id) {
      UserResource r = mock(UserResource.class);
      when(usersResource.get(id)).thenReturn(r);
      return r;
    }

    @Test
    @DisplayName("null desired set skips reconciliation entirely (never wipes members)")
    void shouldSkipWhenDesiredNull() {
      helper.syncGroupMembers(null, GROUP_ID, realmResource);

      verify(groupResource, never()).members(anyInt(), anyInt());
    }

    @Test
    @DisplayName("adds missing members and removes stale members (keyed on user externalId)")
    void shouldAddAndRemoveMembers() {
      when(groupResource.members(0, 100)).thenReturn(List.of(user("keep-id"), user("stale-id")));
      UserResource stale = userResourceFor("stale-id");
      UserResource added = userResourceFor("new-id");
      UserResource kept = userResourceFor("keep-id");

      helper.syncGroupMembers(Set.of("keep-id", "new-id"), GROUP_ID, realmResource);

      verify(stale).leaveGroup(GROUP_ID);
      verify(added).joinGroup(GROUP_ID);
      verify(kept, never()).leaveGroup(GROUP_ID);
    }

    @Test
    @DisplayName("NotFoundException on leave (raced removal) is logged and does not abort the loop")
    void shouldContinueWhenLeaveRaces() {
      when(groupResource.members(0, 100)).thenReturn(List.of(user("gone-id"), user("other-id")));
      UserResource gone = userResourceFor("gone-id");
      UserResource other = userResourceFor("other-id");
      doThrow(new NotFoundException()).when(gone).leaveGroup(GROUP_ID);

      helper.syncGroupMembers(Collections.emptySet(), GROUP_ID, realmResource);

      verify(gone).leaveGroup(GROUP_ID);
      verify(other).leaveGroup(GROUP_ID);
    }

    @Test
    @DisplayName("WebApplicationException on leave propagates so the event is DLQ'd")
    void shouldPropagateServerErrorOnLeave() {
      when(groupResource.members(0, 100)).thenReturn(List.of(user("u")));
      UserResource failing = userResourceFor("u");
      doThrow(new WebApplicationException(Response.serverError().build()))
          .when(failing)
          .leaveGroup(GROUP_ID);

      assertThrows(
          WebApplicationException.class,
          () -> helper.syncGroupMembers(Collections.emptySet(), GROUP_ID, realmResource));
    }

    @Test
    @DisplayName("empty desired set removes all current members")
    void shouldRemoveAllWhenEmpty() {
      when(groupResource.members(0, 100)).thenReturn(List.of(user("a"), user("b")));
      UserResource a = userResourceFor("a");
      UserResource b = userResourceFor("b");

      helper.syncGroupMembers(Collections.emptySet(), GROUP_ID, realmResource);

      verify(a).leaveGroup(GROUP_ID);
      verify(b).leaveGroup(GROUP_ID);
    }

    @Test
    @DisplayName("NotFoundException on join (unsynced user) is logged and does not abort the loop")
    void shouldSkipUnsyncedUserOnJoin() {
      when(groupResource.members(0, 100)).thenReturn(Collections.emptyList());
      UserResource ghost = userResourceFor("ghost-id");
      UserResource present = userResourceFor("present-id");
      doThrow(new NotFoundException()).when(ghost).joinGroup(GROUP_ID);

      helper.syncGroupMembers(
          new LinkedHashSet<>(List.of("ghost-id", "present-id")), GROUP_ID, realmResource);

      verify(ghost).joinGroup(GROUP_ID);
      verify(present).joinGroup(GROUP_ID);
    }

    @Test
    @DisplayName("WebApplicationException on join propagates so the event is DLQ'd")
    void shouldPropagateServerErrorOnJoin() {
      when(groupResource.members(0, 100)).thenReturn(Collections.emptyList());
      UserResource failing = userResourceFor("u");
      doThrow(new WebApplicationException(Response.serverError().build()))
          .when(failing)
          .joinGroup(GROUP_ID);

      assertThrows(
          WebApplicationException.class,
          () -> helper.syncGroupMembers(Set.of("u"), GROUP_ID, realmResource));
    }

    @Test
    @DisplayName("paginates current members so groups with >100 members don't lose the overflow")
    void shouldPaginateCurrentMembers() {
      List<UserRepresentation> firstPage = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        firstPage.add(user("id-" + i));
      }
      when(groupResource.members(0, 100)).thenReturn(firstPage);
      // Second page is short (size 1 < KEYCLOAK_PAGE_SIZE), so fetchAllPaged stops here.
      when(groupResource.members(100, 100)).thenReturn(List.of(user("id-100")));
      UserResource overflow = userResourceFor("id-100");

      // desired keeps page 1 entirely; overflow id-100 is not desired and must be removed
      Set<String> desired = new HashSet<>();
      for (int i = 0; i < 100; i++) {
        desired.add("id-" + i);
      }

      helper.syncGroupMembers(desired, GROUP_ID, realmResource);

      verify(overflow).leaveGroup(GROUP_ID);
    }
  }

  @Test
  @DisplayName("adds missing groups and removes stale groups (keyed on externalId)")
  void shouldAddAndRemoveGroups() {
    GroupRepresentation keep = group("keep-id", "keep-group");
    GroupRepresentation stale = group("stale-id", "stale-group");
    when(userResource.groups(0, 100)).thenReturn(List.of(keep, stale));

    helper.syncUserGroups(List.of("keep-id", "new-id"), USER_ID, realmResource);

    verify(userResource).leaveGroup("stale-id");
    verify(userResource).joinGroup("new-id");
    verify(userResource, never()).leaveGroup("keep-id");
  }

  @Test
  @DisplayName("does nothing when group ids match")
  void shouldDoNothingWhenGroupsMatch() {
    GroupRepresentation existing = group("g-id", "existing-group");
    when(userResource.groups(0, 100)).thenReturn(List.of(existing));

    helper.syncUserGroups(List.of("g-id"), USER_ID, realmResource);

    verify(userResource, never()).leaveGroup(anyString());
    verify(userResource, never()).joinGroup(anyString());
  }

  @Test
  @DisplayName("null desired list removes all current memberships")
  void shouldRemoveAllWhenDesiredIsNull() {
    GroupRepresentation g1 = group("a", "alpha");
    GroupRepresentation g2 = group("b", "beta");
    when(userResource.groups(0, 100)).thenReturn(List.of(g1, g2));

    helper.syncUserGroups(null, USER_ID, realmResource);

    verify(userResource).leaveGroup("a");
    verify(userResource).leaveGroup("b");
    verify(userResource, never()).joinGroup(anyString());
  }

  @Test
  @DisplayName("empty desired list removes all current memberships")
  void shouldRemoveAllWhenDesiredIsEmpty() {
    GroupRepresentation g = group("g-id", "some-group");
    when(userResource.groups(0, 100)).thenReturn(List.of(g));

    helper.syncUserGroups(Collections.emptyList(), USER_ID, realmResource);

    verify(userResource).leaveGroup("g-id");
  }

  @Test
  @DisplayName("rename: same externalId in current and desired set ⇒ no membership churn")
  void shouldBeStableAcrossPortalRename() {
    // Portal renames "Editors" -> "Writers"; Keycloak side already reflects the new name
    // because GROUP_UPDATED was applied before this USER_UPDATED event. The user's desired
    // membership list still references the same externalId, so no leave/join is issued.
    GroupRepresentation renamed = group("kc-uuid-1", "Writers");
    when(userResource.groups(0, 100)).thenReturn(List.of(renamed));

    helper.syncUserGroups(List.of("kc-uuid-1"), USER_ID, realmResource);

    verify(userResource, never()).leaveGroup(anyString());
    verify(userResource, never()).joinGroup(anyString());
  }

  @Test
  @DisplayName("nonexistent group id is logged and skipped, does not throw")
  void shouldSkipNonexistentGroupId() {
    when(userResource.groups(0, 100)).thenReturn(Collections.emptyList());
    doThrow(new NotFoundException()).when(userResource).joinGroup("ghost-id");

    helper.syncUserGroups(List.of("ghost-id"), USER_ID, realmResource);

    verify(userResource).joinGroup("ghost-id");
  }

  @Test
  @DisplayName("NotFoundException on leaveGroup is logged and does not abort the loop")
  void shouldContinueWhenLeaveGroupRaces() {
    GroupRepresentation gone = group("gone-id", "gone-group");
    GroupRepresentation other = group("other-id", "other-group");
    when(userResource.groups(0, 100)).thenReturn(List.of(gone, other));

    doThrow(new NotFoundException()).when(userResource).leaveGroup("gone-id");

    helper.syncUserGroups(List.of(), USER_ID, realmResource);

    verify(userResource).leaveGroup("gone-id");
    verify(userResource).leaveGroup("other-id");
  }

  @Test
  @DisplayName("WebApplicationException on leaveGroup propagates so the event is DLQ'd")
  void shouldPropagate5xxOnLeaveGroup() {
    GroupRepresentation g = group("g-id", "g");
    when(userResource.groups(0, 100)).thenReturn(List.of(g));
    doThrow(new WebApplicationException(Response.serverError().build()))
        .when(userResource)
        .leaveGroup("g-id");

    assertThrows(
        WebApplicationException.class,
        () -> helper.syncUserGroups(List.of(), USER_ID, realmResource));
  }

  @Test
  @DisplayName("NotFoundException on joinGroup is logged and does not abort the loop")
  void shouldContinueWhenJoinGroupRaces() {
    when(userResource.groups(0, 100)).thenReturn(Collections.emptyList());
    doThrow(new NotFoundException()).when(userResource).joinGroup("raced-id");

    helper.syncUserGroups(List.of("raced-id", "present-id"), USER_ID, realmResource);

    verify(userResource).joinGroup("raced-id");
    verify(userResource).joinGroup("present-id");
  }

  @Test
  @DisplayName("WebApplicationException on joinGroup propagates so the event is DLQ'd")
  void shouldPropagate5xxOnJoinGroup() {
    when(userResource.groups(0, 100)).thenReturn(Collections.emptyList());
    doThrow(new WebApplicationException(Response.serverError().build()))
        .when(userResource)
        .joinGroup("g-id");

    assertThrows(
        WebApplicationException.class,
        () -> helper.syncUserGroups(List.of("g-id"), USER_ID, realmResource));
  }

  @Test
  @DisplayName("paginates current memberships so users with >100 groups don't lose the overflow")
  void shouldPaginateCurrentMemberships() {
    List<GroupRepresentation> firstPage = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      firstPage.add(group("id-" + i, "group-" + i));
    }
    GroupRepresentation overflow = group("id-100", "group-100");
    when(userResource.groups(0, 100)).thenReturn(firstPage);
    when(userResource.groups(100, 100)).thenReturn(List.of(overflow));
    when(userResource.groups(200, 100)).thenReturn(Collections.emptyList());

    // desired keeps page 1 entirely; overflow id-100 is not desired and must be removed
    List<String> desired = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      desired.add("id-" + i);
    }

    helper.syncUserGroups(desired, USER_ID, realmResource);

    verify(userResource).leaveGroup("id-100");
    verify(userResource, never()).leaveGroup("id-0");
  }
}
