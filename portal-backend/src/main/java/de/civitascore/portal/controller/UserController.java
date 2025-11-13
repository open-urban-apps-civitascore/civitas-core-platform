package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.UserInputDTO;
import de.civitascore.portal.model.output.PrincipalUserOutput;
import de.civitascore.portal.model.output.UserOutputDTO;
import de.civitascore.portal.model.output.assembler.UserAssembler;
import de.civitascore.portal.repository.specification.UserSpec;
import de.civitascore.portal.security.dto.PrincipalUserDetails;
import de.civitascore.portal.service.BaseService;
import de.civitascore.portal.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Users", description = "User management endpoints")
public class UserController
    extends BaseController<UserInputDTO, UserOutputDTO, User, UserSpec, String> {
  private final UserService userService;
  private final UserAssembler userAssembler;

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
        name = "externalId",
        description = "Filter by external ID (exact match).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "ext-123")),
    @Parameter(
        name = "q",
        description = "Search in firstName, lastName, or email (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "john"))
  })
  @Override
  public ResponseEntity<Page<UserOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") UserSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  @GetMapping("/me")
  @Operation(
      summary = "Get current user",
      description = "Returns the profile information of the authenticated user")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successful retrieval of user profile"),
        @ApiResponse(responseCode = "401", description = "If the user is not authenticated"),
        @ApiResponse(
            responseCode = "403",
            description = "If the user is authenticated but does not have sufficient permissions")
      })
  public ResponseEntity<PrincipalUserOutput> getCurrentUser(
      @AuthenticationPrincipal PrincipalUserDetails userPrincipal) {
    log.debug("UserController.getCurrentUser called by user: {}", userPrincipal.getUsername());
    return ResponseEntity.ok(PrincipalUserOutput.fromPrincipal(userPrincipal));
  }

  @Override
  protected BaseService<User, String, UserInputDTO> getService() {
    return userService;
  }

  @Override
  protected UserAssembler getAssembler() {
    return userAssembler;
  }
}
