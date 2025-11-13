package de.civitascore.portal.controller.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtException;

@ExtendWith(MockitoExtension.class)
class SecurityExceptionHandlerTest {

  @InjectMocks private SecurityExceptionHandler SUT;

  private HttpServletRequest request;
  private HttpServletResponse response;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
  }

  @Test
  @DisplayName("Authentication exception should return 401 with correct error message")
  void testCommenceReturns401WithCorrectMessage() throws IOException {
    // given
    AuthenticationException authException = mock(AuthenticationException.class);
    when(authException.getMessage()).thenReturn("Invalid credentials");
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(printWriter);

    // when
    SUT.commence(request, response, authException);

    // then
    verify(response).setContentType("application/json");
    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);

    printWriter.flush();
    Map<String, Object> responseMap =
        objectMapper.readValue(stringWriter.toString(), new TypeReference<>() {});

    assertThat(responseMap).containsEntry("error", "UNAUTHORIZED");
    assertThat(responseMap).containsEntry("message", "Authentication required");
    assertThat(responseMap).containsKey("timestamp");
  }

  @Test
  @DisplayName("Access denied exception should return 403 with correct error message")
  void testHandleReturns403WithCorrectMessage() throws IOException {
    // given
    AccessDeniedException accessDeniedException = new AccessDeniedException("Access is denied");
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(printWriter);

    // when
    SUT.handle(request, response, accessDeniedException);

    // then
    verify(response).setContentType("application/json");
    verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);

    printWriter.flush();
    Map<String, Object> responseMap =
        objectMapper.readValue(stringWriter.toString(), new TypeReference<>() {});

    assertThat(responseMap).containsEntry("error", "ACCESS_DENIED");
    assertThat(responseMap).containsEntry("message", "Insufficient privileges");
    assertThat(responseMap).containsKey("timestamp");
  }

  @Test
  @DisplayName("JWT exception should return 401 with invalid token error")
  void testHandleJwtExceptionReturns401() {
    // given
    JwtException jwtException = new JwtException("Token expired");

    // when
    ResponseEntity<Map<String, Object>> response = SUT.handleJwtException(jwtException);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody()).containsEntry("error", "INVALID_TOKEN");
    assertThat(response.getBody()).containsEntry("message", "JWT validation failed");
    assertThat(response.getBody()).containsKey("timestamp");
  }

  @Test
  @DisplayName("JWT exception with null message should return valid response")
  void testHandleJwtExceptionWithNullMessage() {
    // given
    JwtException jwtException = new JwtException(null);

    // when
    ResponseEntity<Map<String, Object>> response = SUT.handleJwtException(jwtException);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody()).containsEntry("error", "INVALID_TOKEN");
    assertThat(response.getBody()).containsEntry("message", "JWT validation failed");
  }
}
