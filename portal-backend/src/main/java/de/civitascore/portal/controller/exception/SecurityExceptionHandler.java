package de.civitascore.portal.controller.exception;

import de.civitascore.portal.problem.ProblemDetails;
import de.civitascore.portal.problem.ProblemType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Security exception handler that produces RFC 9457 Problem Detail responses for authentication and
 * authorization failures.
 *
 * <p>Implements both {@link AuthenticationEntryPoint} and {@link AccessDeniedHandler} so that
 * Spring Security delegates unauthenticated and forbidden requests here.
 */
@ControllerAdvice
// Ahead of FallbackExceptionHandler, whose catch-all would otherwise take the JwtException.
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ObjectMapper mapper = new JsonMapper();
  private final ProblemDetails problemDetails;

  public SecurityExceptionHandler(ProblemDetails problemDetails) {
    this.problemDetails = problemDetails;
  }

  /**
   * Handles unauthenticated requests by writing a 401 Problem Detail response.
   *
   * @param request the current HTTP request
   * @param response the HTTP response to write to
   * @param ex the authentication exception
   * @throws IOException if writing the response fails
   */
  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    log.warn("Authentication failed: {}", Encode.forJava(ex.getMessage()));
    writeProblemDetailResponse(request, response, ProblemType.UNAUTHORIZED);
  }

  /**
   * Handles access-denied requests by writing a 403 Problem Detail response.
   *
   * @param request the current HTTP request
   * @param response the HTTP response to write to
   * @param ex the access denied exception
   * @throws IOException if writing the response fails
   */
  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    log.warn("Access denied: {}", Encode.forJava(ex.getMessage()));
    writeProblemDetailResponse(request, response, ProblemType.ACCESS_DENIED);
  }

  /**
   * Handles JWT validation failures and returns a 401 Problem Detail response.
   *
   * @param ex the JWT exception
   * @param request the current HTTP request
   * @return a Problem Detail with HTTP 401 status
   */
  @ExceptionHandler(JwtException.class)
  public ProblemDetail handleJwtException(JwtException ex, HttpServletRequest request) {
    log.warn("JWT validation failed: {}", Encode.forJava(ex.getMessage()));
    return problemDetails.of(ProblemType.INVALID_TOKEN, URI.create(request.getRequestURI()));
  }

  private void writeProblemDetailResponse(
      HttpServletRequest request, HttpServletResponse response, ProblemType type)
      throws IOException {
    ProblemDetail problemDetail = problemDetails.of(type, URI.create(request.getRequestURI()));

    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setStatus(type.status().value());
    response.getWriter().write(mapper.writeValueAsString(problemDetail));
  }
}
