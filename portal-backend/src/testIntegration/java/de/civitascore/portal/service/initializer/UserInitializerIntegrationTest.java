package de.civitascore.portal.service.initializer;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.service.event.BaseEventPublishingIntegrationTest;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles({"test-integration", "init", "init-test"})
@DisplayName("UserInitializer Integration Tests")
class UserInitializerIntegrationTest extends BaseEventPublishingIntegrationTest {

  private static final String TEST_EMAIL = "init-test@example.com";
  private static final String TEST_SYNC_EMAIL = "init-sync@example.com";
  private static final String TEST_GROUP_NAME = "Init Test Admins";
  private static final String TEST_SCOPED_GROUP_NAME = "Init Test Architects";

  @Autowired private GroupInitializer groupInitializer;
  @Autowired private UserInitializer userInitializer;
  @Autowired private PermissionInitializer permissionInitializer;
  @Autowired private RoleInitializer roleInitializer;
  @Autowired private GroupRepository groupRepository;
  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private TransactionTemplate txTemplate;

  /**
   * Re-seed permissions, roles, and groups before each test, then run the user initializer. The
   * base class {@code tearDown()} calls {@code portalData.cleanAll()} which deletes all roles and
   * groups. The {@link GroupInitializer} depends on seeded roles ("Tenant Admin", "Data
   * Architect"), and {@link UserInitializer} depends on seeded groups, so they must be re-created
   * in order. Permission and role re-seeding runs inside a transaction because {@link
   * RoleInitializer} accesses lazy-loaded permission collections.
   */
  @BeforeEach
  void reseedAndRunInitializers() {
    txTemplate.executeWithoutResult(
        status -> {
          permissionInitializer.initialize();
          roleInitializer.initialize();
        });
    groupInitializer.initialize();
    userInitializer.initialize();
  }

  @AfterEach
  void tearDownInitTest() {
    assignmentRepository.deleteAll();
    groupRepository.deleteAll();
  }

  @Test
  @DisplayName("Should create group with Tenant Admin assignment")
  void shouldCreateGroupWithRoleAssignment() {

    Group group = groupRepository.findByName(TEST_GROUP_NAME).orElseThrow();
    assertThat(group.getName()).isEqualTo(TEST_GROUP_NAME);
    assertThat(group.getDescription()).isEqualTo("Init test admin group");

    List<Assignment> assignments = assignmentRepository.findAllByGroupId(group.getId());
    assertThat(assignments).hasSize(1);
    assertThat(assignments.get(0).getRole().getName()).isEqualTo("Tenant Admin");
    assertThat(assignments.get(0).getScopeType()).isNull();
  }

  @Test
  @DisplayName("Should create group with scoped Data Architect assignment")
  void shouldCreateGroupWithScopedAssignment() {

    Group group = groupRepository.findByName(TEST_SCOPED_GROUP_NAME).orElseThrow();
    assertThat(group.getDescription()).isEqualTo("Init test data architect group");

    List<Assignment> assignments = assignmentRepository.findAllByGroupId(group.getId());
    assertThat(assignments).hasSize(1);
    assertThat(assignments.get(0).getRole().getName()).isEqualTo("Data Architect");
    assertThat(assignments.get(0).getScopeType()).isEqualTo(ScopeType.TENANT);
  }

  @Test
  @Transactional
  @DisplayName("Should sync user to Keycloak and persist externalId automatically")
  void shouldSyncUserAndSetExternalId() {

    Optional<User> user = userRepository.findByEmail(TEST_EMAIL);
    assertThat(user).isPresent();
    assertThat(user.get().getFirstName()).isEqualTo("Init");
    assertThat(user.get().getLastName()).isEqualTo("TestUser");
    assertThat(user.get().getActive()).isTrue();
    assertThat(user.get().getExternalId()).isNotBlank();

    UserRepresentation keycloakUser = findKeycloakUserByEmail(TEST_EMAIL);
    assertThat(keycloakUser).isNotNull();
    assertThat(keycloakUser.isEmailVerified()).isFalse();
    assertThat(keycloakUser.getRequiredActions())
        .contains("VERIFY_EMAIL", "UPDATE_PASSWORD", "CONFIGURE_TOTP");

    Group group = groupRepository.findByName(TEST_GROUP_NAME).orElseThrow();
    assertThat(group.getMembers()).extracting("email").containsExactly(TEST_EMAIL);
  }

  @Test
  @DisplayName(
      "Should sync user without externalId to Keycloak with requiredActions and emailVerified=false")
  void shouldSyncUserWithoutExternalIdToKeycloak() {

    Optional<User> user = userRepository.findByEmail(TEST_SYNC_EMAIL);
    assertThat(user).isPresent();
    assertThat(user.get().getFirstName()).isEqualTo("Init");
    assertThat(user.get().getLastName()).isEqualTo("SyncUser");
    assertThat(user.get().getActive()).isTrue();
    assertThat(user.get().getExternalId()).isNotBlank();

    UserRepresentation keycloakUser = findKeycloakUserByEmail(TEST_SYNC_EMAIL);
    assertThat(keycloakUser).isNotNull();
    assertThat(keycloakUser.isEmailVerified()).isFalse();
    assertThat(keycloakUser.getRequiredActions())
        .contains("VERIFY_EMAIL", "UPDATE_PASSWORD", "CONFIGURE_TOTP");
  }

  @Test
  @DisplayName("Should not duplicate groups, users or assignments when called twice")
  void shouldBeIdempotent() {
    groupInitializer.initialize();
    userInitializer.initialize();

    assertThat(groupRepository.count()).isEqualTo(2);
    assertThat(userRepository.count()).isEqualTo(2);
    assertThat(assignmentRepository.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("Should create all configured groups, users and assignments")
  void shouldCreateConfiguredGroupsAndUsers() {

    assertThat(groupRepository.count()).isEqualTo(2);
    assertThat(userRepository.count()).isEqualTo(2);
    assertThat(assignmentRepository.count()).isEqualTo(2);
  }
}
