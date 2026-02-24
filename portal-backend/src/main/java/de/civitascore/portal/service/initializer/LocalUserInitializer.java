package de.civitascore.portal.service.initializer;

import com.civitas.configadapter.model.idm.UserConfig;
import de.civitascore.portal.configuration.LocalInitProperties;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.service.ConfigEventPublisherService;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local-init")
@Slf4j
public class LocalUserInitializer {

  private final LocalInitProperties properties;
  private final UserRepository userRepository;
  private final GroupRepository groupRepository;
  private final RoleRepository roleRepository;
  private final AssignmentRepository assignmentRepository;
  private final ConfigEventPublisherService configEventPublisher;
  private final String targetRealm;

  public LocalUserInitializer(
      LocalInitProperties properties,
      UserRepository userRepository,
      GroupRepository groupRepository,
      RoleRepository roleRepository,
      AssignmentRepository assignmentRepository,
      ConfigEventPublisherService configEventPublisher,
      @Value("${keycloak.target-realm}") String targetRealm) {
    this.properties = properties;
    this.userRepository = userRepository;
    this.groupRepository = groupRepository;
    this.roleRepository = roleRepository;
    this.assignmentRepository = assignmentRepository;
    this.configEventPublisher = configEventPublisher;
    this.targetRealm = targetRealm;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void initialize() {
    if (properties.getGroups().isEmpty() && properties.getUsers().isEmpty()) {
      log.debug("No local users or groups configured — skipping local init");
      return;
    }

    log.info("Starting local user and group initialization");

    Map<String, Group> groupsByName = initializeGroups();
    initializeUsers(groupsByName);

    log.info("Local user and group initialization completed");
  }

  private Map<String, Group> initializeGroups() {
    Map<String, Group> groupsByName = new HashMap<>();

    for (LocalInitProperties.GroupEntry entry : properties.getGroups()) {
      Group group =
          groupRepository
              .findByName(entry.getName())
              .orElseGet(
                  () -> {
                    Group newGroup = new Group();
                    newGroup.setName(entry.getName());
                    newGroup.setDescription(entry.getDescription());
                    Group saved = groupRepository.save(newGroup);
                    log.info("Created local group '{}'", entry.getName());
                    return saved;
                  });

      groupsByName.put(entry.getName(), group);

      if (entry.getRoleName() != null) {
        createAssignmentIfAbsent(group, entry.getRoleName());
      }
    }

    return groupsByName;
  }

  private void createAssignmentIfAbsent(Group group, String roleName) {
    roleRepository
        .findByName(roleName)
        .ifPresentOrElse(
            role -> {
              if (assignmentRepository.existsByGroupAndRoleAndScopeTypeIsNull(group, role)) {
                log.debug(
                    "Assignment for group '{}' and role '{}' already exists — skipping",
                    group.getName(),
                    role.getName());
                return;
              }
              Assignment assignment = new Assignment();
              assignment.setGroup(group);
              assignment.setRole(role);
              assignmentRepository.save(assignment);
              log.info(
                  "Created assignment for group '{}' with role '{}'",
                  group.getName(),
                  role.getName());
            },
            () ->
                log.warn(
                    "Role '{}' not found for local group '{}' — skipping assignment",
                    roleName,
                    group.getName()));
  }

  private void initializeUsers(Map<String, Group> groupsByName) {
    for (LocalInitProperties.UserEntry entry : properties.getUsers()) {
      if (userRepository.findByEmail(entry.getEmail()).isPresent()) {
        log.debug("Local user '{}' already exists — skipping", entry.getEmail());
        continue;
      }

      User user = new User();
      user.setFirstName(entry.getFirstName());
      user.setLastName(entry.getLastName());
      user.setEmail(entry.getEmail());
      user.setTitle(entry.getTitle());
      user.setActive(true);

      if (entry.getExternalId() != null) {
        user.setExternalId(entry.getExternalId());
      }

      User createdUser = userRepository.save(user);

      entry
          .getGroups()
          .forEach(
              groupName -> {
                Group group = groupsByName.get(groupName);
                if (group != null) {
                  createdUser.addGroup(group);
                } else {
                  log.warn(
                      "Group '{}' not found for local user '{}' — skipping group assignment",
                      groupName,
                      entry.getEmail());
                }
              });

      log.info("Created local user '{}'", entry.getEmail());

      if (entry.getExternalId() == null) {
        publishUserCreated(createdUser);
      }
    }
  }

  private void publishUserCreated(User user) {
    UserConfig userConfig = new UserConfig();
    userConfig.setUsername(user.getEmail());
    userConfig.setEmail(user.getEmail());
    userConfig.setFirstName(user.getFirstName());
    userConfig.setLastName(user.getLastName());
    userConfig.setEnabled(true);
    userConfig.setEmailVerified(true);

    configEventPublisher
        .publishUserCreated(targetRealm, userConfig)
        .whenComplete(
            (ignored, ex) -> {
              if (ex != null) {
                log.error("Failed to sync local user '{}' to Keycloak", user.getEmail(), ex);
              } else {
                log.info("Synced local user '{}' to Keycloak via config adapter", user.getEmail());
              }
            });
  }
}
