package de.civitascore.portal.configuration;

import de.civitascore.portal.security.dto.PrincipalUserDetails;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Provides the current auditor (user) for JPA auditing.
 *
 * <p>This implementation retrieves the user ID directly from the JWT token in the Spring Security
 * context and falls back to "system" if no authenticated user is available.
 *
 * <p>Note: This does NOT query the database to avoid circular dependencies with JPA auditing.
 */
@Component
@Slf4j
public class AuditorAwareImpl implements AuditorAware<String> {

  private static final String SYSTEM_AUDITOR = "system";

  @Override
  @NonNull public Optional<String> getCurrentAuditor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      log.trace("No authenticated user found, using system auditor");
      return Optional.of(SYSTEM_AUDITOR);
    }

    if (authentication.getPrincipal() instanceof PrincipalUserDetails userDetails) {
      String userId = userDetails.getUserId();
      if (userId != null && !userId.isBlank()) {
        log.trace("Using auditor ID from JWT: {}", userId);
        return Optional.of(userId);
      }
      log.warn(
          "User ID is null or blank in JWT for email {}, using system auditor",
          userDetails.getEmail());
      return Optional.of(SYSTEM_AUDITOR);
    }

    log.trace("Principal is not PrincipalUserDetails, using system auditor");
    return Optional.of(SYSTEM_AUDITOR);
  }
}
