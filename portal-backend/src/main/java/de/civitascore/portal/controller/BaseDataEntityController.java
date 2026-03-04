package de.civitascore.portal.controller;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.base.BaseDataEntity;
import de.civitascore.portal.model.input.BaseDataEntityInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.BaseOutputDTO;
import de.civitascore.portal.model.output.assembler.AssignmentAssembler;
import de.civitascore.portal.repository.specification.base.BaseSpec;
import de.civitascore.portal.service.AssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

public abstract class BaseDataEntityController<
        I extends BaseDataEntityInputDTO,
        O extends BaseOutputDTO,
        E extends BaseDataEntity,
        S extends BaseSpec<E>>
    extends BaseController<I, O, E, S> {

  @Autowired private AssignmentService assignmentService;
  @Autowired private AssignmentAssembler assignmentAssembler;

  protected abstract ScopeType getScopeType();

  @GetMapping("/{id}/assignments")
  @Operation(
      summary = "Get assignments",
      description = "Returns all role assignments scoped to this resource.")
  public ResponseEntity<List<AssignmentOutputDTO>> getAssignments(@PathVariable UUID id) {
    getService().findByIdOrThrow(id);
    List<Assignment> assignments =
        assignmentService.findAllByScopeTypeAndScopeId(getScopeType(), id);
    List<AssignmentOutputDTO> output =
        assignments.stream().map(assignmentAssembler::toOutput).toList();
    return ResponseEntity.ok(output);
  }
}
