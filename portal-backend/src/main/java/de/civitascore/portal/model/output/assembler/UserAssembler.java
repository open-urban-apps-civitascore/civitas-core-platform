package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataPoolMapper;
import de.civitascore.portal.mapper.GroupMapper;
import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.model.output.UserOutputDTO;
import de.civitascore.portal.service.AssignmentService;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link User} entities to {@link UserOutputDTO}. Participates in the
 * template method pattern defined by {@link BaseAssembler}.
 */
@Component
@RequiredArgsConstructor
public class UserAssembler implements BaseAssembler<User, UserOutputDTO> {

  private final UserMapper userMapper;
  private final GroupMapper groupMapper;
  private final DataPoolMapper dataPoolMapper;
  private final AssignmentService assignmentService;

  /** {@inheritDoc} Maps user fields including group summaries. */
  @Override
  public UserOutputDTO mapToBaseDto(User entity) {
    UserOutputDTO output = userMapper.toOutput(entity);

    // Map groups
    if (entity.getGroups() != null && !entity.getGroups().isEmpty()) {
      output.setGroups(
          entity.getGroups().stream().map(groupMapper::toSummary).collect(Collectors.toList()));
    }

    output.setDatapools(
        dataPoolMapper.toDataPoolSummaries(
            assignmentService.findAllByUserIdAndScopeType(entity.getId(), ScopeType.DATAPOOL)));

    return output;
  }

  /** Converts a user entity back to its input DTO for PATCH operations. */
  public UserInputDTO toInput(User entity) {
    return userMapper.toInput(entity);
  }
}
