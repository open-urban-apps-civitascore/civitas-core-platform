package de.civitascore.portal.controller;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.assembler.AssignmentAssembler;
import de.civitascore.portal.service.AssignmentService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Serves the assignments sub-resource of a data element. */
@Component
@RequiredArgsConstructor
class ScopeAssignments {

  private final AssignmentService assignmentService;
  private final AssignmentAssembler assignmentAssembler;

  /**
   * The assignments scoped to the data element. An unknown id gives an empty list, so the caller
   * first checks that the data element exists.
   */
  List<AssignmentOutputDTO> of(ScopeType scopeType, UUID scopeId) {
    return assignmentService.findAllByScopeTypeAndScopeId(scopeType, scopeId).stream()
        .map(assignmentAssembler::toOutput)
        .toList();
  }
}
