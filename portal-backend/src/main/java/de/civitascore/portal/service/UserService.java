package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.model.output.event.TopicResolver;
import de.civitascore.portal.model.output.event.UserEventDTO;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.service.event.SynchronousEventPublisher;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class UserService extends EventPublishingService<User, UserInputDTO> {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final ObjectMapper objectMapper;

  public UserService(
      SynchronousEventPublisher syncEventPublisher,
      TopicResolver topicResolver,
      UserRepository userRepository,
      UserMapper userMapper,
      ObjectMapper objectMapper) {
    super(syncEventPublisher, topicResolver);
    this.userRepository = userRepository;
    this.userMapper = userMapper;
    this.objectMapper = objectMapper;
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
  protected User preSave(User entity) {
    validateUniqueEmail(entity);
    return entity;
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
    try {
      String inputJson = objectMapper.writeValueAsString(input);
      JsonNode jsonNode = objectMapper.readTree(inputJson);

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

  @Override
  protected void updateExternalId(User entity, String externalId) {
    if (externalId != null && !externalId.isBlank()) {
      entity.setExternalId(externalId);
    }
  }

  @Override
  protected String getAggregateType() {
    return User.class.getSimpleName();
  }

  @Override
  protected String getRealm(User entity) {
    return "civitas";
  }

  @Override
  protected UUID getEntityId(User entity) {
    return entity.getId();
  }

  @Override
  protected UserEventDTO toKafkaRepresentation(User entity) {
    return new UserEventDTO(
        entity.getId(),
        entity.getFirstName(),
        entity.getLastName(),
        entity.getEmail(),
        entity.getActive(),
        entity.getExternalId());
  }
}
