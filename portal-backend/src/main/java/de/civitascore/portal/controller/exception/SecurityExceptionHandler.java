package de.civitascore.portal.controller.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Security exception handler that produces RFC 9457 Problem Detail responses for authentication and
 * authorization failures.
 *
 * <p>Implements both {@link AuthenticationEntryPoint} and {@link AccessDeniedHandler} so that
 * Spring Security delegates unauthenticated and forbidden requests here.
 */
@ControllerAdvice
@Slf4j
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private static final String ERROR_URN_PREFIX = "urn:civitas:error:";
  private final ObjectMapper mapper = new ObjectMapper();

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
    log.warn("Authentication failed: {}", ex.getMessage());
    writeProblemDetailResponse(
        request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
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
    log.warn("Access denied: {}", ex.getMessage());
    writeProblemDetailResponse(
        request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Insufficient privileges");
  }

  /**
   * Handles JWT validation failures and returns a 401 Problem Detail response.
   *
   * @param ex the JWT exception
   * @param request the current HTTP request
   * @return a Problem Detail with HTTP 401 status
   */
  @ExceptionHandler(JwtException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ProblemDetail handleJwtException(JwtException ex, HttpServletRequest request) {
    log.warn("JWT validation failed: {}", ex.getMessage());
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "JWT validation failed");
    problemDetail.setType(URI.create(ERROR_URN_PREFIX + "INVALID_TOKEN"));
    problemDetail.setTitle(HttpStatus.UNAUTHORIZED.getReasonPhrase());
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    return problemDetail;
  }

  private void writeProblemDetailResponse(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String errorCode,
      String detail)
      throws IOException {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setType(URI.create(ERROR_URN_PREFIX + errorCode));
    problemDetail.setTitle(status.getReasonPhrase());
    problemDetail.setInstance(URI.create(request.getRequestURI()));

    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setStatus(status.value());
    response.getWriter().write(mapper.writeValueAsString(problemDetail));
  }
}
