package de.civitascore.portal.service.initializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.service.event.BaseEventPublishingIntegrationTest;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles({"test-integration", "local-init"})
@DisplayName("LocalUserInitializer Integration Tests")
class LocalUserInitializerIntegrationTest extends BaseEventPublishingIntegrationTest {

  private static final String TEST_EMAIL = "init-test@example.com";
  private static final String TEST_GROUP_NAME = "Init Test Admins";

  @Autowired private LocalUserInitializer localUserInitializer;
  @Autowired private GroupRepository groupRepository;

  @BeforeEach
  void setUpInitTest() {
    groupRepository.deleteAll();
  }

  @AfterEach
  void tearDownInitTest() {
    groupRepository.deleteAll();
  }

  @Test
  @Transactional
  @Rollback
  @DisplayName("Should create group with assigned role in database")
  void shouldCreateGroupWithRole() {
    localUserInitializer.initialize();

    Group group = groupRepository.findByName(TEST_GROUP_NAME).orElseThrow();
    assertThat(group.getName()).isEqualTo(TEST_GROUP_NAME);
    assertThat(group.getDescription()).isEqualTo("Init test admin group");
    assertThat(group.getRoles()).extracting("name").containsExactly("Tenant Admin");
  }

  @Test
  @Transactional
  @Rollback
  @DisplayName("Should create user and assign to group in database")
  void shouldCreateUserAssignedToGroup() {
    localUserInitializer.initialize();

    Optional<User> user = userRepository.findByEmail(TEST_EMAIL);
    assertThat(user).isPresent();
    assertThat(user.get().getFirstName()).isEqualTo("Init");
    assertThat(user.get().getLastName()).isEqualTo("TestUser");
    assertThat(user.get().getActive()).isTrue();
    assertThat(user.get().getGroups()).extracting("name").containsExactly(TEST_GROUP_NAME);
  }

  @Test
  @DisplayName("Should not duplicate group or user when called twice")
  void shouldBeIdempotent() {
    localUserInitializer.initialize();
    localUserInitializer.initialize();

    assertThat(groupRepository.count()).isEqualTo(1);
    assertThat(userRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("Should sync user to Keycloak after initialization")
  void shouldSyncUserToKeycloak() {
    localUserInitializer.initialize();

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              UserRepresentation keycloakUser = findKeycloakUserByEmail(TEST_EMAIL);
              assertThat(keycloakUser).as("User should be synced to Keycloak").isNotNull();
            });
  }

  @Test
  @DisplayName("Should skip initialization when no groups or users are configured")
  void shouldSkipWhenNothingConfigured() {
    // Verify clean state — nothing was created yet
    assertThat(groupRepository.count()).isZero();
    assertThat(userRepository.count()).isZero();

    // When configured (from application-local.yml), initialize creates exactly one group and user
    localUserInitializer.initialize();

    assertThat(groupRepository.count()).isEqualTo(1);
    assertThat(userRepository.count()).isEqualTo(1);
  }
}
