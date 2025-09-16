package de.civitascore.portal.controller.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Slf4j
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ObjectMapper mapper = new ObjectMapper();

  // Handle authentication failures (401)
  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    log.warn("Authentication failed: {}", ex.getMessage());
    writeErrorResponse(
        response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
  }

  // Handle access denied (403)
  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    log.warn("Access denied: {}", ex.getMessage());
    writeErrorResponse(
        response, HttpServletResponse.SC_FORBIDDEN, "ACCESS_DENIED", "Insufficient privileges");
  }

  // Handle JWT exceptions in controllers
  @ExceptionHandler(JwtException.class)
  public ResponseEntity<Map<String, Object>> handleJwtException(JwtException ex) {
    log.warn("JWT validation failed: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(createErrorMap("INVALID_TOKEN", "JWT validation failed"));
  }

  // Helper methods
  private void writeErrorResponse(
      HttpServletResponse response, int status, String error, String message) throws IOException {
    response.setContentType("application/json");
    response.setStatus(status);
    response.getWriter().write(mapper.writeValueAsString(createErrorMap(error, message)));
  }

  private Map<String, Object> createErrorMap(String error, String message) {
    return Map.of(
        "error", error,
        "message", message,
        "timestamp", LocalDateTime.now().toString());
  }
}
