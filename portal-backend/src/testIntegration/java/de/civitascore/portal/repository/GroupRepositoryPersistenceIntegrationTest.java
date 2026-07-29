package de.civitascore.portal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.User;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Group Repository Persistence Tests")
class GroupRepositoryPersistenceIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private GroupRepository groupRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager txManager;

  private final List<UUID> createdGroupIds = new ArrayList<>();
  private final List<UUID> createdUserIds = new ArrayList<>();

  @AfterEach
  void cleanup() {
    // Delete inside a transaction so removing a group also clears its group_members join rows
    // (deleting the owning side of the ManyToMany), then remove the users.
    new TransactionTemplate(txManager)
        .executeWithoutResult(
            status -> {
              createdGroupIds.forEach(
                  id -> groupRepository.findById(id).ifPresent(groupRepository::delete));
              userRepository.deleteAllById(createdUserIds);
            });
    createdGroupIds.clear();
    createdUserIds.clear();
  }

  private User syncedUser(String externalId) {
    User user =
        User.builder()
            .firstName("Member")
            .lastName("Test")
            .email("m." + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
            .externalId(externalId)
            .build();
    User saved = userRepository.saveAndFlush(user);
    createdUserIds.add(saved.getId());
    return saved;
  }

  @Test
  @DisplayName(
      "findByExternalIdIsNull eagerly loads members (no LazyInitializationException outside the session)")
  void findByExternalIdIsNullEagerlyLoadsMembers() {
    // Regression guard for the catch-up sync fix: GroupInitializer builds the sync payload from
    // findByExternalIdIsNull() results OUTSIDE any transaction, so members must be fetched eagerly
    // by the entity graph. A LazyInitializationException there is not an IllegalStateException, so
    // the initializer's catch block would not catch it — a regression would silently abort startup.
    User u1 = syncedUser("kc-user-1");
    User u2 = syncedUser("kc-user-2");

    Group group = new Group();
    group.setName("unsynced-" + UUID.randomUUID().toString().substring(0, 8));
    group.setMembers(new HashSet<>(List.of(u1, u2)));
    UUID gid = groupRepository.saveAndFlush(group).getId();
    createdGroupIds.add(gid);

    // Detach everything: members can only be present now if the query fetched them eagerly.
    entityManager.clear();

    Group loaded =
        groupRepository.findByExternalIdIsNull().stream()
            .filter(g -> g.getId().equals(gid))
            .findFirst()
            .orElseThrow();

    assertThat(Hibernate.isInitialized(loaded.getMembers()))
        .as("members must be eagerly fetched for the outside-transaction catch-up sync")
        .isTrue();
    assertThat(loaded.getMembers())
        .extracting(User::getExternalId)
        .containsExactlyInAnyOrder("kc-user-1", "kc-user-2");
  }
}
