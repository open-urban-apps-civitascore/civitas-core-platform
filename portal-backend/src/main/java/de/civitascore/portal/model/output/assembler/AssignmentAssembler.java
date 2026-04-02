package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.AssignmentMapper;
import de.civitascore.portal.mapper.GroupMapper;
import de.civitascore.portal.mapper.RoleMapper;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.entity.base.NamedEntity;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.MeAssignmentOutputDTO;
import de.civitascore.portal.model.output.summary.DataEntitySummaryDTO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link Assignment} entities to {@link AssignmentOutputDTO}. Participates
 * in the template method pattern defined by {@link BaseAssembler}.
 */
@Component
@RequiredArgsConstructor
public class AssignmentAssembler implements BaseAssembler<Assignment, AssignmentOutputDTO, UUID> {

  private final AssignmentMapper assignmentMapper;
  private final GroupMapper groupMapper;
  private final RoleMapper roleMapper;

  /**
   * {@inheritDoc} Maps assignment fields including group summary, role summary, and resolved scope.
   */
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

  /**
   * Aggregates assignments by (scopeType, scopeId), merging permission names from all roles that
   * share the same scope. This deduplicates permissions and produces a compact representation.
   */
  public List<MeAssignmentOutputDTO> toMeAssignments(List<Assignment> assignments) {
    // Key: "scopeType:scopeId" → aggregated DTO
    Map<String, MeAssignmentOutputDTO> grouped = new LinkedHashMap<>();

    for (Assignment assignment : assignments) {
      ScopeType scopeType = assignment.getScopeType();
      NamedEntity scopeEntity = assignment.getScope();
      UUID scopeId = scopeEntity != null ? scopeEntity.getId() : null;

      String key = scopeType + ":" + scopeId;

      MeAssignmentOutputDTO dto =
          grouped.computeIfAbsent(
              key,
              k -> {
                MeAssignmentOutputDTO d = new MeAssignmentOutputDTO();
                d.setScopeType(scopeType);
                d.setScopeId(scopeId);
                d.setPermissions(new TreeSet<>());
                return d;
              });

      if (assignment.getRole() != null && assignment.getRole().getPermissions() != null) {
        for (Permission permission : assignment.getRole().getPermissions()) {
          dto.getPermissions().add(permission.getName());
        }
      }
    }

    return List.copyOf(grouped.values());
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

  /** {@inheritDoc} Converts an assignment entity back to its input DTO for PATCH operations. */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(Assignment entity) {
    return (I) assignmentMapper.toInput(entity);
  }
}
