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
import de.civitascore.portal.configuration.EventProperties;
import de.civitascore.portal.configuration.KeycloakProperties;
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
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupInitializer Tests")
class GroupInitializerTest {

  @Mock private GroupRepository groupRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private AssignmentRepository assignmentRepository;
  @Mock private ConfigEventPublisherService configEventPublisher;
  @Mock private org.springframework.transaction.PlatformTransactionManager transactionManager;

  private static final String TARGET_REALM = "test-realm";
  private static final String AUTH_SERVER_URL = "http://keycloak:8080";
  private static final KeycloakProperties KEYCLOAK_PROPERTIES =
      new KeycloakProperties(TARGET_REALM, AUTH_SERVER_URL, TARGET_REALM, true, false);
  private static final KeycloakProperties KEYCLOAK_PROPERTIES_BACKFILL_ON =
      new KeycloakProperties(TARGET_REALM, AUTH_SERVER_URL, TARGET_REALM, true, true);
  private static final int CONFIG_ADAPTER_TIMEOUT_SECONDS = 1;

  private GroupInitializer initializer;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient()
        .when(transactionManager.getTransaction(any()))
        .thenReturn(new SimpleTransactionStatus());
    initializer =
        new GroupInitializer(
            groupRepository,
            roleRepository,
            assignmentRepository,
            configEventPublisher,
            Optional.empty(),
            KEYCLOAK_PROPERTIES,
            new EventProperties(CONFIG_ADAPTER_TIMEOUT_SECONDS),
            transactionManager);
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

    @Test
    @DisplayName("skips a group with a blank name and still syncs the rest of the layer")
    void shouldSkipGroupWithBlankNameAndContinue() {
      Group blank = group("");
      Group ok = group("Ok");
      when(groupRepository.findByExternalIdIsNull()).thenReturn(List.of(blank, ok));

      // buildGroupConfig throws IllegalStateException for the blank-named group before any
      // publish, so only the valid group reaches the publisher.
      when(configEventPublisher.publishGroupCreated(eq("test-realm"), any(GroupConfig.class)))
          .thenReturn(CompletableFuture.completedFuture(successResult("kc-ok-id")));

      initializer.initialize();

      verify(configEventPublisher, times(1)).publishGroupCreated(eq("test-realm"), any());
      verify(groupRepository, times(1)).save(any());
      assertThat(ok.getExternalId()).isEqualTo("kc-ok-id");
      assertThat(blank.getExternalId()).isNull();
    }

    @Test
    @DisplayName("publishes a layer in parallel before awaiting any result")
    void shouldPublishLayerInParallel() {
      Group a = group("A");
      Group b = group("B");
      Group c = group("C");
      when(groupRepository.findByExternalIdIsNull()).thenReturn(List.of(a, b, c));

      // Force a single InOrder verification: all three publishGroupCreated calls must happen
      // before any save (which only happens after .get() returns). If the implementation were
      // sequential publish->wait->save, we'd see publish/save/publish/save/... instead.
      when(configEventPublisher.publishGroupCreated(eq("test-realm"), any(GroupConfig.class)))
          .thenReturn(CompletableFuture.completedFuture(successResult("kc-a")))
          .thenReturn(CompletableFuture.completedFuture(successResult("kc-b")))
          .thenReturn(CompletableFuture.completedFuture(successResult("kc-c")));

      initializer.initialize();

      org.mockito.InOrder order =
          org.mockito.Mockito.inOrder(configEventPublisher, groupRepository);
      order
          .verify(configEventPublisher, times(3))
          .publishGroupCreated(eq("test-realm"), any(GroupConfig.class));
      order.verify(groupRepository, times(3)).save(any(Group.class));
    }

    @Test
    @DisplayName("cancels the underlying future when the wait times out")
    void shouldCancelFutureOnTimeout() {
      Group g = group("Slow");
      when(groupRepository.findByExternalIdIsNull()).thenReturn(List.of(g));

      CompletableFuture<ConfigResultEvent> hanging = new CompletableFuture<>();
      when(configEventPublisher.publishGroupCreated(eq("test-realm"), any(GroupConfig.class)))
          .thenReturn(hanging);

      initializer.initialize();

      assertThat(hanging.isCancelled())
          .as("Timed-out future must be cancelled to free the publisher's correlation entry")
          .isTrue();
    }
  }

  @Nested
  @DisplayName("backfillMemberships")
  class BackfillMemberships {

    private GroupInitializer backfillOnInitializer() {
      return new GroupInitializer(
          groupRepository,
          roleRepository,
          assignmentRepository,
          configEventPublisher,
          Optional.empty(),
          KEYCLOAK_PROPERTIES_BACKFILL_ON,
          new EventProperties(CONFIG_ADAPTER_TIMEOUT_SECONDS),
          transactionManager);
    }

    private Group syncedGroup(String name, String externalId) {
      Group g = group(name);
      g.setExternalId(externalId);
      return g;
    }

    @Test
    @DisplayName("publishes GROUP_UPDATED once per already-synced group when the flag is on")
    void shouldPublishUpdatePerSyncedGroupWhenEnabled() {
      when(groupRepository.findByExternalIdIsNull()).thenReturn(List.of());
      when(groupRepository.findByExternalIdIsNotNull())
          .thenReturn(List.of(syncedGroup("Engineers", "kc-eng"), syncedGroup("Ops", "kc-ops")));
      when(configEventPublisher.publishGroupUpdated(eq("test-realm"), any(GroupConfig.class)))
          .thenReturn(CompletableFuture.completedFuture(successResult("kc-eng")))
          .thenReturn(CompletableFuture.completedFuture(successResult("kc-ops")));

      backfillOnInitializer().initialize();

      verify(configEventPublisher, times(2))
          .publishGroupUpdated(eq("test-realm"), any(GroupConfig.class));
      verify(configEventPublisher, never()).publishGroupCreated(any(), any());
    }

    @Test
    @DisplayName("never persists an externalId — these groups already have one")
    void shouldNotPersistExternalIdDuringBackfill() {
      when(groupRepository.findByExternalIdIsNull()).thenReturn(List.of());
      when(groupRepository.findByExternalIdIsNotNull())
          .thenReturn(List.of(syncedGroup("Engineers", "kc-eng")));
      when(configEventPublisher.publishGroupUpdated(eq("test-realm"), any(GroupConfig.class)))
          .thenReturn(CompletableFuture.completedFuture(successResult("kc-eng")));

      backfillOnInitializer().initialize();

      verify(groupRepository, never()).save(any());
    }

    @Test
    @DisplayName("is a no-op when the flag is off")
    void shouldNotBackfillWhenDisabled() {
      when(groupRepository.findByExternalIdIsNull()).thenReturn(List.of());

      // Default `initializer` is built with the flag off.
      initializer.initialize();

      verify(configEventPublisher, never()).publishGroupUpdated(any(), any());
      verify(groupRepository, never()).findByExternalIdIsNotNull();
    }

    @Test
    @DisplayName("continues to the next group when one backfill publish times out")
    void shouldContinueAfterTimeout() {
      when(groupRepository.findByExternalIdIsNull()).thenReturn(List.of());
      when(groupRepository.findByExternalIdIsNotNull())
          .thenReturn(List.of(syncedGroup("Slow", "kc-slow"), syncedGroup("Fast", "kc-fast")));

      CompletableFuture<ConfigResultEvent> hanging = new CompletableFuture<>();
      when(configEventPublisher.publishGroupUpdated(eq("test-realm"), any(GroupConfig.class)))
          .thenReturn(hanging)
          .thenReturn(CompletableFuture.completedFuture(successResult("kc-fast")));

      backfillOnInitializer().initialize();

      // Both groups are still published (parallel), and the hung one is cancelled on timeout.
      verify(configEventPublisher, times(2))
          .publishGroupUpdated(eq("test-realm"), any(GroupConfig.class));
      assertThat(hanging.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("skips a corrupt group (blank name) and still backfills the rest")
    void shouldSkipCorruptGroupAndContinue() {
      when(groupRepository.findByExternalIdIsNull()).thenReturn(List.of());
      when(groupRepository.findByExternalIdIsNotNull())
          .thenReturn(List.of(syncedGroup("", "kc-blank"), syncedGroup("Ok", "kc-ok")));
      // buildGroupConfig throws for the blank-named group, so only the valid one is published.
      when(configEventPublisher.publishGroupUpdated(eq("test-realm"), any(GroupConfig.class)))
          .thenReturn(CompletableFuture.completedFuture(successResult("kc-ok")));

      backfillOnInitializer().initialize();

      verify(configEventPublisher, times(1))
          .publishGroupUpdated(eq("test-realm"), any(GroupConfig.class));
    }

    @Test
    @DisplayName("does not fail when a backfill result is FAILURE or null")
    void shouldTolerateFailureAndNullResults() {
      when(groupRepository.findByExternalIdIsNull()).thenReturn(List.of());
      when(groupRepository.findByExternalIdIsNotNull())
          .thenReturn(List.of(syncedGroup("Failing", "kc-fail"), syncedGroup("Null", "kc-null")));
      when(configEventPublisher.publishGroupUpdated(eq("test-realm"), any(GroupConfig.class)))
          .thenReturn(CompletableFuture.completedFuture(failureResult("KEYCLOAK_GROUP_ERROR")))
          .thenReturn(CompletableFuture.completedFuture(null));

      backfillOnInitializer().initialize();

      // Both are attempted; neither a FAILURE status nor a null result throws or persists anything.
      verify(configEventPublisher, times(2))
          .publishGroupUpdated(eq("test-realm"), any(GroupConfig.class));
      verify(groupRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("listener order")
  class ListenerOrder {

    @Test
    @DisplayName("ORDER constant is strictly lower than Spring's LOWEST_PRECEDENCE default")
    void orderMustBeLowerThanUserInitializer() {
      // UserInitializer has no @Order — Spring defaults event listeners to LOWEST_PRECEDENCE.
      // GroupInitializer.ORDER must compare lower (run earlier) so user memberships can rely on
      // groups already having an externalId.
      assertThat(GroupInitializer.ORDER)
          .isLessThan(org.springframework.core.Ordered.LOWEST_PRECEDENCE);
    }
  }
}
