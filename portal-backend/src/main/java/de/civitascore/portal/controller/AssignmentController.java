package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.input.assignment.AssignmentInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.assembler.AssignmentAssembler;
import de.civitascore.portal.repository.specification.AssignmentSpec;
import de.civitascore.portal.service.AssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** REST controller for managing role-to-user/group assignment resources. */
@RestController
@RequestMapping("/assignments")
@RequiredArgsConstructor
@Tag(name = "Assignments", description = "Role assignment management endpoints")
public class AssignmentController
    extends BaseController<AssignmentInputDTO, AssignmentOutputDTO, Assignment, AssignmentSpec> {

  private final AssignmentService assignmentService;
  private final AssignmentAssembler assignmentAssembler;

  /** {@inheritDoc} */
  @Override
  AssignmentService getService() {
    return assignmentService;
  }

  /** {@inheritDoc} */
  @Override
  protected AssignmentAssembler getAssembler() {
    return assignmentAssembler;
  }

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
              + " DATASTRUCTURE, DATASPACE, CATALOG.",
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
  /**
   * Retrieves a paginated list of assignments with optional filtering by role, user, group, scope,
   * and role type.
   *
   * @param spec the assignment search/filter specification
   * @param pageable pagination and sorting parameters
   * @return a page of assignment output DTOs with HTTP 200 status
   */
  @Override
  public ResponseEntity<Page<AssignmentOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") AssignmentSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  /**
   * Update operation is not supported for assignments.
   *
   * @param id the assignment UUID (unused)
   * @param input the assignment input (unused)
   * @return never returns normally
   * @throws UnsupportedOperationException always
   */
  @PutMapping("/{id}")
  @Override
  @Operation(hidden = true)
  public ResponseEntity<AssignmentOutputDTO> update(
      @PathVariable UUID id, @RequestBody AssignmentInputDTO input) {
    throw new UnsupportedOperationException("Assignment updates are not supported.");
  }

  /**
   * Patch operation is not supported for assignments.
   *
   * @param id the assignment UUID (unused)
   * @param updates the JSON patch data (unused)
   * @return never returns normally
   * @throws UnsupportedOperationException always
   */
  @PatchMapping("/{id}")
  @Override
  @Operation(hidden = true)
  public ResponseEntity<AssignmentOutputDTO> patch(
      @PathVariable UUID id, @RequestBody JsonNode updates) {
    throw new UnsupportedOperationException("Assignment patches are not supported.");
  }
}
