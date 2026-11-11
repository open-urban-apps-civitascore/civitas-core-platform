package de.civitascore.portal.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Records status and problem type of every API answer of the integration tests, so that {@link
 * UndocumentedAnswers} can compare them with the OpenAPI description.
 */
public class AnswerRecorder extends OncePerRequestFilter {

  private static final Set<RecordedAnswer> ANSWERS = ConcurrentHashMap.newKeySet();
  private static final List<String> UNRECORDED_PATHS =
      List.of("/api-docs", "/swagger-ui", "/actuator");

  private final ObjectMapper mapper = new JsonMapper();

  /** The answers recorded so far in this JVM, by every Spring context of the test run. */
  public static Set<RecordedAnswer> answers() {
    return Set.copyOf(ANSWERS);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return UNRECORDED_PATHS.stream().anyMatch(path::startsWith);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
    try {
      filterChain.doFilter(request, wrapper);
    } finally {
      ANSWERS.add(
          new RecordedAnswer(
              request.getMethod(),
              request.getRequestURI(),
              wrapper.getStatus(),
              problemType(wrapper)));
      wrapper.copyBodyToResponse();
    }
  }

  private @Nullable String problemType(ContentCachingResponseWrapper response) {
    if (response.getContentType() == null
        || !MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(
            MediaType.parseMediaType(response.getContentType()))) {
      return null;
    }
    try {
      return mapper.readTree(response.getContentAsByteArray()).path("type").asString(null);
    } catch (JacksonException e) {
      return null;
    }
  }
}
