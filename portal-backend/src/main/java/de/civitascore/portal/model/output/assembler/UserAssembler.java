package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.output.UserOutputDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserAssembler implements BaseAssembler<User, UserOutputDTO, String> {

  private final UserMapper userMapper;

  @Override
  public UserOutputDTO mapToBaseDto(User entity) {
    return userMapper.toOutput(entity);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(User entity) {
    return (I) userMapper.toInput(entity);
  }
}
