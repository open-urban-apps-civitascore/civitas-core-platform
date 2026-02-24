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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.adapter.ConfigAdapter;
import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.configuration.ApplicationConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.messaging.EventPublisher;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.Config;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.Payload;
import de.civitascore.configadapter.model.idm.ClientConfig;
import de.civitascore.configadapter.model.idm.GroupConfig;
import de.civitascore.configadapter.model.idm.IdmConfigValue;
import de.civitascore.configadapter.model.idm.RealmConfig;
import de.civitascore.configadapter.model.idm.RoleConfig;
import de.civitascore.configadapter.model.idm.UserConfig;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.configuration2.MapConfiguration;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test for KeycloakAdapter using Testcontainers. Tests actual Keycloak operations:
 * realm, client, and user management.
 */
@Testcontainers
class KeycloakAdapterIntegrationTest {

  @SuppressWarnings("resource") // suppress false positive warning
  @Container
  static GenericContainer<?> keycloak =
      new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:23.0"))
          .withExposedPorts(8080)
          .withEnv("KEYCLOAK_ADMIN", "admin")
          .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
          .withCommand("start-dev")
          .withReuse(false);

  private KeycloakAdapter adapter;
  private TestEventPublisher eventPublisher;
  private Keycloak keycloakClient;

  @BeforeEach
  void setUp() throws InterruptedException {
    String keycloakUrl = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);

    // Wait for Keycloak to be ready
    waitForKeycloakReady(keycloakUrl);

    // Create configuration
    Map<String, Object> props = new HashMap<>();
    props.put("keycloak.url", keycloakUrl);
    props.put("keycloak.realm", "master");
    props.put("keycloak.username", "admin");
    props.put("keycloak.password", "admin");
    props.put("keycloak.client.id", "admin-cli");
    props.put(
        "keycloak.topics",
        String.join(
            ",",
            Topics.USER_CREATED.toString(),
            Topics.USER_UPDATED.toString(),
            Topics.USER_DELETED.toString(),
            Topics.USER_LOCKED.toString(),
            Topics.USER_UNLOCKED.toString(),
            Topics.USER_PASSWORD_CHANGED.toString(),
            Topics.USER_PASSWORD_RESET.toString(),
            Topics.REALM_CREATED.toString(),
            Topics.REALM_UPDATED.toString(),
            Topics.REALM_DELETED.toString(),
            Topics.CLIENT_CREATED.toString(),
            Topics.CLIENT_UPDATED.toString(),
            Topics.CLIENT_DELETED.toString()));
    AppConfig config = new AppConfig(new MapConfiguration(props));

    // Create adapter
    adapter = new KeycloakAdapter();
    adapter.initialize(config);

    // Create test event publisher
    eventPublisher = new TestEventPublisher();
    adapter.setEventPublisher(eventPublisher);

    // Create Keycloak client for verification
    keycloakClient = Keycloak.getInstance(keycloakUrl, "master", "admin", "admin", "admin-cli");
  }

  @AfterEach
  void tearDown() {
    if (adapter != null) {
      adapter.close();
    }
    if (keycloakClient != null) {
      keycloakClient.close();
    }
  }

  @Test
  void shouldCreateRealm() throws FatalAdapterException, RetryableAdapterException {
    // Given - Create RealmConfig directly (as a developer would)
    RealmConfig realmConfig = new RealmConfig();
    realmConfig.setRealm("test-realm");
    realmConfig.setEnabled(true);
    realmConfig.setDisplayName("Test Realm");

    ConfigEvent event = createConfigEvent("test-realm", "realm", Operation.CREATE, realmConfig);

    // When
    adapter.processConfigEvent(Topics.REALM_CREATED.toString(), event);

    // Then
    RealmRepresentation createdRealm = keycloakClient.realm("test-realm").toRepresentation();
    assertNotNull(createdRealm);
    assertEquals("test-realm", createdRealm.getRealm());
    assertEquals("Test Realm", createdRealm.getDisplayName());
    assertTrue(createdRealm.isEnabled());

    // Verify success result was published
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
    assertEquals("test-realm", resultEvent.resourceId());
  }

  @Test
  void shouldUpdateRealm() throws FatalAdapterException, RetryableAdapterException {
    // Given - create realm first
    createRealm("update-realm");

    // Create update configuration using our ConfigValue
    RealmConfig updateConfig = new RealmConfig();
    updateConfig.setRealm("update-realm");
    updateConfig.setDisplayName("Updated Realm");
    updateConfig.setEnabled(false);

    ConfigEvent event = createConfigEvent("update-realm", "realm", Operation.UPDATE, updateConfig);

    // When
    adapter.processConfigEvent(Topics.REALM_UPDATED.toString(), event);

    // Then
    RealmRepresentation updatedRealm = keycloakClient.realm("update-realm").toRepresentation();
    assertEquals("Updated Realm", updatedRealm.getDisplayName());
    assertFalse(updatedRealm.isEnabled());

    // Verify success result
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    assertEquals(
        ConfigResultEvent.Status.SUCCESS, eventPublisher.getPublishedEvents().getFirst().status());
  }

  private void createRealm(String realm) {
    RealmRepresentation realmRep = new RealmRepresentation();
    realmRep.setRealm(realm);
    realmRep.setEnabled(true);
    keycloakClient.realms().create(realmRep);
  }

  @Test
  void shouldCreateUser() throws FatalAdapterException, RetryableAdapterException {
    // Given - create test realm first
    createRealm("user-realm");

    RoleRepresentation initialRoleRep = new RoleRepresentation();
    initialRoleRep.setName("testrole");

    keycloakClient.realm("user-realm").roles().create(initialRoleRep);

    // Create UserConfig directly
    UserConfig userConfig = new UserConfig();
    userConfig.setUsername("testuser");
    userConfig.setEmail("testuser@example.com");
    userConfig.setFirstName("Test");
    userConfig.setLastName("User");
    userConfig.setEnabled(true);
    userConfig.setRealmRoles(List.of("testrole"));

    ConfigEvent event = createConfigEvent("user-realm", "user", Operation.CREATE, userConfig);

    // When
    adapter.processConfigEvent(Topics.USER_CREATED.toString(), event);

    // Then
    List<UserRepresentation> users = keycloakClient.realm("user-realm").users().search("testuser");
    assertEquals(1, users.size());
    UserRepresentation createdUser = users.getFirst();
    assertEquals("testuser", createdUser.getUsername());
    assertEquals("testuser@example.com", createdUser.getEmail());
    assertTrue(createdUser.isEnabled());
    var userId = createdUser.getId();
    var userRoles =
        keycloakClient.realm("user-realm").users().get(userId).roles().realmLevel().listAll();
    assertTrue(
        userRoles.stream().anyMatch(role -> role.getName().equals("testrole")),
        "No added roles found");

    // Verify success result
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    assertEquals(
        ConfigResultEvent.Status.SUCCESS, eventPublisher.getPublishedEvents().getFirst().status());
  }

  @Test
  void shouldCreateClient() throws FatalAdapterException, RetryableAdapterException {
    // Given - create test realm first
    createRealm("client-realm");

    // Create ClientConfig directly
    ClientConfig clientConfig = new ClientConfig();
    clientConfig.setClientId("test-client");
    clientConfig.setEnabled(true);
    clientConfig.setPublicClient(false);
    clientConfig.setDirectAccessGrantsEnabled(true);

    ConfigEvent event = createConfigEvent("client-realm", "client", Operation.CREATE, clientConfig);

    // When
    adapter.processConfigEvent(Topics.CLIENT_CREATED.toString(), event);

    // Then
    List<ClientRepresentation> clients =
        keycloakClient.realm("client-realm").clients().findByClientId("test-client");
    assertEquals(1, clients.size());
    ClientRepresentation createdClient = clients.getFirst();
    assertEquals("test-client", createdClient.getClientId());
    assertTrue(createdClient.isEnabled());

    // Verify success result
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    assertEquals(
        ConfigResultEvent.Status.SUCCESS, eventPublisher.getPublishedEvents().getFirst().status());
  }

  @Test
  void shouldDeleteRealm() throws FatalAdapterException, RetryableAdapterException {
    // Given - create realm first
    createRealm("delete-realm");

    RealmConfig realmConfig = new RealmConfig();
    realmConfig.setRealm("delete-realm");
    ConfigEvent event = createConfigEvent("delete-realm", "realm", Operation.DELETE, realmConfig);

    // When
    adapter.processConfigEvent(Topics.REALM_DELETED.toString(), event);

    // Then - verify realm is deleted
    assertThrows(
        jakarta.ws.rs.NotFoundException.class,
        () -> keycloakClient.realm("delete-realm").toRepresentation());

    // Verify success result
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    assertEquals(
        ConfigResultEvent.Status.SUCCESS, eventPublisher.getPublishedEvents().getFirst().status());
  }

  @Test
  void shouldCreateRole() throws FatalAdapterException, RetryableAdapterException {
    // Given - create test realm first
    createRealm("role-create-realm");

    // Create RoleConfig directly
    RoleConfig roleConfig = new RoleConfig();
    roleConfig.setName("testrole");

    ConfigEvent event =
        createConfigEvent("role-create-realm", "role", Operation.CREATE, roleConfig);

    // When
    adapter.processConfigEvent(Topics.ROLE_CREATED.toString(), event);

    // Then
    List<RoleRepresentation> roles = keycloakClient.realm("role-create-realm").roles().list();

    assertTrue(
        roles.stream().anyMatch(role -> role.getName().equals("testrole")), "No added roles found");

    // Verify success result
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    assertEquals(
        ConfigResultEvent.Status.SUCCESS, eventPublisher.getPublishedEvents().getFirst().status());
  }

  @Test
  void shouldCreateRoleWithNestedRole() throws FatalAdapterException, RetryableAdapterException {
    // Given - create test realm first
    createRealm("role-nested-create-realm");

    RoleRepresentation nestedRoleRep = new RoleRepresentation();
    nestedRoleRep.setName("nestedtestrole");

    keycloakClient.realm("role-nested-create-realm").roles().create(nestedRoleRep);

    // Create RoleConfig with composite roles using our simple API
    RoleConfig roleConfig = new RoleConfig();
    roleConfig.setName("testrole");
    roleConfig.setComposite(true);
    roleConfig.setCompositeRoles(Set.of("nestedtestrole")); // Simple Set<String>!

    ConfigEvent event =
        createConfigEvent("role-nested-create-realm", "role", Operation.CREATE, roleConfig);

    // When
    adapter.processConfigEvent(Topics.ROLE_CREATED.toString(), event);

    // Then
    List<RoleRepresentation> roles =
        keycloakClient.realm("role-nested-create-realm").roles().list();

    RoleRepresentation addedRole =
        roles.stream()
            .filter(role -> role.getName().equals("testrole"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No added roles found"));
    assertTrue(addedRole.isComposite(), "role 'testrole' is not marked as composite.");
    RoleResource roleResource =
        keycloakClient.realm("role-nested-create-realm").roles().get("testrole");
    Set<RoleRepresentation> compositeRoles = roleResource.getRealmRoleComposites();
    assertEquals(1, compositeRoles.size());
    boolean roleFound =
        compositeRoles.stream().anyMatch(role -> role.getName().equals("nestedtestrole"));
    assertTrue(roleFound, "No nested roles found");

    // Verify success result
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    assertEquals(
        ConfigResultEvent.Status.SUCCESS, eventPublisher.getPublishedEvents().getFirst().status());
  }

  @Test
  void shouldUpdateRole() throws FatalAdapterException, RetryableAdapterException {
    // Given - create test realm first
    createRealm("role-update-realm");

    RoleRepresentation initialRoleRep = new RoleRepresentation();
    initialRoleRep.setName("testrole");
    initialRoleRep.setId("1234");

    keycloakClient.realm("role-update-realm").roles().create(initialRoleRep);

    // Create RoleConfig for update
    RoleConfig roleConfig = new RoleConfig();
    roleConfig.setName("testrole");
    roleConfig.setDescription("new description");
    ConfigEvent event =
        createConfigEvent("role-update-realm", "role", Operation.UPDATE, roleConfig);

    // When
    adapter.processConfigEvent(Topics.ROLE_UPDATED.toString(), event);

    // Then
    List<RoleRepresentation> roles = keycloakClient.realm("role-update-realm").roles().list();

    RoleRepresentation addedRole =
        roles.stream()
            .filter(role -> role.getName().equals("testrole"))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("The expected role 'testrole' could not be found."));

    assertEquals(roleConfig.getDescription(), addedRole.getDescription(), "Role not updated");

    // Verify success result
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    assertEquals(
        ConfigResultEvent.Status.SUCCESS, eventPublisher.getPublishedEvents().getFirst().status());
  }

  @Test
  void shouldDeleteRole() throws FatalAdapterException, RetryableAdapterException {
    // Given - create test realm first
    createRealm("role-delete-realm");

    RoleRepresentation initialRoleRep = new RoleRepresentation();
    initialRoleRep.setName("testrole");

    keycloakClient.realm("role-delete-realm").roles().create(initialRoleRep);

    // For delete, we just need the role name
    RoleConfig roleConfig = new RoleConfig();
    roleConfig.setName("testrole");
    ConfigEvent event =
        createConfigEvent("role-delete-realm", "role", Operation.DELETE, roleConfig);

    // When
    adapter.processConfigEvent(Topics.ROLE_DELETED.toString(), event);

    // Then
    List<RoleRepresentation> roles = keycloakClient.realm("role-delete-realm").roles().list();

    assertFalse(
        roles.stream().anyMatch(role -> role.getName().equals("testrole")),
        "Role should be deleted");

    // Verify success result
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    assertEquals(
        ConfigResultEvent.Status.SUCCESS, eventPublisher.getPublishedEvents().getFirst().status());
  }

  // ============== GROUP TESTS ==============

  @Test
  void createGroup_whenValidConfig_shouldCreateGroupInRealm()
      throws FatalAdapterException, RetryableAdapterException {
    // Given - create test realm first
    createRealm("group-create-realm");

    // Create GroupConfig directly
    GroupConfig groupConfig = new GroupConfig();
    groupConfig.setName("testgroup");

    ConfigEvent event =
        createConfigEvent("group-create-realm", "group", Operation.CREATE, groupConfig);

    // When
    adapter.processConfigEvent(Topics.GROUP_CREATED.toString(), event);

    // Then
    List<GroupRepresentation> groups = keycloakClient.realm("group-create-realm").groups().groups();

    assertTrue(
        groups.stream().anyMatch(group -> group.getName().equals("testgroup")),
        "No added groups found");

    // Verify success result
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    assertEquals(
        ConfigResultEvent.Status.SUCCESS, eventPublisher.getPublishedEvents().getFirst().status());
  }

  @Test
  void createGroup_whenConfigHasRealmRoles_shouldAssignRolesToGroup()
      throws FatalAdapterException, RetryableAdapterException {
    // Given - create test realm and role first
    createRealm("group-roles-realm");

    RoleRepresentation roleRep = new RoleRepresentation();
    roleRep.setName("grouprole");
    RealmResource realmResource = keycloakClient.realm("group-roles-realm");
    realmResource.roles().create(roleRep);

    // Create GroupConfig with realm roles
    GroupConfig groupConfig = new GroupConfig();
    groupConfig.setName("testgroup-with-roles");
    groupConfig.setRealmRoles(Set.of("grouprole"));

    ConfigEvent event =
        createConfigEvent("group-roles-realm", "group", Operation.CREATE, groupConfig);

    // When
    adapter.processConfigEvent(Topics.GROUP_CREATED.toString(), event);

    // Then
    List<GroupRepresentation> groups = realmResource.groups().groups();

    GroupRepresentation addedGroup =
        groups.stream()
            .filter(group -> group.getName().equals("testgroup-with-roles"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No added groups found"));

    // Verify roles are assigned
    var groupRoles =
        realmResource.groups().group(addedGroup.getId()).roles().realmLevel().listAll();

    assertTrue(
        groupRoles.stream().anyMatch(role -> role.getName().equals("grouprole")),
        "Role not assigned to group");

    // Verify success result
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    assertEquals(
        ConfigResultEvent.Status.SUCCESS, eventPublisher.getPublishedEvents().getFirst().status());
  }

  @Test
  void createGroup_whenConfigHasParentId_shouldCreateAsSubgroup()
      throws FatalAdapterException, RetryableAdapterException {
    // Given - create test realm and parent group first
    createRealm("subgroup-realm");

    GroupRepresentation parentGroupRep = new GroupRepresentation();
    parentGroupRep.setName("parent-group");
    String parentGroupId;
    RealmResource realmResource = keycloakClient.realm("subgroup-realm");
    try (Response response = realmResource.groups().add(parentGroupRep)) {
      assertEquals(201, response.getStatus(), "Creation of parent group failed");
      parentGroupId = CreatedResponseUtil.getCreatedId(response);
    }

    // Create subgroup config
    GroupConfig subGroupConfig = new GroupConfig();
    subGroupConfig.setName("child-group");
    subGroupConfig.setParentId(parentGroupId);

    ConfigEvent event =
        createConfigEvent("subgroup-realm", "group", Operation.CREATE, subGroupConfig);

    // When
    adapter.processConfigEvent(Topics.GROUP_CREATED.toString(), event);

    // Then - verify subgroup was created under parent
    // Note: toRepresentation() doesn't load subGroups, need to query them explicitly
    List<GroupRepresentation> subGroups =
        realmResource.groups().group(parentGroupId).getSubGroups(0, 100, false);

    assertTrue(
        subGroups.stream().anyMatch(g -> g.getName().equals("child-group")),
        "Subgroup not created under parent");

    // Verify success result
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    assertEquals(
        ConfigResultEvent.Status.SUCCESS, eventPublisher.getPublishedEvents().getFirst().status());
  }

  @Test
  void updateGroup_whenValidConfig_shouldUpdateGroupAndAssignRoles()
      throws FatalAdapterException, RetryableAdapterException {
    // Given - create test realm and group first
    createRealm("group-update-realm");

    GroupRepresentation initialGroupRep = new GroupRepresentation();
    initialGroupRep.setName("update-group");
    RealmResource realmResource = keycloakClient.realm("group-update-realm");
    String groupId;
    try (Response response = realmResource.groups().add(initialGroupRep)) {
      assertEquals(201, response.getStatus(), "Group creation failed");
      groupId = CreatedResponseUtil.getCreatedId(response);
    }

    // Create role to assign
    RoleRepresentation roleRep = new RoleRepresentation();
    roleRep.setName("updatedrole");
    realmResource.roles().create(roleRep);

    // Create update config
    GroupConfig updateConfig = new GroupConfig();
    updateConfig.setId(groupId);
    updateConfig.setName("update-group");
    updateConfig.setRealmRoles(Set.of("updatedrole"));

    ConfigEvent event =
        createConfigEvent("group-update-realm", "group", Operation.UPDATE, updateConfig);

    // When
    adapter.processConfigEvent(Topics.GROUP_UPDATED.toString(), event);

    // Then - verify roles are assigned
    var groupRoles = realmResource.groups().group(groupId).roles().realmLevel().listAll();

    assertTrue(
        groupRoles.stream().anyMatch(role -> role.getName().equals("updatedrole")),
        "Role not assigned to group after update");

    // Verify success result
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    assertEquals(
        ConfigResultEvent.Status.SUCCESS, eventPublisher.getPublishedEvents().getFirst().status());
  }

  @Test
  void deleteGroup_whenValidConfig_shouldRemoveGroupFromRealm()
      throws FatalAdapterException, RetryableAdapterException {
    // Given - create test realm and group first
    createRealm("group-delete-realm");

    GroupRepresentation initialGroupRep = new GroupRepresentation();
    initialGroupRep.setName("delete-group");
    String groupId;
    try (var response = keycloakClient.realm("group-delete-realm").groups().add(initialGroupRep)) {
      assertEquals(201, response.getStatus(), "Group creation failed");
      groupId = CreatedResponseUtil.getCreatedId(response);
    }

    // Create delete config
    GroupConfig deleteConfig = new GroupConfig();
    deleteConfig.setId(groupId);
    deleteConfig.setName("delete-group");

    ConfigEvent event =
        createConfigEvent("group-delete-realm", "group", Operation.DELETE, deleteConfig);

    // When
    adapter.processConfigEvent(Topics.GROUP_DELETED.toString(), event);

    // Then - verify group is deleted
    List<GroupRepresentation> remainingGroups =
        keycloakClient.realm("group-delete-realm").groups().groups();

    assertFalse(
        remainingGroups.stream().anyMatch(g -> g.getName().equals("delete-group")),
        "Group should be deleted");

    // Verify success result
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    assertEquals(
        ConfigResultEvent.Status.SUCCESS, eventPublisher.getPublishedEvents().getFirst().status());
  }

  @Test
  void shouldThrowFatalExceptionOnFailure() {
    // Given - try to update non-existent realm
    RealmConfig realmConfig = new RealmConfig();
    realmConfig.setRealm("non-existent-realm");

    ConfigEvent event =
        createConfigEvent("non-existent-realm", "realm", Operation.UPDATE, realmConfig);

    // When/Then - verify FatalAdapterException is thrown with correct error code
    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent(Topics.REALM_UPDATED.toString(), event));

    assertEquals(AdapterErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    assertNotNull(exception.getSafeExternalMessage());
    assertFalse(exception.isRetryable());
  }

  @Test
  void shouldHandleCorrelationIdInResults()
      throws FatalAdapterException, RetryableAdapterException {
    // Given
    String correlationId = UUID.randomUUID().toString();
    RealmConfig realmConfig = new RealmConfig();
    realmConfig.setRealm("correlation-realm");
    realmConfig.setEnabled(true);

    ConfigEvent event =
        createConfigEventWithCorrelation(
            "correlation-realm", "realm", Operation.CREATE, realmConfig, correlationId);

    // When
    adapter.processConfigEvent(Topics.REALM_CREATED.toString(), event);

    // Then - verify correlation ID is preserved in result
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(correlationId, resultEvent.correlationId());
    assertEquals(event.metadata().messageId(), resultEvent.originalMessageId());
  }

  @Test
  void updateUser_whenRoleMissingInConfig_shouldRemoveRoleFromUser()
      throws FatalAdapterException, RetryableAdapterException {
    // Given - Realm, 2 roles and User with both roles
    createRealm("user-sync-realm");

    RoleRepresentation roleA = new RoleRepresentation();
    roleA.setName("role-a");
    RoleRepresentation roleB = new RoleRepresentation();
    roleB.setName("role-b");
    keycloakClient.realm("user-sync-realm").roles().create(roleA);
    keycloakClient.realm("user-sync-realm").roles().create(roleB);

    UserRepresentation userRep = new UserRepresentation();
    userRep.setUsername("sync-user");
    userRep.setEnabled(true);
    RealmResource realmResource = keycloakClient.realm("user-sync-realm");
    UsersResource users = realmResource.users();
    RolesResource roles = realmResource.roles();
    String userId;
    try (Response response = realmResource.users().create(userRep)) {
      userId = CreatedResponseUtil.getCreatedId(response);
      var roleARep = roles.get("role-a").toRepresentation();
      var roleBRep = roles.get("role-b").toRepresentation();
      users.get(userId).roles().realmLevel().add(List.of(roleARep, roleBRep));
    }

    UserConfig userUpdateConfig = new UserConfig();
    userUpdateConfig.setId(userId);
    userUpdateConfig.setUsername("sync-user");
    userUpdateConfig.setRealmRoles(List.of("role-a")); // role-b ist weg

    ConfigEvent event =
        createConfigEvent("user-sync-realm", "user", Operation.UPDATE, userUpdateConfig);

    // When
    adapter.processConfigEvent(Topics.USER_UPDATED.toString(), event);

    // Then
    String userId2 = users.search("sync-user").getFirst().getId();
    List<RoleRepresentation> currentRoles = users.get(userId2).roles().realmLevel().listAll();

    assertTrue(
        currentRoles.stream().anyMatch(r -> r.getName().equals("role-a")),
        "Role A should still be there");

    assertFalse(
        currentRoles.stream().anyMatch(r -> r.getName().equals("role-b")),
        "Role B should have been removed (Sync)!");
  }

  @Test
  void updateGroup_whenRoleMissingInConfig_shouldRemoveRoleFromGroup()
      throws FatalAdapterException, RetryableAdapterException {
    // Given - Realm, 2 roles and group with both roles
    createRealm("group-sync-realm");

    RoleRepresentation roleX = new RoleRepresentation();
    roleX.setName("role-x");
    RoleRepresentation roleY = new RoleRepresentation();
    roleY.setName("role-y");
    RealmResource realmResource = keycloakClient.realm("group-sync-realm");
    RolesResource rolesResource = realmResource.roles();
    rolesResource.create(roleX);
    rolesResource.create(roleY);

    GroupRepresentation groupRep = new GroupRepresentation();
    groupRep.setName("sync-group");
    String groupId;
    try (Response response = keycloakClient.realm("group-sync-realm").groups().add(groupRep)) {
      groupId = CreatedResponseUtil.getCreatedId(response);
      RoleRepresentation roleXRep = rolesResource.get("role-x").toRepresentation();
      RoleRepresentation roleYRep = rolesResource.get("role-y").toRepresentation();
      realmResource.groups().group(groupId).roles().realmLevel().add(List.of(roleXRep, roleYRep));
    }

    // Config für Update: only "role-x" ("role-y" missing)
    GroupConfig groupUpdateConfig = new GroupConfig();
    groupUpdateConfig.setId(groupId);
    groupUpdateConfig.setName("sync-group");
    groupUpdateConfig.setRealmRoles(Set.of("role-x")); // role-y missing

    ConfigEvent event =
        createConfigEvent("group-sync-realm", "group", Operation.UPDATE, groupUpdateConfig);

    // When
    adapter.processConfigEvent(Topics.GROUP_UPDATED.toString(), event);

    // Then
    List<RoleRepresentation> currentRoles =
        realmResource.groups().group(groupId).roles().realmLevel().listAll();

    assertTrue(
        currentRoles.stream().anyMatch(r -> r.getName().equals("role-x")),
        "Role X should act exist");
    assertFalse(
        currentRoles.stream().anyMatch(r -> r.getName().equals("role-y")),
        "Role Y should be removed!");
  }

  @Test
  void updateClient_whenValidConfig_shouldUpdateClientAttributes()
      throws FatalAdapterException, RetryableAdapterException {
    // Given - create test realm and client first
    createRealm("client-update-realm");

    ClientRepresentation initialClient = new ClientRepresentation();
    initialClient.setClientId("my-app-client");
    initialClient.setEnabled(true);
    String clientId;

    try (var response =
        keycloakClient.realm("client-update-realm").clients().create(initialClient)) {
      clientId = CreatedResponseUtil.getCreatedId(response);
      assertEquals(201, response.getStatus());
    }

    ClientConfig updateClientConfig = new ClientConfig();
    updateClientConfig.setId(clientId);
    updateClientConfig.setDescription("New Description via Adapter");
    updateClientConfig.setEnabled(false);

    ConfigEvent event =
        createConfigEvent("client-update-realm", "client", Operation.UPDATE, updateClientConfig);

    // When
    adapter.processConfigEvent(Topics.CLIENT_UPDATED.toString(), event);

    // Then
    List<ClientRepresentation> clients =
        keycloakClient.realm("client-update-realm").clients().findByClientId("my-app-client");
    assertFalse(clients.isEmpty(), "Client should still exist");

    ClientRepresentation updatedClient = clients.getFirst();
    assertEquals(
        "New Description via Adapter",
        updatedClient.getDescription(),
        "Description was not updated");
    assertFalse(updatedClient.isEnabled(), "Client should be disabled");

    // Verify success result
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    assertEquals(
        ConfigResultEvent.Status.SUCCESS, eventPublisher.getPublishedEvents().getFirst().status());
  }

  @Test
  void deleteClient_whenValidConfig_shouldRemoveClientFromRealm()
      throws FatalAdapterException, RetryableAdapterException {
    // Given - create test realm and client first
    createRealm("client-delete-realm");

    ClientRepresentation initialClient = new ClientRepresentation();
    initialClient.setClientId("delete-me-client");
    initialClient.setEnabled(true);
    String clientId;

    RealmResource realmResource = keycloakClient.realm("client-delete-realm");
    try (Response response = realmResource.clients().create(initialClient)) {
      clientId = CreatedResponseUtil.getCreatedId(response);
      assertEquals(201, response.getStatus());
    }

    ClientConfig deleteClientConfig = new ClientConfig();
    deleteClientConfig.setId(clientId);

    ConfigEvent event =
        createConfigEvent("client-delete-realm", "client", Operation.DELETE, deleteClientConfig);

    // When
    adapter.processConfigEvent(Topics.CLIENT_DELETED.toString(), event);

    // Then
    List<ClientRepresentation> clients = realmResource.clients().findByClientId("delete-me-client");
    assertTrue(clients.isEmpty(), "Client should be deleted");

    // Verify success result
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    assertEquals(
        ConfigResultEvent.Status.SUCCESS, eventPublisher.getPublishedEvents().getFirst().status());
  }

  // Helper methods

  private void waitForKeycloakReady(String keycloakUrl) {
    ThrowingRunnable assertion =
        () -> {
          try (Keycloak testClient =
              Keycloak.getInstance(keycloakUrl, "master", "admin", "admin", "admin-cli")) {
            testClient.serverInfo().getInfo();
          }
        };
    await()
        .atMost(30, SECONDS)
        .pollInterval(1, SECONDS)
        .ignoreExceptions()
        .untilAsserted(assertion);
  }

  /**
   * Creates a ConfigEvent with a config value. This is how developers would use the API in
   * production - create ConfigValue objects directly and wrap them in ConfigEvent.
   */
  private ConfigEvent createConfigEvent(
      String targetResource,
      String targetComponent,
      Operation operation,
      IdmConfigValue configValue) {
    return createConfigEventWithCorrelation(
        targetResource, targetComponent, operation, configValue, UUID.randomUUID().toString());
  }

  /** Creates a ConfigEvent with a correlation ID. Used for testing correlation ID propagation. */
  private ConfigEvent createConfigEventWithCorrelation(
      String targetResource,
      String targetComponent,
      Operation operation,
      IdmConfigValue configValue,
      String correlationId) {
    Metadata metadata =
        new Metadata(
            UUID.randomUUID().toString(),
            OffsetDateTime.now(),
            "test.source",
            correlationId,
            "1.0",
            "result.topic");

    Config config = new Config(targetResource, configValue);
    Payload payload = new Payload(targetComponent, targetResource, operation, config);

    return new ConfigEvent(metadata, payload);
  }

  /** Test event publisher that captures published events */
  static class TestEventPublisher implements EventPublisher {
    private final List<ConfigResultEvent> publishedEvents =
        Collections.synchronizedList(new ArrayList<>());

    @Override
    public void publish(String topic, ConfigResultEvent event) {
      publishedEvents.add(event);
    }

    public List<ConfigResultEvent> getPublishedEvents() {
      return new ArrayList<>(publishedEvents);
    }

    /*
     * (non-Javadoc)
     * @see de.civitascore.configadapter.messaging.EventPublisher#getName()
     */
    @Override
    public String getName() {
      return "test";
    }

    public void clear() {
      publishedEvents.clear();
    }

    /*
     * (non-Javadoc)
     * @see de.civitascore.configadapter.messaging.EventBase#initialize(de.civitascore.configadapter.configuration.ApplicationConfig, de.civitascore.configadapter.adapter.ConfigAdapter)
     */
    @Override
    public void initialize(ApplicationConfig config, ConfigAdapter adapter) {}
  }
}
