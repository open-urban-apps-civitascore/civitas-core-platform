package de.civitascore.portal.service.initializer;

import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.idm.UserConfig;
import de.civitascore.configadapter.model.idm.UserConfig.CredentialConfig;
import de.civitascore.portal.configuration.InitProperties;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.service.ConfigEventPublisherService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("init")
@Slf4j
public class UserInitializer {

  private final InitProperties properties;
  private final UserRepository userRepository;
  private final GroupRepository groupRepository;
  private final RoleRepository roleRepository;
  private final AssignmentRepository assignmentRepository;
  private final ConfigEventPublisherService configEventPublisher;
  private final Environment environment;

  @Value("${keycloak.target-realm}")
  private String targetRealm;

  @Value("${event.config-adapter-timeout-seconds:10}")
  private int configAdapterTimeoutSeconds;

  public UserInitializer(
      InitProperties properties,
      UserRepository userRepository,
      GroupRepository groupRepository,
      RoleRepository roleRepository,
      AssignmentRepository assignmentRepository,
      ConfigEventPublisherService configEventPublisher,
      Environment environment) {
    this.properties = properties;
    this.userRepository = userRepository;
    this.groupRepository = groupRepository;
    this.roleRepository = roleRepository;
    this.assignmentRepository = assignmentRepository;
    this.configEventPublisher = configEventPublisher;
    this.environment = environment;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void initialize() {
    if (properties.getGroups().isEmpty() && properties.getUsers().isEmpty()) {
      log.debug("No users or groups configured — skipping init");
      return;
    }

    log.info("Starting user and group initialization");

    Map<String, Group> groupsByName = initializeGroups();
    initializeUsers(groupsByName);

    log.info("User and group initialization completed");
  }

  private Map<String, Group> initializeGroups() {
    Map<String, Group> groupsByName = new HashMap<>();

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

      groupsByName.put(entry.getName(), group);

      if (entry.getRoleName() != null) {
        createAssignmentIfAbsent(group, entry.getRoleName(), entry.getScopeType());
      }
    }

    return groupsByName;
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

  private void initializeUsers(Map<String, Group> groupsByName) {
    for (InitProperties.UserEntry entry : properties.getUsers()) {
      if (userRepository.findByEmail(entry.getEmail()).isPresent()) {
        log.debug("User '{}' already exists — skipping", entry.getEmail());
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
                      "Group '{}' not found for user '{}' — skipping group assignment",
                      groupName,
                      entry.getEmail());
                }
              });

      log.info("Created user '{}'", entry.getEmail());

      if (entry.getExternalId() == null) {
        publishUserCreated(createdUser, entry.getPassword());
      }
    }
  }

  private void publishUserCreated(User user, String password) {
    UserConfig userConfig = new UserConfig();
    userConfig.setUsername(user.getEmail());
    userConfig.setEmail(user.getEmail());
    userConfig.setFirstName(user.getFirstName());
    userConfig.setLastName(user.getLastName());
    userConfig.setEnabled(true);
    userConfig.setEmailVerified(true);

    if (password != null && !password.isBlank() && environment.matchesProfiles("local")) {
      CredentialConfig credential = new CredentialConfig();
      credential.setType("password");
      credential.setValue(password);
      credential.setTemporary(false);
      userConfig.setCredentials(List.of(credential));
    }

    try {
      ConfigResultEvent result =
          configEventPublisher
              .publishUserCreated(targetRealm, userConfig)
              .get(configAdapterTimeoutSeconds, TimeUnit.SECONDS);

      if (result != null
          && result.status() == ConfigResultEvent.Status.SUCCESS
          && !Strings.isBlank(result.resourceId())) {
        user.setExternalId(result.resourceId());
        userRepository.save(user);
        log.info(
            "Synced user '{}' to Keycloak, externalId={}", user.getEmail(), result.resourceId());
      } else if (result != null) {
        log.warn(
            "Keycloak sync for user '{}' failed: status={}, message={}, errorCode={}",
            user.getEmail(),
            result.status(),
            result.message(),
            result.errorCode());
      } else {
        log.warn("Keycloak sync for user '{}' returned null result", user.getEmail());
      }
    } catch (TimeoutException e) {
      log.error("Timeout waiting for Keycloak sync for user '{}'", user.getEmail());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while waiting for Keycloak sync for user '{}'", user.getEmail());
    } catch (java.util.concurrent.ExecutionException e) {
      log.error("Failed to sync user '{}' to Keycloak", user.getEmail(), e);
    }
  }
}
