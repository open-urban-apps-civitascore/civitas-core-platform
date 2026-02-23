package de.civitascore.portal.service;

import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.model.ConfigValue;
import de.civitascore.configadapter.model.idm.UserConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserService extends EventPublishingService<User, UserInputDTO> {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final GroupRepository groupRepository;
  private final ObjectMapper objectMapper;
  private final String targetRealm;

  public UserService(
      ConfigEventPublisherService configEventPublisher,
      UserRepository userRepository,
      UserMapper userMapper,
      GroupRepository groupRepository,
      ObjectMapper objectMapper,
      @Value("${keycloak.target-realm}") String targetRealm) {
    super(configEventPublisher);
    this.userRepository = userRepository;
    this.userMapper = userMapper;
    this.groupRepository = groupRepository;
    this.objectMapper = objectMapper;
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

  @Override
  protected UserInputDTO preProcessCreateInput(UserInputDTO input) {
    validateGroupIdsExist(input.getGroupIds());
    return super.preProcessCreateInput(input);
  }

  @Override
  protected User preSave(User entity) {
    validateUniqueEmail(entity);
    return super.preSave(entity);
  }

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

    // Map groups
    if (entity.getGroups() != null && !entity.getGroups().isEmpty()) {
      List<String> groups = entity.getGroups().stream().map(Group::getName).toList();
      userConfig.setGroups(groups);
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

  @Override
  protected UserInputDTO preProcessUpdateInput(UserInputDTO input, User existingEntity) {
    validateGroupIdsExist(input.getGroupIds());

    try {
      String inputJson = objectMapper.writeValueAsString(input);
      JsonNode jsonNode = objectMapper.readTree(inputJson);

      if (jsonNode.has("title") && StringUtils.isBlank(jsonNode.get("title").asText())) {
        throw new InvalidInputException(
            "title", existingEntity.getId(), "Title cannot be null or blank");
      }
      if (jsonNode.has("firstName") && StringUtils.isBlank(jsonNode.get("firstName").asText())) {
        throw new InvalidInputException(
            "firstName", existingEntity.getId(), "First name cannot be null or blank");
      }
      if (jsonNode.has("lastName") && StringUtils.isBlank(jsonNode.get("lastName").asText())) {
        throw new InvalidInputException(
            "lastName", existingEntity.getId(), "Last name cannot be null or blank");
      }
      if (jsonNode.has("email") && StringUtils.isBlank(jsonNode.get("email").asText())) {
        throw new InvalidInputException(
            "email", existingEntity.getId(), "Email cannot be null or blank");
      }
    } catch (InvalidInputException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to process update input", e);
    }
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

  @Override
  protected void updateExternalId(User entity, String externalId) {
    if (externalId != null && !externalId.isBlank()) {
      entity.setExternalId(externalId);
    }
  }

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

  @Override
  protected String getRealm(User entity) {
    return targetRealm;
  }

  @Override
  protected String getConfigPath() {
    return "/users";
  }

  @Override
  protected ConfigValue toConfigValue(User entity) {
    return buildUserConfig(entity);
  }

  @Override
  protected UUID getEntityId(User entity) {
    return entity.getId();
  }
}
