package de.civitascore.portal.model.output;

import de.civitascore.portal.model.embedded.UserTitleType;
import de.civitascore.portal.security.dto.PrincipalUserDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;

public record PrincipalUserOutput(
    @Schema(description = "User's unique identifier", example = "testuser") String username,
    @Schema(description = "User's email address", example = "user@example.com") String email,
    @Schema(description = "User's title", example = "MS") UserTitleType title,
    @Schema(description = "User's first name", example = "John") String firstName,
    @Schema(description = "User's last name", example = "Doe") String lastName,
    @Schema(description = "User's roles", example = "[\"USER\", \"ADMIN\"]") List<String> roles) {
  // Factory method for clean mapping
  public static PrincipalUserOutput fromPrincipal(PrincipalUserDetails principal) {
    return new PrincipalUserOutput(
        principal.getUsername(),
        principal.getEmail(),
        principal.getTitle(),
        principal.getGivenName(),
        principal.getFamilyName(),
        principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(auth -> auth.replace("ROLE_", ""))
            .sorted()
            .toList());
  }
}
