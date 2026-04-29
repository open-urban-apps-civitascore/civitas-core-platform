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
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.GroupRepresentation;

class GroupSyncHelperTest {

  private GroupSyncHelper helper;
  private RealmResource realmResource;
  private UsersResource usersResource;
  private UserResource userResource;
  private GroupsResource groupsResource;

  private static final String USER_ID = "user-1";

  @BeforeEach
  void setUp() {
    helper = new GroupSyncHelper();
    realmResource = mock(RealmResource.class);
    usersResource = mock(UsersResource.class);
    userResource = mock(UserResource.class);
    groupsResource = mock(GroupsResource.class);

    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.get(USER_ID)).thenReturn(userResource);
    when(realmResource.groups()).thenReturn(groupsResource);
  }

  private GroupRepresentation group(String id, String name) {
    GroupRepresentation g = new GroupRepresentation();
    g.setId(id);
    g.setName(name);
    return g;
  }

  @Test
  @DisplayName("adds missing groups and removes stale groups")
  void shouldAddAndRemoveGroups() {
    GroupRepresentation keep = group("keep-id", "keep-group");
    GroupRepresentation stale = group("stale-id", "stale-group");
    when(userResource.groups(0, 100)).thenReturn(List.of(keep, stale));

    GroupRepresentation newGroup = group("new-id", "new-group");
    when(groupsResource.groups("new-group", true, 0, 1, true)).thenReturn(List.of(newGroup));

    helper.syncUserGroups(List.of("keep-group", "new-group"), USER_ID, realmResource);

    verify(userResource).leaveGroup("stale-id");
    verify(userResource).joinGroup("new-id");
    verify(userResource, never()).leaveGroup("keep-id");
  }

  @Test
  @DisplayName("does nothing when groups match")
  void shouldDoNothingWhenGroupsMatch() {
    GroupRepresentation existing = group("g-id", "existing-group");
    when(userResource.groups(0, 100)).thenReturn(List.of(existing));

    helper.syncUserGroups(List.of("existing-group"), USER_ID, realmResource);

    verify(userResource, never()).leaveGroup(anyString());
    verify(userResource, never()).joinGroup(anyString());
    verify(groupsResource, never())
        .groups(anyString(), anyBoolean(), anyInt(), anyInt(), anyBoolean());
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
  @DisplayName("nonexistent group is logged and skipped, does not throw")
  void shouldSkipNonexistentGroup() {
    when(userResource.groups(0, 100)).thenReturn(Collections.emptyList());
    when(groupsResource.groups("ghost", true, 0, 1, true)).thenReturn(Collections.emptyList());

    helper.syncUserGroups(List.of("ghost"), USER_ID, realmResource);

    verify(userResource, never()).joinGroup(anyString());
  }

  @Test
  @DisplayName("uses exact-match lookup so 'prod' does not match 'production'")
  void shouldUseExactMatchLookup() {
    when(userResource.groups(0, 100)).thenReturn(Collections.emptyList());
    when(groupsResource.groups("prod", true, 0, 1, true)).thenReturn(Collections.emptyList());

    helper.syncUserGroups(List.of("prod"), USER_ID, realmResource);

    verify(groupsResource).groups("prod", true, 0, 1, true);
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

    GroupRepresentation racedAway = group("raced-id", "raced");
    GroupRepresentation present = group("present-id", "present");
    when(groupsResource.groups("raced", true, 0, 1, true)).thenReturn(List.of(racedAway));
    when(groupsResource.groups("present", true, 0, 1, true)).thenReturn(List.of(present));

    doThrow(new NotFoundException()).when(userResource).joinGroup("raced-id");

    helper.syncUserGroups(List.of("raced", "present"), USER_ID, realmResource);

    verify(userResource).joinGroup("raced-id");
    verify(userResource).joinGroup("present-id");
  }

  @Test
  @DisplayName("WebApplicationException on joinGroup propagates so the event is DLQ'd")
  void shouldPropagate5xxOnJoinGroup() {
    when(userResource.groups(0, 100)).thenReturn(Collections.emptyList());
    GroupRepresentation g = group("g-id", "g");
    when(groupsResource.groups("g", true, 0, 1, true)).thenReturn(List.of(g));
    doThrow(new WebApplicationException(Response.serverError().build()))
        .when(userResource)
        .joinGroup("g-id");

    assertThrows(
        WebApplicationException.class,
        () -> helper.syncUserGroups(List.of("g"), USER_ID, realmResource));
  }

  @Test
  @DisplayName("paginates current memberships so users with >100 groups don't lose the overflow")
  void shouldPaginateCurrentMemberships() {
    List<GroupRepresentation> firstPage = new java.util.ArrayList<>();
    for (int i = 0; i < 100; i++) {
      firstPage.add(group("id-" + i, "group-" + i));
    }
    GroupRepresentation overflow = group("id-100", "group-100");
    when(userResource.groups(0, 100)).thenReturn(firstPage);
    when(userResource.groups(100, 100)).thenReturn(List.of(overflow));
    when(userResource.groups(200, 100)).thenReturn(Collections.emptyList());

    // desired keeps page 1 entirely; overflow group "group-100" is not desired and must be removed
    List<String> desired = new java.util.ArrayList<>();
    for (int i = 0; i < 100; i++) {
      desired.add("group-" + i);
    }

    helper.syncUserGroups(desired, USER_ID, realmResource);

    verify(userResource).leaveGroup("id-100");
    verify(userResource, never()).leaveGroup("id-0");
  }
}
