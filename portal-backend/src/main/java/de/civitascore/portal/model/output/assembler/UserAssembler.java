package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.output.UserOutputDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Minimal User assembler using your mapper + basic enrichments. */
@Component
@RequiredArgsConstructor
public class UserAssembler implements EntityAssembler<User, UserOutputDTO, String> {

  private final UserMapper userMapper;

  @Override
  public UserOutputDTO mapToBaseDto(User entity) {
    return userMapper.toOutput(entity); // Your mapper's basic field mapping
  }
}
