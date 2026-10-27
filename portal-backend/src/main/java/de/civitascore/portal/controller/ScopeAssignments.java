package de.civitascore.portal.controller;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.base.BaseDataEntity;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.assembler.AssignmentAssembler;
import de.civitascore.portal.service.AssignmentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Serves the assignments sub-resource of a data element. */
@Component
@RequiredArgsConstructor
class ScopeAssignments {

  private final AssignmentService assignmentService;
  private final AssignmentAssembler assignmentAssembler;

  /** The assignments scoped to the data element. */
  List<AssignmentOutputDTO> of(ScopeType scopeType, BaseDataEntity dataElement) {
    return assignmentService.findAllByScopeTypeAndScopeId(scopeType, dataElement.getId()).stream()
        .map(assignmentAssembler::toOutput)
        .toList();
  }
}
