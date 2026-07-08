package de.civitascore.portal.service;

import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.model.ConfigValue;
import de.civitascore.configadapter.model.idm.UserConfig;
import de.civitascore.portal.configuration.EventProperties;
import de.civitascore.portal.configuration.KeycloakProperties;
import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link User} entities with Keycloak integration. Extends {@link
 * EventPublishingService} to synchronize user lifecycle events (create, update, delete) with
 * Keycloak via the config adapter pipeline. Handles email uniqueness validation and external ID
 * tracking.
 */
@Service
@Slf4j
public class UserService extends EventPublishingService<User, UserInputDTO> {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final GroupRepository groupRepository;
  private final KeycloakProperties keycloakProperties;

  public UserService(
      ConfigEventPublisherService configEventPublisher,
      UserRepository userRepository,
      UserMapper userMapper,
      GroupRepository groupRepository,
      KeycloakProperties keycloakProperties,
      EventProperties eventProperties) {
    super(configEventPublisher, eventProperties);
    this.userRepository = userRepository;
    this.userMapper = userMapper;
    this.groupRepository = groupRepository;
    this.keycloakProperties = keycloakProperties;
  }

  @Override
  protected UserRepository getRepository() {
    return userRepository;
  }

  @Override
  protected UserMapper getMapper() {
    return userMapper;
  }

  @Override
  protected String getEntityName() {
    return "User";
  }

  /**
   * Validates email uniqueness before persisting the user entity.
   *
   * @param entity the user entity to validate
   * @return the validated entity
   * @throws UniqueConstraintViolationException if another user with the same email already exists
   */
  @Override
  protected User preSave(User entity) {
    validateUniqueEmail(entity);
    return super.preSave(entity);
  }

  @Transactional
  public User replaceGroups(UUID userId, List<UUID> groupIds) {
    User user = findByIdOrThrow(userId);

    if (user.getExternalId() == null || user.getExternalId().isBlank()) {
      throw new InvalidInputException(
          "user", "not synced", "User has not been synced to Keycloak yet; retry shortly.");
    }

    if (groupIds.isEmpty()) {
      user.setGroups(new HashSet<>());
    } else {
      List<Group> groups = groupRepository.findAllById(groupIds);
      if (groups.size() != groupIds.size()) {
        throw new InvalidInputException(
            "groups",
            (groupIds.size() - groups.size()) + " groups not found",
            "One or more groups do not exist.");
      }
      user.setGroups(new HashSet<>(groups));
    }

    user = getRepository().saveAndFlush(user);
    preValidateWithExternalSystem(user, null, "update", null);
    return user;
  }

  @Override
  protected ConfigValue toConfigValuePreSave(User entity, UserInputDTO input) {
    // Capture the current email before the entity is mutated so that toConfigValuePostSave
    // can reliably detect whether the email address was changed by the update.
    UserConfig config = new UserConfig();

    Set<String> requiredActions = new HashSet<>();

    boolean isNewUser = entity.getExternalId() == null || entity.getExternalId().isBlank();

    if (isNewUser) {
      requiredActions.add("VERIFY_EMAIL");
      requiredActions.add("UPDATE_PASSWORD");
      requiredActions.add("CONFIGURE_TOTP");
    }

    boolean hasEmailChanged = !Objects.equals(entity.getEmail(), input.getEmail());

    if (hasEmailChanged) {
      requiredActions.add("VERIFY_EMAIL");
    }

    config.setEmailVerified(!isNewUser && !hasEmailChanged);
    config.setRequiredActions(new ArrayList<>(requiredActions));

    return config;
  }

  @Override
  protected ConfigValue toConfigValuePostSave(
      User entity, UserInputDTO input, ConfigValue preSaveConfigValue) {

    UserConfig userConfig = preSaveConfigValue instanceof UserConfig uc ? uc : new UserConfig();

    // Set Keycloak user ID if it exists (required for UPDATE/DELETE operations)
    if (entity.getExternalId() != null && !entity.getExternalId().isBlank()) {
      userConfig.setId(entity.getExternalId());
    }

    userConfig.setUsername(entity.getEmail()); // Use email as username
    userConfig.setEmail(entity.getEmail());
    userConfig.setFirstName(entity.getFirstName());
    userConfig.setLastName(entity.getLastName());
    userConfig.setEnabled(true);
    // Use externalId (Keycloak group UUID) — stable across portal-side group renames.
    // Groups not yet synced to Keycloak (externalId == null) are excluded; the adapter cannot
    // assign a membership to a group that does not yet exist on the Keycloak side. The missing
    // memberships are re-attempted on the next user update after the catch-up sync runs.
    userConfig.setGroups(
        entity.getGroups().stream()
            .map(Group::getExternalId)
            .filter(Objects::nonNull)
            .filter(id -> !id.isBlank())
            .sorted()
            .toList());

    return userConfig;
  }

  private void validateUniqueEmail(User entity) {
    userRepository
        .findByEmail(entity.getEmail())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException("User", "email", entity.getEmail());
              }
            });
  }

  /**
   * Stores the Keycloak user ID on the entity after successful external system synchronization.
   *
   * @param entity the user entity to update
   * @param externalId the Keycloak user ID returned by the config adapter
   */
  @Override
  protected void updateExternalId(User entity, String externalId) {
    if (externalId != null && !externalId.isBlank()) {
      entity.setExternalId(externalId);
    }
  }

  /**
   * Maps a CRUD operation name to the corresponding Kafka topic for user events.
   *
   * @param operation the operation name ("create", "update", or "delete")
   * @return the matching {@link Topics} enum value
   * @throws IllegalArgumentException if the operation is unknown
   */
  @Override
  protected Topics resolveTopic(String operation) {
    return switch (operation.toLowerCase()) {
      case "create" -> Topics.USER_CREATED;
      case "update" -> Topics.USER_UPDATED;
      case "delete" -> Topics.USER_DELETED;
      default -> throw new IllegalArgumentException("Unknown operation for User: " + operation);
    };
  }

  @Override
  protected String getTargetComponent() {
    return "user";
  }

  /**
   * Returns the Keycloak realm for the user entity, used as the target resource in config events.
   *
   * @param entity the user entity
   * @return the configured target realm name
   */
  @Override
  protected String getRealm(User entity) {
    return keycloakProperties.targetRealm();
  }

  @Override
  protected String getConfigPath() {
    return "/users";
  }

  /**
   * Extracts the entity ID from the user, used for correlation in config adapter events.
   *
   * @param entity the user entity
   * @return the user's UUID
   */
  @Override
  protected UUID getEntityId(User entity) {
    return entity.getId();
  }
}
