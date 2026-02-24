package de.civitascore.portal.service.event;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.ConfigAdapterTestHelper;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.service.UserService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

@EmbeddedKafka(
    partitions = 1,
    brokerProperties = {"listeners=PLAINTEXT://localhost:0", "port=0"},
    topics = {
      "core.civitas.idm.user.created",
      "core.civitas.idm.user.updated",
      "core.civitas.idm.user.deleted",
      "core.civitas.config.results"
    })
@TestPropertySource(properties = {"kafka.enabled=true"})
@Slf4j
public abstract class BaseEventPublishingIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired protected UserService userService;
  @Autowired protected UserRepository userRepository;
  @Autowired protected KafkaTemplate<String, String> kafkaTemplate;

  @Value("${spring.embedded.kafka.brokers}")
  private String embeddedKafkaBrokers;

  private ConfigAdapterTestHelper configAdapterHelper;
  @Autowired private GroupRepository groupRepository;
  @Autowired private AssignmentRepository assignmentRepository;

  @BeforeEach
  void setUp() {
    log.debug("=== Test Setup Starting ===");
    assignmentRepository.deleteAll();
    groupRepository.deleteAll();
    userRepository.deleteAll();

    ensureCivitasCoreRealmExists();
    cleanupKeycloakUsers();

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

    userRepository.deleteAll();
    cleanupKeycloakUsers();
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
    input.setEmail("test." + System.currentTimeMillis() + "@example.com");
    input.setPhone("+49123456789");
    input.setActive(true);
    return input;
  }
}
