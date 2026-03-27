package de.civitascore.portal.service;

import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.model.ConfigValue;
import de.civitascore.configadapter.model.idm.UserConfig;
import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link User} entities with Keycloak integration. Extends {@link
 * EventPublishingService} to synchronize user lifecycle events (create, update, delete) with
 * Keycloak via the config adapter pipeline. Handles group membership, email uniqueness validation,
 * and external ID tracking.
 */
@Service
@Slf4j
public class UserService extends EventPublishingService<User, UserInputDTO> {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final GroupRepository groupRepository;
  private final String targetRealm;

  public UserService(
      ConfigEventPublisherService configEventPublisher,
      UserRepository userRepository,
      UserMapper userMapper,
      GroupRepository groupRepository,
      @Value("${keycloak.target-realm}") String targetRealm) {
    super(configEventPublisher);
    this.userRepository = userRepository;
    this.userMapper = userMapper;
    this.groupRepository = groupRepository;
    this.targetRealm = targetRealm;
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
   * Validates that all referenced group IDs exist before creating the user.
   *
   * @param input the user creation input
   * @return the validated input
   * @throws InvalidInputException if any referenced group does not exist
   */
  @Override
  protected UserInputDTO preProcessCreateInput(UserInputDTO input) {
    validateGroupIdsExist(input.getGroupIds());
    return super.preProcessCreateInput(input);
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

  /**
   * Resolves and updates group membership from the input before publishing the user to Keycloak.
   * Fetches groups with eagerly loaded members and updates the bidirectional relationship.
   *
   * @param entity the saved user entity
   * @param input the user input containing group IDs
   * @return the entity with updated group memberships
   */
  @Override
  protected User prePublish(User entity, UserInputDTO input) {
    List<UUID> groupUUIDs = input.getGroupIds();
    // Handle group membership updates after the user has been saved
    if (groupUUIDs != null) {
      // Fetch the new groups from the input (with members eagerly loaded)
      Set<Group> newGroups = new HashSet<>(groupRepository.findAllByIdWithMembers(groupUUIDs));

      // Use the entity's setGroups helper to handle the bidirectional relationship
      entity.setGroups(newGroups);
    }

    return super.prePublish(entity, input);
  }

  private UserConfig buildUserConfig(User entity) {
    UserConfig userConfig = new UserConfig();

    // Set Keycloak user ID if it exists (required for UPDATE/DELETE operations)
    if (entity.getExternalId() != null && !entity.getExternalId().isBlank()) {
      userConfig.setId(entity.getExternalId());
    }

    userConfig.setUsername(entity.getEmail()); // Use email as username
    userConfig.setEmail(entity.getEmail());
    userConfig.setFirstName(entity.getFirstName());
    userConfig.setLastName(entity.getLastName());
    userConfig.setEnabled(true); // Default to enabled
    userConfig.setEmailVerified(false); // Default to not verified

    // Require email verification and password setup for new users
    if (entity.getExternalId() == null || entity.getExternalId().isBlank()) {
      userConfig.setRequiredActions(List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));
    }

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
   * Validates that all referenced group IDs exist before updating the user.
   *
   * @param input the user update input
   * @param existingEntity the current user entity
   * @return the validated input
   * @throws InvalidInputException if any referenced group does not exist
   */
  @Override
  protected UserInputDTO preProcessUpdateInput(UserInputDTO input, User existingEntity) {
    validateGroupIdsExist(input.getGroupIds());
    return input;
  }

  private void validateGroupIdsExist(List<UUID> groupIds) {
    if (groupIds == null || groupIds.isEmpty()) {
      return;
    }

    long foundCount = groupRepository.countByIdIn(groupIds);
    long missingCount = groupIds.size() - foundCount;

    if (missingCount > 0) {
      throw new InvalidInputException(
          "groups", missingCount + " groups not found", "One or more groups do not exist.");
    }
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
    return targetRealm;
  }

  @Override
  protected String getConfigPath() {
    return "/users";
  }

  /**
   * Converts a user entity into a {@link ConfigValue} (Keycloak user configuration) for the config
   * adapter event payload.
   *
   * @param entity the user entity
   * @return the Keycloak user configuration value
   */
  @Override
  protected ConfigValue toConfigValue(User entity) {
    return buildUserConfig(entity);
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
