package de.civitascore.portal.controller;

import de.civitascore.portal.model.output.PrincipalUserOutput;
import de.civitascore.portal.security.dto.PrincipalUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

  /**
   * Get the profile of the currently authenticated user.
   *
   * @param userPrincipal the authenticated user
   * @return UserDto with the user’s public profile information
   */
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
}
