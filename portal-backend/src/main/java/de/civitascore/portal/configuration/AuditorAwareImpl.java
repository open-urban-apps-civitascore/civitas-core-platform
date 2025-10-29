package de.civitascore.portal.configuration;

import de.civitascore.portal.security.dto.PrincipalUserDetails;
import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuditorAwareImpl implements AuditorAware<String> {

  @Override
  @NonNull public Optional<String> getCurrentAuditor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.of("system");
    }

    PrincipalUserDetails userDetails = (PrincipalUserDetails) authentication.getPrincipal();
    String username = userDetails.getUsername();
    return Optional.ofNullable(username);
  }
}
