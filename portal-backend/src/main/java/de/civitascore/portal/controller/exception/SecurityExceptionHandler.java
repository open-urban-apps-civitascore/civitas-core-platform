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

@ControllerAdvice
@Slf4j
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private static final String ERROR_URN_PREFIX = "urn:civitas:error:";
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    log.warn("Authentication failed: {}", ex.getMessage());
    writeProblemDetailResponse(
        response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    log.warn("Access denied: {}", ex.getMessage());
    writeProblemDetailResponse(
        response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Insufficient privileges");
  }

  @ExceptionHandler(JwtException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ProblemDetail handleJwtException(JwtException ex) {
    log.warn("JWT validation failed: {}", ex.getMessage());
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "JWT validation failed");
    problemDetail.setType(URI.create(ERROR_URN_PREFIX + "INVALID_TOKEN"));
    problemDetail.setTitle(HttpStatus.UNAUTHORIZED.getReasonPhrase());
    return problemDetail;
  }

  private void writeProblemDetailResponse(
      HttpServletResponse response, HttpStatus status, String errorCode, String detail)
      throws IOException {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setType(URI.create(ERROR_URN_PREFIX + errorCode));
    problemDetail.setTitle(status.getReasonPhrase());

    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setStatus(status.value());
    response.getWriter().write(mapper.writeValueAsString(problemDetail));
  }
}
