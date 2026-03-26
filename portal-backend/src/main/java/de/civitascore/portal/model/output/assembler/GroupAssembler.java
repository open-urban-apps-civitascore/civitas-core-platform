package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.GroupMapper;
import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.GroupOutputDTO;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link Group} entities to {@link GroupOutputDTO}. Participates in the
 * template method pattern defined by {@link BaseAssembler}.
 */
@Component
@RequiredArgsConstructor
public class GroupAssembler implements BaseAssembler<Group, GroupOutputDTO, UUID> {

  private final GroupMapper groupMapper;
  private final UserMapper userMapper;
  private final AssignmentAssembler assignmentAssembler;

  /** {@inheritDoc} Maps group fields including contact user, members, and assignment summaries. */
  @Override
  public GroupOutputDTO mapToBaseDto(Group entity) {
    GroupOutputDTO output = groupMapper.toOutput(entity);

    // Map contactUser
    if (entity.getContactUser() != null) {
      output.setContactUser(userMapper.toSummary(entity.getContactUser()));
    }

    // Map members
    if (entity.getMembers() != null && !entity.getMembers().isEmpty()) {
      output.setMembers(
          entity.getMembers().stream().map(userMapper::toSummary).collect(Collectors.toList()));
    }

    // Map assignments
    if (entity.getAssignments() != null && !entity.getAssignments().isEmpty()) {
      List<AssignmentOutputDTO> assignments =
          entity.getAssignments().stream()
              .map(assignmentAssembler::mapToBaseDto)
              .collect(Collectors.toList());
      output.setAssignments(assignments);
    }

    return output;
  }

  /** {@inheritDoc} Converts a group entity back to its input DTO for PATCH operations. */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Group entity) {
    return (I) groupMapper.toInput(entity);
  }
}
