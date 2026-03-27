package de.civitascore.portal.security;

import de.civitascore.portal.security.dto.PrincipalUserDetails;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Custom JWT authentication token that carries a {@link PrincipalUserDetails} principal with
 * extracted user profile information, instead of the default JWT subject string.
 */
public class CustomJwtAuthenticationToken extends JwtAuthenticationToken {
  private final PrincipalUserDetails principal;

  /**
   * Create a new authentication token with the given JWT, authorities, and user details.
   *
   * @param jwt the original JWT token
   * @param authorities the granted authorities extracted from the JWT
   * @param principal the user details extracted from the JWT claims
   */
  public CustomJwtAuthenticationToken(
      Jwt jwt, Collection<? extends GrantedAuthority> authorities, PrincipalUserDetails principal) {
    super(jwt, authorities, principal.getUsername());
    this.principal = principal;
  }

  /** Returns the {@link PrincipalUserDetails} containing the authenticated user's profile. */
  @Override
  public PrincipalUserDetails getPrincipal() {
    return this.principal;
  }
}
