/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.keycloak;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civitas.configadapter.Topics;
import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.configuration.AppConfig;
import com.civitas.configadapter.configuration.ApplicationConfig;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.Config;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.Metadata;
import com.civitas.configadapter.model.Operation;
import com.civitas.configadapter.model.Payload;
import com.civitas.configadapter.model.idm.ClientConfig;
import com.civitas.configadapter.model.idm.IdmConfigValue;
import com.civitas.configadapter.model.idm.RealmConfig;
import com.civitas.configadapter.model.idm.RoleConfig;
import com.civitas.configadapter.model.idm.UserConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.representations.idm.ClientRepresentation;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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
  private ObjectMapper objectMapper;

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

    // Create ObjectMapper for test conversions
    objectMapper = new ObjectMapper();
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
  @Order(1)
  void shouldCreateRealm() {
    // Given - Create RealmConfig directly (as a developer would)
    RealmConfig realmConfig = new RealmConfig();
    realmConfig.setRealm("test-realm");
    realmConfig.setEnabled(true);
    realmConfig.setDisplayName("Test Realm");

    ConfigEvent event =
        createConfigEvent("realms/test-realm", "realm", Operation.CREATE, realmConfig);

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
  @Order(2)
  void shouldUpdateRealm() {
    // Given - create realm first
    RealmRepresentation realmRep = new RealmRepresentation();
    realmRep.setRealm("update-realm");
    realmRep.setEnabled(true);
    keycloakClient.realms().create(realmRep);

    // Create update configuration using our ConfigValue
    RealmConfig updateConfig = new RealmConfig();
    updateConfig.setRealm("update-realm");
    updateConfig.setDisplayName("Updated Realm");
    updateConfig.setEnabled(false);

    ConfigEvent event =
        createConfigEvent("realms/update-realm", "realm", Operation.UPDATE, updateConfig);

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

  @Test
  @Order(3)
  void shouldCreateUser() {
    // Given - create test realm first
    RealmRepresentation realmRep = new RealmRepresentation();
    realmRep.setRealm("user-realm");
    realmRep.setEnabled(true);
    keycloakClient.realms().create(realmRep);

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

    ConfigEvent event =
        createConfigEvent("realms/user-realm/users/testuser", "user", Operation.CREATE, userConfig);

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
  @Order(4)
  void shouldCreateClient() {
    // Given - create test realm first
    RealmRepresentation realmRep = new RealmRepresentation();
    realmRep.setRealm("client-realm");
    realmRep.setEnabled(true);
    keycloakClient.realms().create(realmRep);

    // Create ClientConfig directly
    ClientConfig clientConfig = new ClientConfig();
    clientConfig.setClientId("test-client");
    clientConfig.setEnabled(true);
    clientConfig.setPublicClient(false);
    clientConfig.setDirectAccessGrantsEnabled(true);

    ConfigEvent event =
        createConfigEvent(
            "realms/client-realm/clients/test-client", "client", Operation.CREATE, clientConfig);

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
  @Order(5)
  void shouldDeleteRealm() {
    // Given - create realm first
    RealmRepresentation realmRep = new RealmRepresentation();
    realmRep.setRealm("delete-realm");
    realmRep.setEnabled(true);
    keycloakClient.realms().create(realmRep);

    ConfigEvent event = createConfigEvent("realms/delete-realm", "realm", Operation.DELETE, null);

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
  @Order(6)
  void shouldCreateRole() {
    // Given - create test realm first
    RealmRepresentation realmRep = new RealmRepresentation();
    realmRep.setRealm("role-create-realm");
    realmRep.setEnabled(true);
    keycloakClient.realms().create(realmRep);

    // Create RoleConfig directly
    RoleConfig roleConfig = new RoleConfig();
    roleConfig.setName("testrole");

    ConfigEvent event =
        createConfigEvent(
            "realms/role-create-realm/roles/testrole", "role", Operation.CREATE, roleConfig);

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
  @Order(7)
  void shouldCreateRoleWithNestedRole() {
    // Given - create test realm first
    RealmRepresentation realmRep = new RealmRepresentation();
    realmRep.setRealm("role-nested-create-realm");
    realmRep.setEnabled(true);
    keycloakClient.realms().create(realmRep);

    RoleRepresentation nestedRoleRep = new RoleRepresentation();
    nestedRoleRep.setName("nestedtestrole");

    keycloakClient.realm("role-nested-create-realm").roles().create(nestedRoleRep);

    // Create RoleConfig with composite roles using our simple API
    RoleConfig roleConfig = new RoleConfig();
    roleConfig.setName("testrole");
    roleConfig.setComposite(true);
    roleConfig.setCompositeRoles(Set.of("nestedtestrole")); // Simple Set<String>!

    ConfigEvent event =
        createConfigEvent(
            "realms/role-nested-create-realm/roles/testrole", "role", Operation.CREATE, roleConfig);

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
  @Order(8)
  void shouldUpdateRole() {
    // Given - create test realm first
    RealmRepresentation realmRep = new RealmRepresentation();
    realmRep.setRealm("role-update-realm");
    realmRep.setEnabled(true);
    keycloakClient.realms().create(realmRep);

    RoleRepresentation initialRoleRep = new RoleRepresentation();
    initialRoleRep.setName("testrole");

    keycloakClient.realm("role-update-realm").roles().create(initialRoleRep);

    // Create RoleConfig for update
    RoleConfig roleConfig = new RoleConfig();
    roleConfig.setName("testrole");
    roleConfig.setDescription("new description");
    ConfigEvent event =
        createConfigEvent(
            "realms/role-update-realm/roles/testrole", "role", Operation.UPDATE, roleConfig);

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
  @Order(9)
  void shouldDeleteRole() {
    // Given - create test realm first
    RealmRepresentation realmRep = new RealmRepresentation();
    realmRep.setRealm("role-delete-realm");
    realmRep.setEnabled(true);
    keycloakClient.realms().create(realmRep);

    RoleRepresentation initialRoleRep = new RoleRepresentation();
    initialRoleRep.setName("testrole");

    keycloakClient.realm("role-delete-realm").roles().create(initialRoleRep);

    // For delete, we just need the role name
    RoleConfig roleConfig = new RoleConfig();
    roleConfig.setName("testrole");
    ConfigEvent event =
        createConfigEvent(
            "realms/role-delete-realm/roles/testrole", "role", Operation.DELETE, roleConfig);

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

  @Test
  @Order(10)
  void shouldPublishErrorResultOnFailure() {
    // Given - try to update non-existent realm
    RealmConfig realmConfig = new RealmConfig();
    realmConfig.setRealm("non-existent-realm");

    ConfigEvent event =
        createConfigEvent("realms/non-existent-realm", "realm", Operation.UPDATE, realmConfig);

    // When
    adapter.processConfigEvent(Topics.REALM_UPDATED.toString(), event);

    // Then - verify error result was published
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.FAILURE, resultEvent.status());
    assertNotNull(resultEvent.errorCode());
    assertNotNull(resultEvent.message());
  }

  @Test
  void shouldHandleCorrelationIdInResults() {
    // Given
    String correlationId = UUID.randomUUID().toString();
    RealmConfig realmConfig = new RealmConfig();
    realmConfig.setRealm("correlation-realm");
    realmConfig.setEnabled(true);

    ConfigEvent event =
        createConfigEventWithCorrelation(
            "realms/correlation-realm", "realm", Operation.CREATE, realmConfig, correlationId);

    // When
    adapter.processConfigEvent(Topics.REALM_CREATED.toString(), event);

    // Then - verify correlation ID is preserved in result
    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(correlationId, resultEvent.correlationId());
    assertEquals(event.metadata().messageId(), resultEvent.originalMessageId());
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
     * @see com.civitas.configadapter.messaging.EventPublisher#getName()
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
     * @see com.civitas.configadapter.messaging.EventBase#initialize(com.civitas.configadapter.configuration.ApplicationConfig, com.civitas.configadapter.adapter.ConfigAdapter)
     */
    @Override
    public void initialize(ApplicationConfig config, ConfigAdapter adapter) {}
  }
}
