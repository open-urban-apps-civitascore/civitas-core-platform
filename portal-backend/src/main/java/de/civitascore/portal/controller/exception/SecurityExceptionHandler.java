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
 * Spring Security delegates unauthenticated and forbidden requests here, and answers the same
 * failures when a handler throws them.
 */
@ControllerAdvice
// Ahead of FallbackExceptionHandler, whose catch-all would otherwise take the security exceptions.
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
    writeProblemDetailResponse(request, response, handleAuthentication(ex));
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
    writeProblemDetailResponse(request, response, handleAccessDenied(ex));
  }

  /** Answers an authentication failure that a handler throws. */
  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleAuthentication(AuthenticationException ex) {
    return answer(ProblemType.UNAUTHORIZED, "Authentication failed", ex);
  }

  /** Answers an access denial that a handler throws, for example from method security. */
  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    return answer(ProblemType.ACCESS_DENIED, "Access denied", ex);
  }

  /**
   * Handles JWT validation failures and returns a 401 Problem Detail response.
   *
   * @param ex the JWT exception
   * @return a Problem Detail with HTTP 401 status
   */
  @ExceptionHandler(JwtException.class)
  public ProblemDetail handleJwtException(JwtException ex) {
    return answer(ProblemType.INVALID_TOKEN, "JWT validation failed", ex);
  }

  private ProblemDetail answer(ProblemType type, String logPrefix, RuntimeException ex) {
    log.warn("{}: {}", logPrefix, Encode.forJava(ex.getMessage()));
    return problemDetails.of(type);
  }

  private void writeProblemDetailResponse(
      HttpServletRequest request, HttpServletResponse response, ProblemDetail problemDetail)
      throws IOException {
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setStatus(problemDetail.getStatus());
    response.getWriter().write(mapper.writeValueAsString(problemDetail));
  }
}
