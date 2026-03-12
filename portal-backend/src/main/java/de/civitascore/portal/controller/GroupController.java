package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.input.GroupInputDTO;
import de.civitascore.portal.model.input.assignment.AssignmentGroupInputDTO;
import de.civitascore.portal.model.output.GroupOutputDTO;
import de.civitascore.portal.model.output.assembler.GroupAssembler;
import de.civitascore.portal.repository.specification.GroupSpec;
import de.civitascore.portal.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
@Tag(name = "Groups", description = "Group management API")
public class GroupController
    extends BaseController<GroupInputDTO, GroupOutputDTO, Group, GroupSpec> {

  private final GroupService groupService;
  private final GroupAssembler groupAssembler;

  @Parameters({
    @Parameter(
        name = "name",
        description = "Filter by name (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "Engineering")),
    @Parameter(
        name = "description",
        description = "Filter by description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "Development team")),
    @Parameter(
        name = "contactUserId",
        description = "Filter by contact user ID (exact match).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "user-123")),
    @Parameter(
        name = "parentGroupId",
        description = "Filter by parent group ID (exact match).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "group-456")),
    @Parameter(
        name = "q",
        description = "Search in name or description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "team"))
  })
  @Override
  public ResponseEntity<Page<GroupOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") GroupSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  @Override
  protected GroupService getService() {
    return groupService;
  }

  @Override
  protected GroupAssembler getAssembler() {
    return groupAssembler;
  }

  @PutMapping("/{groupId}/assignments")
  @Operation(
      summary = "Replace group assignments",
      description = "Replaces all role assignments for a group using diff-based semantics.")
  public ResponseEntity<GroupOutputDTO> replaceAssignments(
      @PathVariable UUID groupId, @Valid @RequestBody Set<AssignmentGroupInputDTO> assignments) {
    Group updated = groupService.replaceAssignments(groupId, assignments);
    GroupOutputDTO output = groupAssembler.toOutput(updated);
    return ResponseEntity.ok(output);
  }
}
