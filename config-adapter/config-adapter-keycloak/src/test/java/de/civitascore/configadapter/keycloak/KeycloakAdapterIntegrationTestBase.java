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

import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.adapter.ConfigAdapter;
import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.configuration.ApplicationConfig;
import de.civitascore.configadapter.messaging.EventPublisher;
import de.civitascore.configadapter.model.Config;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.Payload;
import de.civitascore.configadapter.model.idm.IdmConfigValue;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.configuration2.MapConfiguration;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RealmRepresentation;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for KeycloakAdapter integration tests. Uses the singleton container pattern so
 * Keycloak and Mailpit start only once per JVM, shared across all subclasses.
 */
abstract class KeycloakAdapterIntegrationTestBase {

  protected static final Network NETWORK = Network.newNetwork();

  @SuppressWarnings("resource")
  protected static final GenericContainer<?> MAILPIT;

  @SuppressWarnings("resource")
  protected static final GenericContainer<?> KEYCLOAK;

  static {
    MAILPIT =
        new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
            .withNetwork(NETWORK)
            .withNetworkAliases("mailpit")
            .withExposedPorts(1025, 8025);
    MAILPIT.start();

    KEYCLOAK =
        new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:23.0"))
            .withNetwork(NETWORK)
            .withNetworkAliases("keycloak")
            .withExposedPorts(8080)
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withCommand("start-dev")
            .withReuse(false);
    KEYCLOAK.start();

    // Singleton container pattern: containers are shared across all subclasses for performance
    // (one startup instead of five). A JVM shutdown hook ensures cleanup.
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  KEYCLOAK.stop();
                  MAILPIT.stop();
                  NETWORK.close();
                }));
  }

  protected KeycloakAdapter adapter;
  protected TestEventPublisher eventPublisher;
  protected Keycloak keycloakClient;

  @BeforeEach
  void setUp() {
    String keycloakUrl = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    waitForKeycloakReady(keycloakUrl);

    Map<String, Object> props = new HashMap<>();
    props.put("keycloak.url", keycloakUrl);
    props.put("keycloak.realm", "master");
    props.put("keycloak.username", "admin");
    props.put("keycloak.password", "admin");
    props.put("keycloak.client.id", "admin-cli");
    props.put("keycloak.invitation.client.id", "test-portal");
    props.put("keycloak.invitation.redirect.uri", "http://localhost:3000/");
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

    adapter = new KeycloakAdapter();
    adapter.initialize(config);

    eventPublisher = new TestEventPublisher();
    adapter.setEventPublisher(eventPublisher);

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

  protected void createRealm(String realm) {
    RealmRepresentation realmRep = new RealmRepresentation();
    realmRep.setRealm(realm);
    realmRep.setEnabled(true);
    keycloakClient.realms().create(realmRep);
  }

  protected ConfigEvent createConfigEvent(
      String targetResource,
      String targetComponent,
      Operation operation,
      IdmConfigValue configValue) {
    return createConfigEventWithCorrelation(
        targetResource, targetComponent, operation, configValue, UUID.randomUUID().toString());
  }

  protected ConfigEvent createConfigEventWithCorrelation(
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

  protected void assertSingleSuccessResult() {
    List<ConfigResultEvent> events = eventPublisher.getPublishedEvents();
    assertEquals(1, events.size());
    assertEquals(ConfigResultEvent.Status.SUCCESS, events.getFirst().status());
  }

  /**
   * Creates a KeycloakAdapter with custom properties, useful for testing different configurations.
   * The returned adapter must be closed by the caller.
   */
  protected KeycloakAdapter createAdapterWithProps(Map<String, Object> additionalProps) {
    String keycloakUrl = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
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
    props.putAll(additionalProps);
    AppConfig config = new AppConfig(new MapConfiguration(props));
    KeycloakAdapter customAdapter = new KeycloakAdapter();
    customAdapter.initialize(config);
    return customAdapter;
  }

  protected String getMailpitApiUrl() {
    return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + "/api/v1/messages";
  }

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

    @Override
    public String getName() {
      return "test";
    }

    public void clear() {
      publishedEvents.clear();
    }

    @Override
    public void initialize(ApplicationConfig config, ConfigAdapter adapter) {}
  }
}
