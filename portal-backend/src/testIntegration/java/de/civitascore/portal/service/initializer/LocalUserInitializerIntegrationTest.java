package de.civitascore.portal.service.initializer;

import static org.assertj.core.api.Assertions.assertThat;

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

@ActiveProfiles({"test-integration", "local-init"})
@DisplayName("LocalUserInitializer Integration Tests")
class LocalUserInitializerIntegrationTest extends BaseEventPublishingIntegrationTest {

  private static final String TEST_EMAIL = "init-test@example.com";
  private static final String TEST_GROUP_NAME = "Init Test Admins";
  private static final String TEST_EXTERNAL_ID = "00000000-0000-0000-0000-000000000002";

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
  @DisplayName("Should create user and assign to group in database")
  void shouldCreateUserAssignedToGroup() {
    localUserInitializer.initialize();

    Optional<User> user = userRepository.findByEmail(TEST_EMAIL);
    assertThat(user).isPresent();
    assertThat(user.get().getFirstName()).isEqualTo("Init");
    assertThat(user.get().getLastName()).isEqualTo("TestUser");
    assertThat(user.get().getActive()).isTrue();
    assertThat(user.get().getExternalId()).isEqualTo(TEST_EXTERNAL_ID);

    Group group = groupRepository.findByName(TEST_GROUP_NAME).orElseThrow();
    List<Group> groupsWithMembers = groupRepository.findAllByIdWithMembers(List.of(group.getId()));
    assertThat(groupsWithMembers.get(0).getMembers())
        .extracting("email")
        .containsExactly(TEST_EMAIL);
  }

  @Test
  @DisplayName("Should not duplicate group, user or assignment when called twice")
  void shouldBeIdempotent() {
    localUserInitializer.initialize();
    localUserInitializer.initialize();

    assertThat(groupRepository.count()).isEqualTo(1);
    assertThat(userRepository.count()).isEqualTo(1);
    assertThat(assignmentRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("Should create configured group, user and assignment")
  void shouldCreateConfiguredGroupAndUser() {
    localUserInitializer.initialize();

    assertThat(groupRepository.count()).isEqualTo(1);
    assertThat(userRepository.count()).isEqualTo(1);
    assertThat(assignmentRepository.count()).isEqualTo(1);
  }
}
