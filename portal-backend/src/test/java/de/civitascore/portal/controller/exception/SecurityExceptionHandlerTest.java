package de.civitascore.portal.controller.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SecurityExceptionHandlerTest {

  private static final String TEST_URI = "/v1/datasets";

  @InjectMocks private SecurityExceptionHandler SUT;

  private HttpServletRequest request;
  private HttpServletResponse response;
  private final ObjectMapper objectMapper = new JsonMapper();

  @BeforeEach
  void setUp() {
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    when(request.getRequestURI()).thenReturn(TEST_URI);
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
    verify(response).setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());

    printWriter.flush();
    Map<String, Object> responseMap =
        objectMapper.readValue(stringWriter.toString(), new TypeReference<>() {});

    assertThat(responseMap)
        .containsEntry("type", "urn:civitas:error:UNAUTHORIZED")
        .containsEntry("detail", "Authentication required")
        .containsEntry("title", "Unauthorized")
        .containsEntry("status", HttpStatus.UNAUTHORIZED.value())
        .containsEntry("instance", TEST_URI);
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
    verify(response).setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    verify(response).setStatus(HttpStatus.FORBIDDEN.value());

    printWriter.flush();
    Map<String, Object> responseMap =
        objectMapper.readValue(stringWriter.toString(), new TypeReference<>() {});

    assertThat(responseMap)
        .containsEntry("type", "urn:civitas:error:ACCESS_DENIED")
        .containsEntry("detail", "Insufficient privileges")
        .containsEntry("title", "Forbidden")
        .containsEntry("status", HttpStatus.FORBIDDEN.value())
        .containsEntry("instance", TEST_URI);
  }

  @Test
  @DisplayName("JWT exception should return 401 with invalid token error")
  void testHandleJwtExceptionReturns401() {
    // given
    JwtException jwtException = new JwtException("Token expired");

    // when
    ProblemDetail problemDetail = SUT.handleJwtException(jwtException, request);

    // then
    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(problemDetail.getType()).hasToString("urn:civitas:error:INVALID_TOKEN");
    assertThat(problemDetail.getDetail()).isEqualTo("JWT validation failed");
    assertThat(problemDetail.getTitle()).isEqualTo("Unauthorized");
  }

  @Test
  @DisplayName("JWT exception with null message should return valid response")
  void testHandleJwtExceptionWithNullMessage() {
    // given
    JwtException jwtException = new JwtException(null);

    // when
    ProblemDetail problemDetail = SUT.handleJwtException(jwtException, request);

    // then
    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(problemDetail.getType()).hasToString("urn:civitas:error:INVALID_TOKEN");
    assertThat(problemDetail.getDetail()).isEqualTo("JWT validation failed");
    assertThat(problemDetail.getInstance()).isEqualTo(URI.create(TEST_URI));
  }
}
