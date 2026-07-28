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

  /** Datapool header set by OPA for pool-inherited filtering, on collection and single reads. */
  public static final String HEADER_NAME_POOL = "X-Allowed-Pool-Ids";

  private final ObjectProvider<AllowedScopes> allowedScopesProvider;

  /**
   * Extracts the {@value HEADER_NAME} and {@value HEADER_NAME_POOL} headers from the request and
   * populates the request-scoped {@link AllowedScopes} bean with wildcard, specific scope IDs
   * and/or datapool IDs.
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

    String scopeHeader = request.getHeader(HEADER_NAME);
    String poolHeader = request.getHeader(HEADER_NAME_POOL);

    if (scopeHeader != null || poolHeader != null) {
      AllowedScopes scopes = allowedScopesProvider.getObject();

      if (scopeHeader != null) {
        if (AllowedScopes.WILDCARD.equals(scopeHeader)) {
          scopes.setWildcard();
          log.debug("Scope filter: wildcard access (TENANT scope)");
        } else {
          Set<UUID> ids = parseUuidSet(HEADER_NAME, scopeHeader);
          scopes.setScopeIds(ids);
          log.debug("Scope filter: {} specific scope IDs", ids.size());
        }
      }

      if (poolHeader != null) {
        Set<UUID> poolIds = parseUuidSet(HEADER_NAME_POOL, poolHeader);
        scopes.setPoolIds(poolIds);
        log.debug("Scope filter: {} datapool IDs", poolIds.size());
      }
    } else {
      log.trace("Scope filter: no {} / {} header present", HEADER_NAME, HEADER_NAME_POOL);
    }

    filterChain.doFilter(request, response);
  }

  private Set<UUID> parseUuidSet(String headerName, String header) {
    return Arrays.stream(header.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(value -> parseUuidSafely(headerName, value))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private UUID parseUuidSafely(String headerName, String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      log.warn("Invalid UUID in {}: {}", headerName, Encode.forJava(value));
      return null;
    }
  }
}
