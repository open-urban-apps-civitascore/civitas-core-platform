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

  @Override
  public String getPassword() {
    return "";
  }

  @Override
  public boolean isAccountNonExpired() {
    return UserDetails.super.isAccountNonExpired();
  }

  @Override
  public boolean isAccountNonLocked() {
    return UserDetails.super.isAccountNonLocked();
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return UserDetails.super.isCredentialsNonExpired();
  }

  @Override
  public boolean isEnabled() {
    return UserDetails.super.isEnabled();
  }
}
