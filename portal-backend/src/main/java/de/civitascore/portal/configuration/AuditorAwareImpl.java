package de.civitascore.portal.configuration;

import de.civitascore.portal.security.dto.PrincipalUserDetails;
import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Provides the current auditor (user) for JPA auditing.
 *
 * <p>This implementation retrieves the username from the Spring Security context and falls back to
 * "system" if no authenticated user is available.
 */
@Component
public class AuditorAwareImpl implements AuditorAware<String> {

  @Override
  @NonNull public Optional<String> getCurrentAuditor() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication != null
        && authentication.getPrincipal() instanceof PrincipalUserDetails userDetails) {
      return Optional.of(userDetails.getUsername());
    }

    // Fallback for system operations (e.g., scheduled tasks, system initialization)
    return Optional.of("system");
  }
}
