package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.model.output.MeAssignmentOutputDTO;
import de.civitascore.portal.model.output.PrincipalUserOutput;
import de.civitascore.portal.model.output.UserOutputDTO;
import de.civitascore.portal.model.output.assembler.AssignmentAssembler;
import de.civitascore.portal.model.output.assembler.UserAssembler;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import de.civitascore.portal.repository.specification.UserSpec;
import de.civitascore.portal.security.dto.PrincipalUserDetails;
import de.civitascore.portal.service.AssignmentService;
import de.civitascore.portal.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

/** REST controller for managing user resources and retrieving the current authenticated user. */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Users", description = "User management endpoints")
public class UserController {

  private final UserService userService;
  private final UserAssembler userAssembler;
  private final AssignmentService assignmentService;
  private final AssignmentAssembler assignmentAssembler;
  private final JsonMergePatch jsonMergePatch;

  @GetMapping
  @Operation(summary = "List all users")
  public Page<UserOutputDTO> listUsers(
      @ParameterObject UserSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return userAssembler.toOutput(userService.findAll(spec, pageable));
  }

  @GetMapping("/{userId}")
  @Operation(summary = "Get a user by ID")
  @Problems(ProblemType.NOT_FOUND)
  public UserOutputDTO getUser(@PathVariable UUID userId) {
    return userAssembler.toOutput(userService.findByIdOrThrow(userId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a new user")
  @Problems({
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION,
    ProblemType.EXTERNAL_SYSTEM_ERROR,
    ProblemType.EXTERNAL_SYSTEM_TIMEOUT
  })
  public ResponseEntity<UserOutputDTO> createUser(@Valid @RequestBody UserInputDTO input) {
    User created = userService.create(input);
    return ResponseEntity.created(Locations.ofCreated(created.getId()))
        .body(userAssembler.toOutput(created));
  }

  @PutMapping("/{userId}")
  @Operation(summary = "Replace a user")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION,
    ProblemType.EXTERNAL_SYSTEM_ERROR,
    ProblemType.EXTERNAL_SYSTEM_TIMEOUT
  })
  public UserOutputDTO updateUser(
      @PathVariable UUID userId, @Valid @RequestBody UserInputDTO input) {
    return userAssembler.toOutput(userService.update(userId, input));
  }

  @PatchMapping("/{userId}")
  @Operation(
      summary = "Partially update a user",
      description = JsonMergePatch.OPERATION_DESCRIPTION)
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION,
    ProblemType.EXTERNAL_SYSTEM_ERROR,
    ProblemType.EXTERNAL_SYSTEM_TIMEOUT
  })
  public UserOutputDTO patchUser(@PathVariable UUID userId, @RequestBody JsonNode updates) {
    UserInputDTO current = userAssembler.toInput(userService.findByIdOrThrow(userId));
    UserInputDTO patched = jsonMergePatch.apply(current, updates, userId);
    return userAssembler.toOutput(userService.update(userId, patched));
  }

  @DeleteMapping("/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a user")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.FOREIGN_KEY_VIOLATION,
    ProblemType.EXTERNAL_SYSTEM_ERROR,
    ProblemType.EXTERNAL_SYSTEM_TIMEOUT
  })
  public void deleteUser(@PathVariable UUID userId) {
    userService.deleteById(userId);
  }

  @GetMapping("/me")
  @Operation(
      summary = "Get current user",
      description =
          "Returns the profile information of the authenticated user including assignments")
  public PrincipalUserOutput getCurrentUser(
      @AuthenticationPrincipal PrincipalUserDetails userPrincipal) {
    log.debug(
        "UserController.getCurrentUser called by user: {}",
        Encode.forJava(userPrincipal.getUsername()));

    List<MeAssignmentOutputDTO> assignments = List.of();
    if (userPrincipal.getUserId() != null) {
      assignments =
          assignmentAssembler.toMeAssignments(
              assignmentService.findAllByUserExternalId(userPrincipal.getUserId().toString()));
    }

    return PrincipalUserOutput.fromPrincipal(userPrincipal, assignments);
  }

  @PutMapping("/{userId}/groups")
  @Operation(
      summary = "Replace user group memberships",
      description =
          "Replaces all group memberships for a user with the provided list of group IDs.")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.EXTERNAL_SYSTEM_ERROR,
    ProblemType.EXTERNAL_SYSTEM_TIMEOUT
  })
  public UserOutputDTO replaceUserGroups(
      @PathVariable UUID userId, @RequestBody List<UUID> groupIds) {
    return userAssembler.toOutput(userService.replaceGroups(userId, groupIds));
  }
}
