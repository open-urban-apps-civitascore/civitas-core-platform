package de.civitascore.portal.service.initializer;

import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.idm.GroupConfig;
import de.civitascore.portal.configuration.InitProperties;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.service.ConfigEventPublisherService;
import de.civitascore.portal.service.GroupService;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Initializes groups and syncs them to Keycloak. Runs on every startup:
 *
 * <ul>
 *   <li>With {@code init} profile: creates groups from configuration properties and their
 *       assignments
 *   <li>Always: syncs all groups without a Keycloak reference ({@code externalId IS NULL})
 * </ul>
 *
 * <p>Must run before {@link UserInitializer} ({@code @Order(10)}) so that groups have Keycloak
 * references when user memberships are synced.
 */
@Component
@Slf4j
public class GroupInitializer {

  private final GroupRepository groupRepository;
  private final RoleRepository roleRepository;
  private final AssignmentRepository assignmentRepository;
  private final ConfigEventPublisherService configEventPublisher;
  private final Optional<InitProperties> initProperties;

  @Value("${keycloak.target-realm}")
  private String targetRealm;

  @Value("${event.config-adapter-timeout-seconds:10}")
  private int configAdapterTimeoutSeconds;

  public GroupInitializer(
      GroupRepository groupRepository,
      RoleRepository roleRepository,
      AssignmentRepository assignmentRepository,
      ConfigEventPublisherService configEventPublisher,
      Optional<InitProperties> initProperties) {
    this.groupRepository = groupRepository;
    this.roleRepository = roleRepository;
    this.assignmentRepository = assignmentRepository;
    this.configEventPublisher = configEventPublisher;
    this.initProperties = initProperties;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(10)
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
                log.warn(
                    "Role '{}' not found for group '{}' — skipping assignment",
                    roleName,
                    group.getName()));
  }

  private void syncUnsyncedGroups() {
    List<Group> unsyncedGroups = groupRepository.findByExternalIdIsNull();

    if (unsyncedGroups.isEmpty()) {
      log.debug("All groups already synced to Keycloak — skipping catch-up");
      return;
    }

    // Sort: root groups first (null parent), then by depth.
    // This ensures parent externalIds are available when syncing children.
    List<Group> sorted =
        unsyncedGroups.stream().sorted(Comparator.comparingInt(this::depth)).toList();

    log.info("Syncing {} unsynced groups to Keycloak", sorted.size());

    for (Group group : sorted) {
      publishGroupCreated(group);
    }

    log.info("Group catch-up sync completed");
  }

  private int depth(Group group) {
    int d = 0;
    Group current = group.getParentGroup();
    while (current != null) {
      d++;
      current = current.getParentGroup();
    }
    return d;
  }

  private void publishGroupCreated(Group group) {
    GroupConfig groupConfig = GroupService.buildGroupConfig(group);

    try {
      ConfigResultEvent result =
          configEventPublisher
              .publishGroupCreated(targetRealm, groupConfig)
              .get(configAdapterTimeoutSeconds, TimeUnit.SECONDS);

      if (result != null
          && result.status() == ConfigResultEvent.Status.SUCCESS
          && !Strings.isBlank(result.resourceId())) {
        group.setExternalId(result.resourceId());
        groupRepository.save(group);
        log.info(
            "Synced group '{}' to Keycloak, externalId={}", group.getName(), result.resourceId());
      } else if (result != null) {
        log.warn(
            "Keycloak sync for group '{}' failed: status={}, message={}, errorCode={}",
            group.getName(),
            result.status(),
            result.message(),
            result.errorCode());
      } else {
        log.warn("Keycloak sync for group '{}' returned null result", group.getName());
      }
    } catch (TimeoutException e) {
      log.error("Timeout waiting for Keycloak sync for group '{}'", group.getName());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while waiting for Keycloak sync for group '{}'", group.getName());
    } catch (ExecutionException e) {
      log.error("Failed to sync group '{}' to Keycloak", group.getName(), e);
    }
  }
}
