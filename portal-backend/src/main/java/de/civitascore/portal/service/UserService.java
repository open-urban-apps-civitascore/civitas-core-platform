package de.civitascore.portal.service;

import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.repository.TenantAwareRepository;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService extends TenantAwareService<User, String, UserInputDTO> {

  private final UserRepository userRepository;
  private final UserMapper userMapper;

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
    return "User";
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
              // If it's an update and the existing entity is the same, skip validation
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    "User", "email", entity.getEmail(), "tenant", entity.getTenantId());
              }
            });
  }
}
