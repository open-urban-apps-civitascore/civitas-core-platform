package de.civitascore.portal.security.dto;

import de.civitascore.portal.model.embedded.UserTitleType;
import java.util.Collection;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Immutable user details extracted from a JWT token for use as the Spring Security principal.
 * Contains the user's portal UUID, OIDC profile claims (email, name), and realm role authorities.
 */
@Getter
@Builder
@Setter
@Data
public class PrincipalUserDetails implements UserDetails {
  @Builder.Default private final UserTitleType title = UserTitleType.OTHER;
  private final UUID userId;
  private final String username;
  private final String email;
  private final String givenName;
  private final String familyName;
  private final Collection<? extends GrantedAuthority> authorities;

  /** Always returns {@code null} since passwords are managed by the external identity provider. */
  @Override
  public String getPassword() {
    return null;
  }

  /** {@inheritDoc} Always returns {@code true} since account status is managed by Keycloak. */
  @Override
  public boolean isAccountNonExpired() {
    return UserDetails.super.isAccountNonExpired();
  }

  /** {@inheritDoc} Always returns {@code true} since account locking is managed by Keycloak. */
  @Override
  public boolean isAccountNonLocked() {
    return UserDetails.super.isAccountNonLocked();
  }

  /** {@inheritDoc} Always returns {@code true} since credential expiry is managed by Keycloak. */
  @Override
  public boolean isCredentialsNonExpired() {
    return UserDetails.super.isCredentialsNonExpired();
  }

  /** {@inheritDoc} Always returns {@code true} since account status is managed by Keycloak. */
  @Override
  public boolean isEnabled() {
    return UserDetails.super.isEnabled();
  }
}
