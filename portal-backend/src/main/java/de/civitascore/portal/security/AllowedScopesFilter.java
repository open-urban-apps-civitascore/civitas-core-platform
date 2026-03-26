package de.civitascore.portal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that extracts X-Allowed-Scope-Ids header from OPA and populates the request-scoped
 * {@link AllowedScopes} bean.
 *
 * <p>Runs after RequestContextFilter (-105) and Spring Security (-100) so that the request scope is
 * available for the {@link AllowedScopes} bean.
 *
 * @see AllowedScopes
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class AllowedScopesFilter extends OncePerRequestFilter {

  /** Header name set by OPA via APISIX send_headers_upstream. */
  public static final String HEADER_NAME = "X-Allowed-Scope-Ids";

  private final ObjectProvider<AllowedScopes> allowedScopesProvider;

  /**
   * Extracts the {@value HEADER_NAME} header from the request and populates the request-scoped
   * {@link AllowedScopes} bean with wildcard or specific scope IDs.
   *
   * @param request the incoming HTTP request
   * @param response the HTTP response
   * @param filterChain the filter chain to continue processing
   * @throws ServletException if an error occurs during filtering
   * @throws IOException if an I/O error occurs during filtering
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String header = request.getHeader(HEADER_NAME);

    if (header != null) {
      AllowedScopes scopes = allowedScopesProvider.getObject();

      if (AllowedScopes.WILDCARD.equals(header)) {
        scopes.setWildcard();
        log.debug("Scope filter: wildcard access (TENANT scope)");
      } else {
        Set<UUID> ids =
            Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::parseUuidSafely)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        scopes.setScopeIds(ids);
        log.debug("Scope filter: {} specific scope IDs", ids.size());
      }
    } else {
      log.trace("Scope filter: no {} header present", HEADER_NAME);
    }

    filterChain.doFilter(request, response);
  }

  private UUID parseUuidSafely(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      log.warn("Invalid UUID in {}: {}", HEADER_NAME, Encode.forJava(value));
      return null;
    }
  }
}
