package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.input.GroupInputDTO;
import de.civitascore.portal.model.input.assignment.AssignmentGroupInputDTO;
import de.civitascore.portal.model.output.GroupOutputDTO;
import de.civitascore.portal.model.output.assembler.GroupAssembler;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import de.civitascore.portal.repository.specification.GroupSpec;
import de.civitascore.portal.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** REST controller for managing user group resources and their role assignments. */
@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
@Tag(name = "Groups", description = "Group management API")
public class GroupController {

  private final GroupService groupService;
  private final GroupAssembler groupAssembler;
  private final JsonMergePatch jsonMergePatch;

  @GetMapping
  @Operation(summary = "List all groups")
  public Page<GroupOutputDTO> listGroups(
      @ParameterObject GroupSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return groupAssembler.toOutput(groupService.findAll(spec, pageable));
  }

  @GetMapping("/{groupId}")
  @Operation(summary = "Get a group by ID")
  @Problems(ProblemType.NOT_FOUND)
  public GroupOutputDTO getGroup(@PathVariable UUID groupId) {
    return groupAssembler.toOutput(groupService.findByIdOrThrow(groupId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a new group")
  @Problems({
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION,
    ProblemType.EXTERNAL_SYSTEM_ERROR,
    ProblemType.EXTERNAL_SYSTEM_TIMEOUT
  })
  public ResponseEntity<GroupOutputDTO> createGroup(@Valid @RequestBody GroupInputDTO input) {
    Group created = groupService.create(input);
    return ResponseEntity.created(Locations.ofCreated(created.getId()))
        .body(groupAssembler.toOutput(created));
  }

  @PutMapping("/{groupId}")
  @Operation(summary = "Replace a group")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION,
    ProblemType.EXTERNAL_SYSTEM_ERROR,
    ProblemType.EXTERNAL_SYSTEM_TIMEOUT
  })
  public GroupOutputDTO updateGroup(
      @PathVariable UUID groupId, @Valid @RequestBody GroupInputDTO input) {
    return groupAssembler.toOutput(groupService.update(groupId, input));
  }

  @PatchMapping("/{groupId}")
  @Operation(
      summary = "Partially update a group",
      description = JsonMergePatch.OPERATION_DESCRIPTION)
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION,
    ProblemType.EXTERNAL_SYSTEM_ERROR,
    ProblemType.EXTERNAL_SYSTEM_TIMEOUT
  })
  public GroupOutputDTO patchGroup(@PathVariable UUID groupId, @RequestBody JsonNode updates) {
    GroupInputDTO current = groupAssembler.toInput(groupService.findByIdOrThrow(groupId));
    GroupInputDTO patched = jsonMergePatch.apply(current, updates, groupId);
    return groupAssembler.toOutput(groupService.update(groupId, patched));
  }

  @DeleteMapping("/{groupId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a group")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.FOREIGN_KEY_VIOLATION,
    ProblemType.EXTERNAL_SYSTEM_ERROR,
    ProblemType.EXTERNAL_SYSTEM_TIMEOUT
  })
  public void deleteGroup(@PathVariable UUID groupId) {
    groupService.deleteById(groupId);
  }

  @PutMapping("/{groupId}/assignments")
  @Operation(
      summary = "Replace group assignments",
      description = "Replaces all role assignments for a group using diff-based semantics.")
  @Problems(ProblemType.NOT_FOUND)
  public GroupOutputDTO replaceGroupAssignments(
      @PathVariable UUID groupId,
      // Spring MVC validates the elements of a Set only from an element @Valid.
      @Valid @RequestBody Set<@Valid AssignmentGroupInputDTO> assignments) {
    return groupAssembler.toOutput(groupService.replaceAssignments(groupId, assignments));
  }
}
