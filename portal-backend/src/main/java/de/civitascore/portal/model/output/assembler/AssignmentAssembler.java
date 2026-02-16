package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.AssignmentMapper;
import de.civitascore.portal.mapper.GroupMapper;
import de.civitascore.portal.mapper.RoleMapper;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.base.NamedEntity;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.summary.DataEntitySummaryDTO;
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

    // Map scope
    output.setScope(resolveScopeSummary(entity));

    return output;
  }

  private DataEntitySummaryDTO resolveScopeSummary(Assignment entity) {
    ScopeType scopeType = entity.getScopeType();
    NamedEntity scopeEntity = entity.getScope();

    boolean scopeRequired = scopeType != null && scopeType != ScopeType.TENANT;

    if (scopeRequired && scopeEntity == null) {
      throw new IllegalStateException(
          "Assignment "
              + entity.getId()
              + " has scopeType "
              + scopeType
              + " but no scope entity. The referenced scope may have been deleted.");
    }

    if (scopeEntity == null) {
      return null;
    }

    DataEntitySummaryDTO summary = new DataEntitySummaryDTO();
    summary.setId(scopeEntity.getId());
    summary.setName(scopeEntity.getName());
    return summary;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Assignment entity) {
    return (I) assignmentMapper.toInput(entity);
  }
}
