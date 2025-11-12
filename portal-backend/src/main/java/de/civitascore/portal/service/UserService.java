package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.entity.User_;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.repository.TenantAwareRepository;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService extends BaseTenantAwareService<User, String, UserInputDTO> {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final ObjectMapper objectMapper;

  @Override
  protected TenantAwareRepository<User, String> getRepository() {
    return userRepository;
  }

  @Override
  protected UserMapper getMapper() {
    return userMapper;
  }

  @Override
  protected String getEntityName() {
    return User.class.getSimpleName();
  }

  @Override
  protected User preSave(User entity) {
    // Validate unique constraint: email + tenant_id
    validateUniqueEmail(entity);
    return super.preSave(entity);
  }

  private void validateUniqueEmail(User entity) {
    userRepository
        .findByEmailAndTenantId(entity.getEmail(), entity.getTenantId())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    User.class.getSimpleName(),
                    User_.EMAIL,
                    entity.getEmail(),
                    User_.TENANT_ID,
                    entity.getTenantId());
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
            "firstName", "First name cannot be null or blank", existingEntity.getId().toString());
      }
      if (jsonNode.has("lastName") && StringUtils.isBlank(jsonNode.get("lastName").asText())) {
        throw new InvalidInputException(
            "lastName", "Last name cannot be null or blank", existingEntity.getId().toString());
      }
      if (jsonNode.has("email") && StringUtils.isBlank(jsonNode.get("email").asText())) {
        throw new InvalidInputException(
            "email", "Email cannot be null or blank", existingEntity.getId().toString());
      }
    } catch (InvalidInputException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to process update input", e);
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }
}
