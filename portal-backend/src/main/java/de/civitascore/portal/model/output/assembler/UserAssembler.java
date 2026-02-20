package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.GroupMapper;
import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.output.UserOutputDTO;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserAssembler implements BaseAssembler<User, UserOutputDTO, UUID> {

  private final UserMapper userMapper;
  private final GroupMapper groupMapper;

  @Override
  public UserOutputDTO mapToBaseDto(User entity) {
    UserOutputDTO output = userMapper.toOutput(entity);

    // Map groups
    if (entity.getGroups() != null && !entity.getGroups().isEmpty()) {
      output.setGroups(
          entity.getGroups().stream().map(groupMapper::toSummary).collect(Collectors.toList()));
    }

    return output;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(User entity) {
    return (I) userMapper.toInput(entity);
  }
}
