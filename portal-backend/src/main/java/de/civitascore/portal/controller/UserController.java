package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.model.output.MeAssignmentOutputDTO;
import de.civitascore.portal.model.output.PrincipalUserOutput;
import de.civitascore.portal.model.output.UserOutputDTO;
import de.civitascore.portal.model.output.assembler.AssignmentAssembler;
import de.civitascore.portal.model.output.assembler.UserAssembler;
import de.civitascore.portal.repository.specification.UserSpec;
import de.civitascore.portal.security.dto.PrincipalUserDetails;
import de.civitascore.portal.service.AssignmentService;
import de.civitascore.portal.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for managing user resources and retrieving the current authenticated user. */
@RestController
@RequestMapping(path = "/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Users", description = "User management endpoints")
public class UserController extends BaseController<UserInputDTO, UserOutputDTO, User, UserSpec> {
  private final UserService userService;
  private final UserAssembler userAssembler;
  private final AssignmentService assignmentService;
  private final AssignmentAssembler assignmentAssembler;

  @Parameters({
    @Parameter(
        name = "firstName",
        description = "Filter by first name (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "John")),
    @Parameter(
        name = "lastName",
        description = "Filter by last name (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "Doe")),
    @Parameter(
        name = "email",
        description = "Filter by email (exact match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "john.doe@example.com")),
    @Parameter(
        name = "active",
        description = "Filter by active status.",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "boolean", example = "true")),
    @Parameter(
        name = "q",
        description = "Search in full name, or email (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "john doe, john@doe.com"))
  })
  /**
   * Retrieves a paginated list of users with optional filtering by first name, last name, email,
   * active status, or free-text search.
   *
   * @param spec the user search/filter specification
   * @param pageable pagination and sorting parameters
   * @return a page of user output DTOs with HTTP 200 status
   */
  @Override
  public ResponseEntity<Page<UserOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") UserSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  /**
   * Returns the profile of the currently authenticated user, including their role assignments.
   *
   * @param userPrincipal the authenticated user's principal details
   * @return the current user's profile output with HTTP 200 status
   */
  @GetMapping("/me")
  @Operation(
      operationId = "getCurrentUser",
      summary = "Get current user",
      description =
          "Returns the profile information of the authenticated user including assignments")
  @ApiResponse(responseCode = "200", description = "User profile returned successfully")
  public ResponseEntity<PrincipalUserOutput> getCurrentUser(
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

    return ResponseEntity.ok(PrincipalUserOutput.fromPrincipal(userPrincipal, assignments));
  }

  @PutMapping("/{userId}/groups")
  @Operation(
      operationId = "replaceUserGroups",
      summary = "Replace user group memberships",
      description =
          "Replaces all group memberships for a user with the provided list of group IDs.")
  @ApiResponse(responseCode = "200", description = "Group memberships updated successfully")
  public ResponseEntity<UserOutputDTO> replaceGroups(
      @PathVariable UUID userId, @RequestBody List<UUID> groupIds) {
    User updated = userService.replaceGroups(userId, groupIds);
    UserOutputDTO output = userAssembler.toOutput(updated);
    return ResponseEntity.ok(output);
  }

  /** {@inheritDoc} */
  @Override
  protected UserService getService() {
    return userService;
  }

  /** {@inheritDoc} */
  @Override
  protected UserAssembler getAssembler() {
    return userAssembler;
  }
}
