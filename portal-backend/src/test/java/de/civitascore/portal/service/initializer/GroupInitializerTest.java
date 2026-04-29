package de.civitascore.portal.service.initializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.idm.GroupConfig;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.service.ConfigEventPublisherService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupInitializer Tests")
class GroupInitializerTest {

  @Mock private GroupRepository groupRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private AssignmentRepository assignmentRepository;
  @Mock private ConfigEventPublisherService configEventPublisher;

  private GroupInitializer initializer;

  @BeforeEach
  void setUp() {
    initializer =
        new GroupInitializer(
            groupRepository,
            roleRepository,
            assignmentRepository,
            configEventPublisher,
            Optional.empty());
    ReflectionTestUtils.setField(initializer, "targetRealm", "test-realm");
    ReflectionTestUtils.setField(initializer, "configAdapterTimeoutSeconds", 1);
  }

  private Group group(String name) {
    Group g = new Group();
    g.setId(UUID.randomUUID());
    g.setName(name);
    return g;
  }

  private ConfigResultEvent successResult(String externalId) {
    return new ConfigResultEvent(
        "corr",
        "msg",
        ConfigResultEvent.Status.SUCCESS,
        "ok",
        externalId,
        Operation.CREATE,
        "test-realm",
        null,
        OffsetDateTime.now(),
        "keycloak",
        "idm");
  }

  private ConfigResultEvent failureResult(String errorCode) {
    return new ConfigResultEvent(
        "corr",
        "msg",
        ConfigResultEvent.Status.FAILURE,
        "kc rejected",
        null,
        Operation.CREATE,
        "test-realm",
        errorCode,
        OffsetDateTime.now(),
        "keycloak",
        "idm");
  }

  @Nested
  @DisplayName("syncUnsyncedGroups")
  class SyncUnsyncedGroups {

    @Test
    @DisplayName("does nothing when all groups already have externalId")
    void shouldSkipWhenAllSynced() {
      when(groupRepository.findByExternalIdIsNull()).thenReturn(List.of());

      initializer.initialize();

      verify(configEventPublisher, never()).publishGroupCreated(any(), any());
      verify(groupRepository, never()).save(any());
    }

    @Test
    @DisplayName("persists externalId on successful sync")
    void shouldPersistExternalIdOnSuccess() {
      Group g = group("Engineers");
      when(groupRepository.findByExternalIdIsNull()).thenReturn(List.of(g));
      when(configEventPublisher.publishGroupCreated(eq("test-realm"), any(GroupConfig.class)))
          .thenReturn(CompletableFuture.completedFuture(successResult("kc-engineers-id")));

      initializer.initialize();

      ArgumentCaptor<Group> savedCaptor = ArgumentCaptor.forClass(Group.class);
      verify(groupRepository).save(savedCaptor.capture());
      assertThat(savedCaptor.getValue().getExternalId()).isEqualTo("kc-engineers-id");
    }

    @Test
    @DisplayName("does not persist externalId when result status is FAILURE")
    void shouldNotPersistOnFailure() {
      Group g = group("Engineers");
      when(groupRepository.findByExternalIdIsNull()).thenReturn(List.of(g));
      when(configEventPublisher.publishGroupCreated(eq("test-realm"), any(GroupConfig.class)))
          .thenReturn(CompletableFuture.completedFuture(failureResult("KEYCLOAK_GROUP_ERROR")));

      initializer.initialize();

      verify(groupRepository, never()).save(any());
      assertThat(g.getExternalId()).isNull();
    }

    @Test
    @DisplayName("does not persist externalId when SUCCESS result has blank resourceId")
    void shouldNotPersistOnBlankResourceId() {
      Group g = group("Engineers");
      when(groupRepository.findByExternalIdIsNull()).thenReturn(List.of(g));
      when(configEventPublisher.publishGroupCreated(eq("test-realm"), any(GroupConfig.class)))
          .thenReturn(CompletableFuture.completedFuture(successResult("   ")));

      initializer.initialize();

      verify(groupRepository, never()).save(any());
    }

    @Test
    @DisplayName("does not persist externalId when result is null")
    void shouldNotPersistOnNullResult() {
      Group g = group("Engineers");
      when(groupRepository.findByExternalIdIsNull()).thenReturn(List.of(g));
      when(configEventPublisher.publishGroupCreated(eq("test-realm"), any(GroupConfig.class)))
          .thenReturn(CompletableFuture.completedFuture(null));

      initializer.initialize();

      verify(groupRepository, never()).save(any());
    }

    @Test
    @DisplayName("continues syncing remaining groups when one publish times out")
    void shouldContinueAfterTimeout() {
      Group slow = group("Slow");
      Group fast = group("Fast");
      when(groupRepository.findByExternalIdIsNull()).thenReturn(List.of(slow, fast));

      CompletableFuture<ConfigResultEvent> timeoutFuture = new CompletableFuture<>();
      // never completes — .get(1, SECONDS) in the initializer will TimeoutException
      when(configEventPublisher.publishGroupCreated(eq("test-realm"), any(GroupConfig.class)))
          .thenReturn(timeoutFuture)
          .thenReturn(CompletableFuture.completedFuture(successResult("kc-fast-id")));

      initializer.initialize();

      // Slow timed out → no save. Fast succeeded → one save.
      verify(groupRepository, times(1)).save(any());
      assertThat(fast.getExternalId()).isEqualTo("kc-fast-id");
      assertThat(slow.getExternalId()).isNull();
    }

    @Test
    @DisplayName("continues syncing remaining groups when one publish fails with exception")
    void shouldContinueAfterExecutionException() {
      Group broken = group("Broken");
      Group ok = group("Ok");
      when(groupRepository.findByExternalIdIsNull()).thenReturn(List.of(broken, ok));

      when(configEventPublisher.publishGroupCreated(eq("test-realm"), any(GroupConfig.class)))
          .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")))
          .thenReturn(CompletableFuture.completedFuture(successResult("kc-ok-id")));

      initializer.initialize();

      verify(groupRepository, times(1)).save(any());
      assertThat(ok.getExternalId()).isEqualTo("kc-ok-id");
      assertThat(broken.getExternalId()).isNull();
    }
  }
}
