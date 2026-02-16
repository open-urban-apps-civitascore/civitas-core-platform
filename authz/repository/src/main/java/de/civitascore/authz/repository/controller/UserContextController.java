package de.civitascore.authz.repository.controller;

import de.civitascore.authz.repository.model.dto.UserContextResponse;
import de.civitascore.authz.repository.service.UserContextService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for retrieving user authorization context.
 *
 * <p>Provides the user-context endpoint consumed by OPA for authorization decisions. Returns the
 * user's groups, role assignments, and permissions for a given Keycloak external ID.
 *
 * <p>This endpoint has no application-level authentication — security is enforced via network
 * policies and Linkerd mTLS (see deployment docs).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class UserContextController {

  private static final String UUID_PATTERN =
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

  private final UserContextService userContextService;

  @GetMapping("/user-context/{externalId}")
  public ResponseEntity<UserContextResponse> getUserContext(
      @PathVariable
          @NotBlank
          @Pattern(regexp = UUID_PATTERN, message = "externalId must be a valid UUID")
          String externalId) {
    return userContextService
        .getUserContext(externalId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}
