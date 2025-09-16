package de.civitascore.portal.security;

import de.civitascore.portal.security.dto.PrincipalUserDetails;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class CustomJwtAuthenticationToken extends JwtAuthenticationToken {
  private final PrincipalUserDetails principal;

  public CustomJwtAuthenticationToken(
      Jwt jwt, Collection<? extends GrantedAuthority> authorities, PrincipalUserDetails principal) {
    super(jwt, authorities, principal.getUsername());
    this.principal = principal;
  }

  @Override
  public PrincipalUserDetails getPrincipal() {
    return this.principal;
  }
}
