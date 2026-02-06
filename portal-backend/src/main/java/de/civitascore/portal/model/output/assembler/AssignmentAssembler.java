package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.AssignmentMapper;
import de.civitascore.portal.mapper.GroupMapper;
import de.civitascore.portal.mapper.RoleMapper;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssignmentAssembler implements BaseAssembler<Assignment, AssignmentOutputDTO, UUID> {

  private final AssignmentMapper assignmentMapper;
  private final GroupMapper groupMapper;
  private final RoleMapper roleMapper;

  @Override
  public AssignmentOutputDTO mapToBaseDto(Assignment entity) {
    AssignmentOutputDTO output = assignmentMapper.toOutput(entity);

    // Map group
    if (entity.getGroup() != null) {
      output.setGroup(groupMapper.toSummary(entity.getGroup()));
    }

    // Map role
    if (entity.getRole() != null) {
      output.setRole(roleMapper.toSummary(entity.getRole()));
    }

    return output;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Assignment entity) {
    return (I) assignmentMapper.toInput(entity);
  }
}
