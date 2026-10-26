package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.RoleInputDTO;
import de.civitascore.portal.model.output.RoleOutputDTO;
import de.civitascore.portal.model.output.assembler.RoleAssembler;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import de.civitascore.portal.repository.specification.RoleSpec;
import de.civitascore.portal.service.RoleService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** REST controller for managing role resources. */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Role management endpoints")
public class RoleController {

  private final RoleService roleService;
  private final RoleAssembler roleAssembler;
  private final JsonMergePatch jsonMergePatch;

  @GetMapping
  @Operation(summary = "List all roles")
  public Page<RoleOutputDTO> listRoles(
      @ParameterObject RoleSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return roleAssembler.toOutput(roleService.findAll(spec, pageable));
  }

  @GetMapping("/{roleId}")
  @Operation(summary = "Get a role by ID")
  @Problems(ProblemType.NOT_FOUND)
  public RoleOutputDTO getRole(@PathVariable UUID roleId) {
    return roleAssembler.toOutput(roleService.findByIdOrThrow(roleId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a new role")
  @Problems({ProblemType.INVALID_INPUT, ProblemType.UNIQUE_CONSTRAINT_VIOLATION})
  public ResponseEntity<RoleOutputDTO> createRole(@Valid @RequestBody RoleInputDTO input) {
    Role created = roleService.create(input);
    return ResponseEntity.created(Locations.ofCreated(created.getId()))
        .body(roleAssembler.toOutput(created));
  }

  @PutMapping("/{roleId}")
  @Operation(summary = "Replace a role")
  @Problems({ProblemType.FORBIDDEN, ProblemType.NOT_FOUND, ProblemType.UNIQUE_CONSTRAINT_VIOLATION})
  public RoleOutputDTO updateRole(
      @PathVariable UUID roleId, @Valid @RequestBody RoleInputDTO input) {
    return roleAssembler.toOutput(roleService.update(roleId, input));
  }

  @PatchMapping("/{roleId}")
  @Operation(
      summary = "Partially update a role",
      description = JsonMergePatch.OPERATION_DESCRIPTION)
  @Problems({ProblemType.FORBIDDEN, ProblemType.NOT_FOUND, ProblemType.UNIQUE_CONSTRAINT_VIOLATION})
  public RoleOutputDTO patchRole(@PathVariable UUID roleId, @RequestBody JsonNode updates) {
    RoleInputDTO current = roleAssembler.toInput(roleService.findByIdOrThrow(roleId));
    RoleInputDTO patched = jsonMergePatch.apply(current, updates, roleId);
    return roleAssembler.toOutput(roleService.update(roleId, patched));
  }

  @DeleteMapping("/{roleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a role")
  @Problems({ProblemType.FORBIDDEN, ProblemType.NOT_FOUND, ProblemType.FOREIGN_KEY_VIOLATION})
  public void deleteRole(@PathVariable UUID roleId) {
    roleService.deleteById(roleId);
  }
}
