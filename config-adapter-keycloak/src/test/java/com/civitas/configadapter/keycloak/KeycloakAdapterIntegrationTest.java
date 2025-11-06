package com.civitas.configadapter.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.civitas.configadapter.config.AppConfig;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.Config;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.Metadata;
import com.civitas.configadapter.model.Payload;
import com.civitas.configadapter.model.Topics;

import io.cloudevents.CloudEvent;

/**
 * Integration test for KeycloakAdapter using Testcontainers.
 * Tests actual Keycloak operations: realm, client, and user management.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KeycloakAdapterIntegrationTest {

    @Container
    static GenericContainer<?> keycloak = new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:23.0"))
            .withExposedPorts(8080)
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withCommand("start-dev")
            .withReuse(false);

    private KeycloakAdapter adapter;
    private TestEventPublisher eventPublisher;
    private AppConfig config;
    private Keycloak keycloakClient;

    @BeforeEach
    void setUp() throws InterruptedException {
        String keycloakUrl = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);

        // Wait for Keycloak to be ready
        waitForKeycloakReady(keycloakUrl);

        // Create configuration
        Properties props = new Properties();
        props.setProperty("keycloak.url", keycloakUrl);
        props.setProperty("keycloak.realm", "master");
        props.setProperty("keycloak.username", "admin");
        props.setProperty("keycloak.password", "admin");
        props.setProperty("keycloak.client.id", "admin-cli");
        config = new AppConfig(props);

        // Create adapter
        adapter = new KeycloakAdapter(config);

        // Create test event publisher
        eventPublisher = new TestEventPublisher();
        adapter.setEventPublisher(eventPublisher);

        // Create Keycloak client for verification
        keycloakClient = Keycloak.getInstance(
                keycloakUrl,
                "master",
                "admin",
                "admin",
                "admin-cli"
        );
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
        // Given
        RealmRepresentation realmRep = new RealmRepresentation();
        realmRep.setRealm("test-realm");
        realmRep.setEnabled(true);
        realmRep.setDisplayName("Test Realm");

        ConfigEvent event = createConfigEvent(
                "realms/test-realm",
                "realm",
                "CREATE",
                realmRep
        );

        // When
        adapter.processConfigEvent(Topics.REALM_CREATED, event);

        // Then
        RealmRepresentation createdRealm = keycloakClient.realm("test-realm").toRepresentation();
        assertNotNull(createdRealm);
        assertEquals("test-realm", createdRealm.getRealm());
        assertEquals("Test Realm", createdRealm.getDisplayName());
        assertTrue(createdRealm.isEnabled());

        // Verify success result was published
        assertEquals(1, eventPublisher.getPublishedEvents().size());
        CloudEvent resultEvent = eventPublisher.getPublishedEvents().get(0);
        assertEquals("SUCCESS", resultEvent.getExtension("status"));
        assertEquals("test-realm", resultEvent.getExtension("resourceid"));
    }

    @Test
    @Order(2)
    void shouldUpdateRealm() {
        // Given - create realm first
        RealmRepresentation realmRep = new RealmRepresentation();
        realmRep.setRealm("update-realm");
        realmRep.setEnabled(true);
        keycloakClient.realms().create(realmRep);

        // Update configuration
        realmRep.setDisplayName("Updated Realm");
        realmRep.setEnabled(false);

        ConfigEvent event = createConfigEvent(
                "realms/update-realm",
                "realm",
                "UPDATE",
                realmRep
        );

        // When
        adapter.processConfigEvent(Topics.REALM_UPDATED, event);

        // Then
        RealmRepresentation updatedRealm = keycloakClient.realm("update-realm").toRepresentation();
        assertEquals("Updated Realm", updatedRealm.getDisplayName());
        assertFalse(updatedRealm.isEnabled());

        // Verify success result
        assertEquals(1, eventPublisher.getPublishedEvents().size());
        assertEquals("SUCCESS", eventPublisher.getPublishedEvents().get(0).getExtension("status"));
    }

    @Test
    @Order(3)
    void shouldCreateUser() {
        // Given - create test realm first
        RealmRepresentation realmRep = new RealmRepresentation();
        realmRep.setRealm("user-realm");
        realmRep.setEnabled(true);
        keycloakClient.realms().create(realmRep);

        UserRepresentation userRep = new UserRepresentation();
        userRep.setUsername("testuser");
        userRep.setEmail("testuser@example.com");
        userRep.setFirstName("Test");
        userRep.setLastName("User");
        userRep.setEnabled(true);

        ConfigEvent event = createConfigEvent(
                "realms/user-realm/users/testuser",
                "user",
                "CREATE",
                userRep
        );

        // When
        adapter.processConfigEvent(Topics.USER_CREATED, event);

        // Then
        List<UserRepresentation> users = keycloakClient.realm("user-realm")
                .users()
                .search("testuser");
        assertEquals(1, users.size());
        UserRepresentation createdUser = users.get(0);
        assertEquals("testuser", createdUser.getUsername());
        assertEquals("testuser@example.com", createdUser.getEmail());
        assertTrue(createdUser.isEnabled());

        // Verify success result
        assertEquals(1, eventPublisher.getPublishedEvents().size());
        assertEquals("SUCCESS", eventPublisher.getPublishedEvents().get(0).getExtension("status"));
    }

    @Test
    @Order(4)
    void shouldCreateClient() {
        // Given - create test realm first
        RealmRepresentation realmRep = new RealmRepresentation();
        realmRep.setRealm("client-realm");
        realmRep.setEnabled(true);
        keycloakClient.realms().create(realmRep);

        ClientRepresentation clientRep = new ClientRepresentation();
        clientRep.setClientId("test-client");
        clientRep.setEnabled(true);
        clientRep.setPublicClient(false);
        clientRep.setDirectAccessGrantsEnabled(true);

        ConfigEvent event = createConfigEvent(
                "realms/client-realm/clients/test-client",
                "client",
                "CREATE",
                clientRep
        );

        // When
        adapter.processConfigEvent(Topics.CLIENT_CREATED, event);

        // Then
        List<ClientRepresentation> clients = keycloakClient.realm("client-realm")
                .clients()
                .findByClientId("test-client");
        assertEquals(1, clients.size());
        ClientRepresentation createdClient = clients.get(0);
        assertEquals("test-client", createdClient.getClientId());
        assertTrue(createdClient.isEnabled());

        // Verify success result
        assertEquals(1, eventPublisher.getPublishedEvents().size());
        assertEquals("SUCCESS", eventPublisher.getPublishedEvents().get(0).getExtension("status"));
    }

    @Test
    @Order(5)
    void shouldDeleteRealm() {
        // Given - create realm first
        RealmRepresentation realmRep = new RealmRepresentation();
        realmRep.setRealm("delete-realm");
        realmRep.setEnabled(true);
        keycloakClient.realms().create(realmRep);

        ConfigEvent event = createConfigEvent(
                "realms/delete-realm",
                "realm",
                "DELETE",
                null
        );

        // When
        adapter.processConfigEvent(Topics.REALM_DELETED, event);

        // Then - verify realm is deleted
        assertThrows(jakarta.ws.rs.NotFoundException.class, () -> {
            keycloakClient.realm("delete-realm").toRepresentation();
        });

        // Verify success result
        assertEquals(1, eventPublisher.getPublishedEvents().size());
        assertEquals("SUCCESS", eventPublisher.getPublishedEvents().get(0).getExtension("status"));
    }

    @Test
    @Order(6)
    void shouldPublishErrorResultOnFailure() {
        // Given - try to update non-existent realm
        RealmRepresentation realmRep = new RealmRepresentation();
        realmRep.setRealm("non-existent-realm");

        ConfigEvent event = createConfigEvent(
                "realms/non-existent-realm",
                "realm",
                "UPDATE",
                realmRep
        );

        // When
        adapter.processConfigEvent(Topics.REALM_UPDATED, event);

        // Then - verify error result was published
        assertEquals(1, eventPublisher.getPublishedEvents().size());
        CloudEvent resultEvent = eventPublisher.getPublishedEvents().get(0);
        assertEquals("FAILURE", resultEvent.getExtension("status"));
        assertNotNull(resultEvent.getExtension("errorcode"));
        assertNotNull(resultEvent.getExtension("errormessage"));
    }

    @Test
    void shouldHandleCorrelationIdInResults() {
        // Given
        String correlationId = UUID.randomUUID().toString();
        RealmRepresentation realmRep = new RealmRepresentation();
        realmRep.setRealm("correlation-realm");
        realmRep.setEnabled(true);

        ConfigEvent event = createConfigEventWithCorrelation(
                "realms/correlation-realm",
                "realm",
                "CREATE",
                realmRep,
                correlationId
        );

        // When
        adapter.processConfigEvent(Topics.REALM_CREATED, event);

        // Then - verify correlation ID is preserved in result
        assertEquals(1, eventPublisher.getPublishedEvents().size());
        CloudEvent resultEvent = eventPublisher.getPublishedEvents().get(0);
        assertEquals(correlationId, resultEvent.getExtension("correlationid"));
        assertEquals(event.metadata().messageId(), resultEvent.getExtension("originalmessageid"));
    }

    // Helper methods

    private void waitForKeycloakReady(String keycloakUrl) throws InterruptedException {
        int maxAttempts = 30;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                Keycloak testClient = Keycloak.getInstance(
                        keycloakUrl,
                        "master",
                        "admin",
                        "admin",
                        "admin-cli"
                );
                testClient.serverInfo().getInfo();
                testClient.close();
                return;
            } catch (Exception e) {
                Thread.sleep(1000);
            }
        }
        throw new RuntimeException("Keycloak did not start in time");
    }

    private ConfigEvent createConfigEvent(String targetResource, String targetComponent, String operation, Object value) {
        return createConfigEventWithCorrelation(targetResource, targetComponent, operation, value, UUID.randomUUID().toString());
    }

    private ConfigEvent createConfigEventWithCorrelation(String targetResource, String targetComponent, String operation, Object value, String correlationId) {
        Metadata metadata = new Metadata(
                UUID.randomUUID().toString(),
                OffsetDateTime.now().toString(),
                "test.source",
                correlationId,
                "1.0",
                "result.topic"
        );

        Config config = new Config(targetResource, value);

        Payload payload = new Payload(
                targetComponent,
                targetResource,
                operation,
                config
        );

        return new ConfigEvent(metadata, payload);
    }

    /**
     * Test event publisher that captures published events
     */
    static class TestEventPublisher implements EventPublisher {
        private final List<CloudEvent> publishedEvents = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void publish(String topic, CloudEvent event) {
            publishedEvents.add(event);
        }

        public List<CloudEvent> getPublishedEvents() {
            return new ArrayList<>(publishedEvents);
        }

        public void clear() {
            publishedEvents.clear();
        }
    }
}
