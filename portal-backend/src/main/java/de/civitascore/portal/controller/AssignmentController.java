package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.input.assignment.AssignmentInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.assembler.AssignmentAssembler;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import de.civitascore.portal.repository.specification.AssignmentSpec;
import de.civitascore.portal.service.AssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for managing role-to-user/group assignment resources. */
@RestController
@RequestMapping("/assignments")
@RequiredArgsConstructor
@Tag(name = "Assignments", description = "Role assignment management endpoints")
public class AssignmentController {

  private final AssignmentService assignmentService;
  private final AssignmentAssembler assignmentAssembler;

  @GetMapping
  @Operation(
      summary = "List all assignments",
      description =
          "Returns a paginated, filterable list of assignments. Supports sorting and"
              + " specification-based filtering.")
  public Page<AssignmentOutputDTO> listAssignments(
      @ParameterObject AssignmentSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return assignmentAssembler.toOutput(assignmentService.findAll(spec, pageable));
  }

  @GetMapping("/{assignmentId}")
  @Operation(
      summary = "Get assignment by ID",
      description = "Returns a single assignment identified by its UUID.")
  @Problems(ProblemType.NOT_FOUND)
  public AssignmentOutputDTO getAssignment(@PathVariable UUID assignmentId) {
    return assignmentAssembler.toOutput(assignmentService.findByIdOrThrow(assignmentId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Create a new assignment",
      description =
          "Creates a new assignment and returns it with a Location header pointing to the new"
              + " assignment URI.")
  @Problems({
    ProblemType.INVALID_INPUT,
    ProblemType.NOT_FOUND,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION
  })
  public ResponseEntity<AssignmentOutputDTO> createAssignment(
      @Valid @RequestBody AssignmentInputDTO input) {
    Assignment created = assignmentService.create(input);
    return ResponseEntity.created(Locations.ofCreated(created.getId()))
        .body(assignmentAssembler.toOutput(created));
  }

  @DeleteMapping("/{assignmentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Delete an assignment",
      description = "Permanently deletes an assignment by its UUID.")
  @Problems(ProblemType.NOT_FOUND)
  public void deleteAssignment(@PathVariable UUID assignmentId) {
    assignmentService.deleteById(assignmentId);
  }
}
