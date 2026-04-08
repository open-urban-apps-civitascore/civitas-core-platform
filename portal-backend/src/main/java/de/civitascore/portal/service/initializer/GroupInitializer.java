package de.civitascore.portal.service.initializer;

import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.idm.GroupConfig;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.service.ConfigEventPublisherService;
import java.util.Comparator;
import java.util.List;
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
 * Catches up groups that exist in the database but have not yet been synced to Keycloak. Runs on
 * every startup (not gated by the {@code init} profile) to ensure all groups have a Keycloak
 * counterpart. Parent groups are synced before children to ensure parentId references resolve.
 */
@Component
@Slf4j
public class GroupInitializer {

  private final GroupRepository groupRepository;
  private final ConfigEventPublisherService configEventPublisher;

  @Value("${keycloak.target-realm}")
  private String targetRealm;

  @Value("${event.config-adapter-timeout-seconds:10}")
  private int configAdapterTimeoutSeconds;

  public GroupInitializer(
      GroupRepository groupRepository, ConfigEventPublisherService configEventPublisher) {
    this.groupRepository = groupRepository;
    this.configEventPublisher = configEventPublisher;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(10)
  @Transactional
  public void syncUnsyncedGroups() {
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
    GroupConfig groupConfig = new GroupConfig();
    groupConfig.setName(group.getName());
    groupConfig.setPath("/" + group.getName().toLowerCase().replaceAll("\\s+", "-"));

    if (group.getParentGroup() != null && group.getParentGroup().getExternalId() != null) {
      groupConfig.setParentId(group.getParentGroup().getExternalId());
    }

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
