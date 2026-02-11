package de.civitascore.portal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.input.AssignmentInputDTO;
import de.civitascore.portal.model.output.AssignmentOutputDTO;
import de.civitascore.portal.model.output.assembler.AssignmentAssembler;
import de.civitascore.portal.repository.specification.AssignmentSpec;
import de.civitascore.portal.service.AssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
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

@RestController
@RequestMapping("/assignments")
@RequiredArgsConstructor
@Tag(name = "Assignments", description = "Role assignment management endpoints")
public class AssignmentController
    extends BaseController<AssignmentInputDTO, AssignmentOutputDTO, Assignment, AssignmentSpec> {

  private final AssignmentService assignmentService;
  private final AssignmentAssembler assignmentAssembler;

  @Override
  AssignmentService getService() {
    return assignmentService;
  }

  @Override
  protected AssignmentAssembler getAssembler() {
    return assignmentAssembler;
  }

  @Parameters({
    @Parameter(
        name = "roleId",
        description = "Filter by role ID (exact match).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "role-123")),
    @Parameter(
        name = "userId",
        description = "Filter by user ID (exact match).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "user-456")),
    @Parameter(
        name = "groupId",
        description = "Filter by group ID (exact match).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "group-789")),
    @Parameter(
        name = "scopeId",
        description = "Filter by scope ID (exact match).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "scope-101112"))
  })
  @Override
  public ResponseEntity<Page<AssignmentOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") AssignmentSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  @PutMapping("/{id}")
  @Override
  @Operation(
      summary = "Update assignment (Not Supported)",
      description = "This operation is not supported. Assignments cannot be updated once created.",
      deprecated = true)
  public ResponseEntity<AssignmentOutputDTO> update(
      @PathVariable UUID id, @RequestBody AssignmentInputDTO input) {
    throw new UnsupportedOperationException("Assignment updates are not supported.");
  }

  @PatchMapping("/{id}")
  @Override
  @Operation(
      summary = "Patch assignment (Not Supported)",
      description = "This operation is not supported. Assignments cannot be patched once created.",
      deprecated = true)
  public ResponseEntity<AssignmentOutputDTO> patch(
      @PathVariable UUID id, @RequestBody JsonNode updates) {
    throw new UnsupportedOperationException("Assignment patches are not supported.");
  }
}
