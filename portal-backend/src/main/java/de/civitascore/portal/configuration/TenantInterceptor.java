package de.civitascore.portal.configuration;

import de.civitascore.portal.security.dto.PrincipalUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor that extracts and sets the tenant ID from the authenticated user's JWT token.
 *
 * <p>This interceptor runs before each request and populates the {@link TenantContext} with the
 * tenant ID from the {@link PrincipalUserDetails}. The tenant context is cleared after request
 * completion in {@link #afterCompletion}.
 */
@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth == null || !auth.isAuthenticated()) {
      log.debug(
          "No authenticated user found for request: {} {}",
          request.getMethod(),
          request.getRequestURI());
      return true;
    }

    if (!(auth.getPrincipal() instanceof PrincipalUserDetails principal)) {
      log.warn(
          "Principal is not of type PrincipalUserDetails: {} for request: {} {}",
          auth.getPrincipal().getClass().getSimpleName(),
          request.getMethod(),
          request.getRequestURI());
      return true;
    }

    String tenantId = principal.getTenantId();

    if (tenantId == null || tenantId.isBlank()) {
      log.warn(
          "No tenant ID found in JWT for user '{}' on request: {} {}",
          principal.getUsername(),
          request.getMethod(),
          request.getRequestURI());
      return true;
    }

    TenantContext.setTenantId(tenantId);
    log.debug(
        "Tenant ID '{}' set for user '{}' on request: {} {}",
        tenantId,
        principal.getUsername(),
        request.getMethod(),
        request.getRequestURI());

    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    TenantContext.clear();
  }
}
