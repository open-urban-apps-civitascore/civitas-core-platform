package de.civitascore.portal.service.initializer;

import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.idm.GroupConfig;
import de.civitascore.portal.configuration.EventProperties;
import de.civitascore.portal.configuration.KeycloakProperties;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.service.ConfigEventPublisherService;
import de.civitascore.portal.service.GroupService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Initializes groups and syncs them to Keycloak. Runs on every startup:
 *
 * <ul>
 *   <li>With {@code init} profile: creates groups from configuration properties and their
 *       assignments
 *   <li>Always: syncs all groups without a Keycloak reference ({@code externalId IS NULL}) in
 *       parallel per parent-depth layer, so a degraded Keycloak path cannot wedge startup linearly
 * </ul>
 *
 * <p>Listener order: {@link #initialize()} uses {@link #ORDER} which must compare lower than the
 * listener order of {@code UserInitializer.initialize()}. {@code UserInitializer} has no explicit
 * {@code @Order}, so Spring defaults it to {@link
 * org.springframework.core.Ordered#LOWEST_PRECEDENCE} — any low integer wins. This ordering is
 * required so that user memberships can be assigned to groups that already have a Keycloak {@code
 * externalId}.
 */
@Component
@Slf4j
public class GroupInitializer {

  /**
   * Listener order for {@link #initialize()}. Must be strictly lower than {@code UserInitializer}'s
   * listener order (currently the Spring default {@link
   * org.springframework.core.Ordered#LOWEST_PRECEDENCE}) so groups are synced to Keycloak before
   * user memberships are pushed.
   */
  public static final int ORDER = 10;

  private final GroupRepository groupRepository;
  private final RoleRepository roleRepository;
  private final AssignmentRepository assignmentRepository;
  private final ConfigEventPublisherService configEventPublisher;
  private final Optional<InitProperties> initProperties;
  private final KeycloakProperties keycloakProperties;
  private final EventProperties eventProperties;

  /**
   * Persists each Keycloak-confirmed {@code externalId} in its own committed transaction. A group
   * whose sync succeeds must not lose its {@code externalId} because a <em>later</em> group's sync
   * throws and rolls back the surrounding {@link #initialize()} transaction — otherwise the group
   * would be re-picked by {@code findByExternalIdIsNull()} on the next startup and its
   * GROUP_CREATED event republished for an object that already exists in Keycloak.
   */
  private final TransactionTemplate externalIdTxTemplate;

  public GroupInitializer(
      GroupRepository groupRepository,
      RoleRepository roleRepository,
      AssignmentRepository assignmentRepository,
      ConfigEventPublisherService configEventPublisher,
      Optional<InitProperties> initProperties,
      KeycloakProperties keycloakProperties,
      EventProperties eventProperties,
      PlatformTransactionManager transactionManager) {
    this.groupRepository = groupRepository;
    this.roleRepository = roleRepository;
    this.assignmentRepository = assignmentRepository;
    this.configEventPublisher = configEventPublisher;
    this.initProperties = initProperties;
    this.keycloakProperties = keycloakProperties;
    this.eventProperties = eventProperties;
    this.externalIdTxTemplate = new TransactionTemplate(transactionManager);
    this.externalIdTxTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(ORDER)
  @Transactional
  public void initialize() {
    initProperties.ifPresent(this::createGroupsFromConfig);
    syncUnsyncedGroups();
  }

  private void createGroupsFromConfig(InitProperties properties) {
    if (properties.getGroups().isEmpty()) {
      return;
    }

    log.info("Creating groups from init configuration");

    for (InitProperties.GroupEntry entry : properties.getGroups()) {
      if (Strings.isBlank(entry.getName())) {
        log.error("Skipping group entry with null or blank name in init configuration");
        continue;
      }

      Group group =
          groupRepository
              .findByName(entry.getName())
              .orElseGet(
                  () -> {
                    Group newGroup = new Group();
                    newGroup.setName(entry.getName());
                    newGroup.setDescription(entry.getDescription());
                    Group saved = groupRepository.save(newGroup);
                    log.info("Created group '{}'", entry.getName());
                    return saved;
                  });

      if (entry.getRoleName() != null) {
        createAssignmentIfAbsent(group, entry.getRoleName(), entry.getScopeType());
      }
    }
  }

  private void createAssignmentIfAbsent(Group group, String roleName, ScopeType scopeType) {
    roleRepository
        .findByName(roleName)
        .ifPresentOrElse(
            role -> {
              boolean exists =
                  scopeType == null
                      ? assignmentRepository.existsByGroupAndRoleAndScopeTypeIsNull(group, role)
                      : assignmentRepository.existsByGroupAndRoleAndScopeType(
                          group, role, scopeType);
              if (exists) {
                log.debug(
                    "Assignment for group '{}' and role '{}' already exists — skipping",
                    group.getName(),
                    role.getName());
                return;
              }
              Assignment assignment = new Assignment();
              assignment.setGroup(group);
              assignment.setRole(role);
              assignment.setScopeType(scopeType);
              assignmentRepository.save(assignment);
              log.info(
                  "Created assignment for group '{}' with role '{}'",
                  group.getName(),
                  role.getName());
            },
            () ->
                log.error(
                    "Role '{}' not found for group '{}' — skipping assignment. This is a "
                        + "configuration error, not a transient warning.",
                    roleName,
                    group.getName()));
  }

  private void syncUnsyncedGroups() {
    List<Group> unsyncedGroups = groupRepository.findByExternalIdIsNull();

    if (unsyncedGroups.isEmpty()) {
      log.debug("All groups already synced to Keycloak — skipping catch-up");
      return;
    }

    // Group by parent-depth so each layer's events can be fired in parallel while preserving the
    // parent-before-child ordering needed for nested groups (parent externalId must already exist
    // before a child can reference it).
    Map<Integer, List<Group>> byDepth =
        unsyncedGroups.stream()
            .sorted(Comparator.comparingInt(this::depth))
            .collect(Collectors.groupingBy(this::depth));

    log.info(
        "Syncing {} unsynced groups to Keycloak across {} depth layer(s)",
        unsyncedGroups.size(),
        byDepth.size());

    for (Map.Entry<Integer, List<Group>> layer : new java.util.TreeMap<>(byDepth).entrySet()) {
      syncLayerInParallel(layer.getKey(), layer.getValue());
    }

    log.info("Group catch-up sync completed");
  }

  private void syncLayerInParallel(int depth, List<Group> groupsAtDepth) {
    List<PendingSync> pending = new ArrayList<>(groupsAtDepth.size());
    for (Group group : groupsAtDepth) {
      GroupConfig groupConfig;
      try {
        groupConfig = GroupService.buildGroupConfig(group);
      } catch (IllegalStateException e) {
        // Corrupt row (null/blank name) — skip it so one bad group cannot abort the whole sweep.
        log.error("Skipping group id={} — invalid state: {}", group.getId(), e.getMessage());
        continue;
      }
      CompletableFuture<ConfigResultEvent> future =
          configEventPublisher.publishGroupCreated(keycloakProperties.targetRealm(), groupConfig);
      pending.add(new PendingSync(group, future));
    }

    log.info("Publishing {} group(s) at depth {} in parallel", pending.size(), depth);

    for (PendingSync entry : pending) {
      handleResult(entry);
    }
  }

  private void handleResult(PendingSync entry) {
    Group group = entry.group();
    try {
      ConfigResultEvent result =
          entry.future().get(eventProperties.configAdapterTimeoutSeconds(), TimeUnit.SECONDS);

      if (result != null
          && result.status() == ConfigResultEvent.Status.SUCCESS
          && !Strings.isBlank(result.resourceId())) {
        persistExternalId(group, result.resourceId());
        log.info(
            "Synced group '{}' to Keycloak, externalId={}", group.getName(), result.resourceId());
      } else if (result != null) {
        log.error(
            "Keycloak sync for group '{}' failed: status={}, message={}, errorCode={}",
            group.getName(),
            result.status(),
            result.message(),
            result.errorCode());
      } else {
        log.error("Keycloak sync for group '{}' returned null result", group.getName());
      }
    } catch (TimeoutException e) {
      // Cancel the future so the publisher's correlation map cleans up rather than holding the
      // pending entry indefinitely. A late Keycloak response is then silently dropped; the group
      // is re-picked up on the next startup via the externalId IS NULL predicate.
      entry.future().cancel(true);
      log.error("Timeout waiting for Keycloak sync for group '{}'", group.getName());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      entry.future().cancel(true);
      log.error("Interrupted while waiting for Keycloak sync for group '{}'", group.getName());
    } catch (ExecutionException e) {
      log.error("Failed to sync group '{}' to Keycloak", group.getName(), e);
    }
  }

  private void persistExternalId(Group group, String externalId) {
    externalIdTxTemplate.executeWithoutResult(
        status -> {
          group.setExternalId(externalId);
          groupRepository.save(group);
        });
  }

  /**
   * Returns the number of ancestors above this group. Includes a cycle guard: FK constraints make
   * cycles structurally impossible today, but a corrupt DB row would otherwise spin the loop
   * forever.
   */
  private int depth(Group group) {
    int d = 0;
    Set<UUID> visited = new HashSet<>();
    Group current = group.getParentGroup();
    while (current != null && visited.add(current.getId())) {
      d++;
      current = current.getParentGroup();
    }
    return d;
  }

  private record PendingSync(Group group, CompletableFuture<ConfigResultEvent> future) {}
}
