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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
  @ApiResponse(responseCode = "200", description = "Page of assignments returned successfully")
  @Parameter(
      name = "id",
      description = "Filter by one or more IDs (comma-separated).",
      example = "1,2,3",
      in = ParameterIn.QUERY,
      schema = @Schema(type = "string", example = "1,2,3"))
  @Parameter(
      name = "createdAtFrom",
      description = "Filter by creation date greater than or equal to this value (ISO 8601).",
      in = ParameterIn.QUERY,
      schema = @Schema(type = "string", format = "date-time", example = "2024-01-01T00:00:00Z"))
  @Parameter(
      name = "createdAtTo",
      description = "Filter by creation date less than or equal to this value (ISO 8601).",
      in = ParameterIn.QUERY,
      schema = @Schema(type = "string", format = "date-time", example = "2024-12-31T23:59:59Z"))
  @Parameter(
      name = "modifiedAtFrom",
      description = "Filter by modification date greater than or equal to this value (ISO 8601).",
      in = ParameterIn.QUERY,
      schema = @Schema(type = "string", format = "date-time", example = "2024-01-01T00:00:00Z"))
  @Parameter(
      name = "modifiedAtTo",
      description = "Filter by modification date less than or equal to this value (ISO 8601).",
      in = ParameterIn.QUERY,
      schema = @Schema(type = "string", format = "date-time", example = "2024-12-31T23:59:59Z"))
  @Parameter(
      name = "roleId",
      description = "Filter by role ID (exact match).",
      in = ParameterIn.QUERY,
      schema = @Schema(type = "string", example = "role-123"))
  @Parameter(
      name = "userId",
      description = "Filter by user ID (exact match).",
      in = ParameterIn.QUERY,
      schema = @Schema(type = "string", example = "user-456"))
  @Parameter(
      name = "groupId",
      description = "Filter by group ID (exact match).",
      in = ParameterIn.QUERY,
      schema = @Schema(type = "string", example = "group-789"))
  @Parameter(
      name = "scopeId",
      description = "Filter by scope ID (exact match).",
      in = ParameterIn.QUERY,
      schema = @Schema(type = "string", example = "scope-101112"))
  @Parameter(
      name = "scopeType",
      description =
          "Filter by scope type (exact match). One of: TENANT, DATASET, DATASOURCE,"
              + " DATASTRUCTURE, DATAPOOL.",
      in = ParameterIn.QUERY,
      schema = @Schema(type = "string", example = "DATASET"))
  @Parameter(
      name = "roleType",
      description = "Filter by role type (exact match). One of: SYSTEM, DATA.",
      in = ParameterIn.QUERY,
      schema = @Schema(type = "string", example = "DATA"))
  @Parameter(
      name = "q",
      description =
          "Search in role name, role description, or group name"
              + " (partial match, case-insensitive).",
      in = ParameterIn.QUERY,
      schema = @Schema(type = "string", example = "admin"))
  public Page<AssignmentOutputDTO> listAssignments(
      @ParameterObject @Parameter(description = "Search/filter spec") AssignmentSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return assignmentAssembler.toOutput(assignmentService.findAll(spec, pageable));
  }

  @GetMapping("/{assignmentId}")
  @Operation(
      summary = "Get assignment by ID",
      description = "Returns a single assignment identified by its UUID.")
  @ApiResponse(responseCode = "200", description = "Assignment returned successfully")
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
  @ApiResponse(responseCode = "201", description = "Assignment created successfully")
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
  @ApiResponse(responseCode = "204", description = "Assignment deleted successfully")
  @Problems(ProblemType.NOT_FOUND)
  public void deleteAssignment(@PathVariable UUID assignmentId) {
    assignmentService.deleteById(assignmentId);
  }
}
