package de.civitascore.portal.service.event;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.ConfigAdapterTestHelper;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.service.GroupService;
import de.civitascore.portal.service.UserService;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@EmbeddedKafka(
    partitions = 1,
    topics = {
      "de.civitascore.idm.user.created",
      "de.civitascore.idm.user.updated",
      "de.civitascore.idm.user.deleted",
      "de.civitascore.idm.group.created",
      "de.civitascore.idm.group.updated",
      "de.civitascore.idm.group.deleted",
      "de.civitascore.config.results"
    })
@TestPropertySource(properties = {"kafka.enabled=true"})
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class BaseEventPublishingIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired protected UserService userService;
  @Autowired protected GroupService groupService;
  @Autowired protected UserRepository userRepository;
  @Autowired protected KafkaTemplate<String, String> kafkaTemplate;
  @Autowired protected PortalTestDataFactory portalData;

  @Value("${spring.embedded.kafka.brokers}")
  private String embeddedKafkaBrokers;

  private ConfigAdapterTestHelper configAdapterHelper;

  @BeforeEach
  void setUp() {
    log.debug("=== Test Setup Starting ===");

    ensureCivitasCoreRealmExists();
    configAdapterHelper =
        new ConfigAdapterTestHelper(KEYCLOAK, embeddedKafkaBrokers, kafkaTemplate);

    log.debug("=== Test Setup Complete ===");
  }

  @AfterEach
  void tearDown() {
    log.debug("=== Test Teardown Starting ===");

    if (configAdapterHelper != null) {
      try {
        configAdapterHelper.close();
      } catch (Exception e) {
        log.warn("Error closing config adapter helper: {}", e.getMessage());
      }
    }
    portalData.cleanAll();
    cleanupKeycloakUsers();
    cleanupKeycloakGroups();
    log.debug("=== Test Teardown Complete ===");
  }

  private void ensureCivitasCoreRealmExists() {
    try {
      try {
        keycloakAdminClient.realm("civitas-core").toRepresentation();
        log.debug("civitas-core realm already exists");
      } catch (Exception e) {
        log.info("Creating civitas-core realm in Keycloak");
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm("civitas-core");
        realm.setEnabled(true);
        realm.setDisplayName("Civitas Core Test Realm");
        realm.setEditUsernameAllowed(true);
        keycloakAdminClient.realms().create(realm);
        log.info("civitas-core realm created successfully");
      }
    } catch (Exception e) {
      log.error("Failed to ensure civitas-core realm exists: {}", e.getMessage(), e);
    }
  }

  private void cleanupKeycloakUsers() {
    try {
      List<UserRepresentation> users = keycloakAdminClient.realm("civitas-core").users().list();
      for (UserRepresentation user : users) {
        if (user.getEmail() != null && user.getEmail().contains("@example.com")) {
          try {
            keycloakAdminClient.realm("civitas-core").users().delete(user.getId());
            log.debug("Cleaned up Keycloak user: {}", user.getEmail());
          } catch (Exception e) {
            log.warn("Failed to delete Keycloak user {}: {}", user.getEmail(), e.getMessage());
          }
        }
      }
    } catch (Exception e) {
      log.warn("Failed to list Keycloak users for cleanup: {}", e.getMessage());
    }
  }

  protected List<org.keycloak.representations.idm.GroupRepresentation> findKeycloakGroups() {
    try {
      return keycloakAdminClient.realm("civitas-core").groups().groups();
    } catch (Exception e) {
      log.error("Failed to list Keycloak groups: {}", e.getMessage());
      return List.of();
    }
  }

  protected List<org.keycloak.representations.idm.GroupRepresentation> findKeycloakUserGroups(
      String userId) {
    try {
      return keycloakAdminClient.realm("civitas-core").users().get(userId).groups();
    } catch (Exception e) {
      log.error("Failed to list Keycloak user groups: {}", e.getMessage());
      return List.of();
    }
  }

  protected List<UserRepresentation> findKeycloakGroupMembers(String groupExternalId) {
    try {
      return keycloakAdminClient.realm("civitas-core").groups().group(groupExternalId).members();
    } catch (Exception e) {
      log.error("Failed to list Keycloak group members: {}", e.getMessage());
      return List.of();
    }
  }

  // Unlike cleanupKeycloakUsers, this deletes every group unconditionally: the civitas-core realm
  // is created empty per test run (ensureCivitasCoreRealmExists) and only ever holds groups synced
  // by the tests, so there is no non-test group to preserve. Test group names are heterogeneous
  // ("Init Test Admins", "syncgrp<ms>", "Test Group <ms>", ...) with no common prefix, so scoping
  // by name would silently leak groups whenever a new test introduces a new naming pattern.
  private void cleanupKeycloakGroups() {
    try {
      List<org.keycloak.representations.idm.GroupRepresentation> groups =
          keycloakAdminClient.realm("civitas-core").groups().groups();
      for (org.keycloak.representations.idm.GroupRepresentation group : groups) {
        try {
          keycloakAdminClient.realm("civitas-core").groups().group(group.getId()).remove();
          log.debug("Cleaned up Keycloak group: {}", group.getName());
        } catch (Exception e) {
          log.warn("Failed to delete Keycloak group {}: {}", group.getName(), e.getMessage());
        }
      }
    } catch (Exception e) {
      log.warn("Failed to list Keycloak groups for cleanup: {}", e.getMessage());
    }
  }

  protected UserRepresentation findKeycloakUserByEmail(String email) {
    try {
      List<UserRepresentation> users =
          keycloakAdminClient.realm("civitas-core").users().searchByEmail(email, true);
      return users.isEmpty() ? null : users.get(0);
    } catch (Exception e) {
      log.error("Failed to search for Keycloak user by email {}: {}", email, e.getMessage());
      return null;
    }
  }

  protected UserInputDTO createValidUserInput() {
    UserInputDTO input = new UserInputDTO();
    input.setFirstName("Integration");
    input.setLastName("Test" + System.currentTimeMillis());
    input.setEmail("test." + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
    input.setPhone("+49123456789");
    input.setActive(true);
    return input;
  }
}
