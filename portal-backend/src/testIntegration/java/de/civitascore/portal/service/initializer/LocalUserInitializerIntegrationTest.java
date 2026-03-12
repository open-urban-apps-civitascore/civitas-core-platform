package de.civitascore.portal.service.initializer;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.service.event.BaseEventPublishingIntegrationTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"test-integration", "local-init", "local-init-test"})
@DisplayName("LocalUserInitializer Integration Tests")
class LocalUserInitializerIntegrationTest extends BaseEventPublishingIntegrationTest {

  private static final String TEST_EMAIL = "init-test@example.com";
  private static final String TEST_SYNC_EMAIL = "init-sync@example.com";
  private static final String TEST_GROUP_NAME = "Init Test Admins";
  private static final String TEST_SCOPED_GROUP_NAME = "Init Test Architects";

  @Autowired private LocalUserInitializer localUserInitializer;
  @Autowired private GroupRepository groupRepository;
  @Autowired private AssignmentRepository assignmentRepository;

  @AfterEach
  void tearDownInitTest() {
    assignmentRepository.deleteAll();
    groupRepository.deleteAll();
  }

  @Test
  @DisplayName("Should create group with Tenant Admin assignment")
  void shouldCreateGroupWithRoleAssignment() {
    localUserInitializer.initialize();

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
    localUserInitializer.initialize();

    Group group = groupRepository.findByName(TEST_SCOPED_GROUP_NAME).orElseThrow();
    assertThat(group.getDescription()).isEqualTo("Init test data architect group");

    List<Assignment> assignments = assignmentRepository.findAllByGroupId(group.getId());
    assertThat(assignments).hasSize(1);
    assertThat(assignments.get(0).getRole().getName()).isEqualTo("Data Architect");
    assertThat(assignments.get(0).getScopeType()).isEqualTo(ScopeType.TENANT);
  }

  @Test
  @DisplayName("Should sync user to Keycloak and persist externalId automatically")
  void shouldSyncUserAndSetExternalId() {
    localUserInitializer.initialize();

    Optional<User> user = userRepository.findByEmail(TEST_EMAIL);
    assertThat(user).isPresent();
    assertThat(user.get().getFirstName()).isEqualTo("Init");
    assertThat(user.get().getLastName()).isEqualTo("TestUser");
    assertThat(user.get().getActive()).isTrue();
    assertThat(user.get().getExternalId()).isNotBlank();

    assertThat(findKeycloakUserByEmail(TEST_EMAIL)).isNotNull();

    Group group = groupRepository.findByName(TEST_GROUP_NAME).orElseThrow();
    List<Group> groupsWithMembers = groupRepository.findAllByIdWithMembers(List.of(group.getId()));
    assertThat(groupsWithMembers.get(0).getMembers())
        .extracting("email")
        .containsExactly(TEST_EMAIL);
  }

  @Test
  @DisplayName("Should sync user without externalId to Keycloak and persist externalId")
  void shouldSyncUserWithoutExternalIdToKeycloak() {
    localUserInitializer.initialize();

    Optional<User> user = userRepository.findByEmail(TEST_SYNC_EMAIL);
    assertThat(user).isPresent();
    assertThat(user.get().getFirstName()).isEqualTo("Init");
    assertThat(user.get().getLastName()).isEqualTo("SyncUser");
    assertThat(user.get().getActive()).isTrue();
    assertThat(user.get().getExternalId()).isNotBlank();

    assertThat(findKeycloakUserByEmail(TEST_SYNC_EMAIL)).isNotNull();
  }

  @Test
  @DisplayName("Should not duplicate groups, users or assignments when called twice")
  void shouldBeIdempotent() {
    localUserInitializer.initialize();
    localUserInitializer.initialize();

    assertThat(groupRepository.count()).isEqualTo(2);
    assertThat(userRepository.count()).isEqualTo(2);
    assertThat(assignmentRepository.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("Should create all configured groups, users and assignments")
  void shouldCreateConfiguredGroupsAndUsers() {
    localUserInitializer.initialize();

    assertThat(groupRepository.count()).isEqualTo(2);
    assertThat(userRepository.count()).isEqualTo(2);
    assertThat(assignmentRepository.count()).isEqualTo(2);
  }
}
