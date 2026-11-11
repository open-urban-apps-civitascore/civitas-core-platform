package de.civitascore.portal.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.util.AnswerRecorder;
import de.civitascore.portal.util.RecordedAnswer;
import de.civitascore.portal.util.UndocumentedAnswers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * Fails on the answers of the integration tests that the OpenAPI description does not document. It
 * runs after every other integration test class of the JVM, see {@code junit-platform.properties}.
 */
@Order(Integer.MAX_VALUE)
class UndocumentedAnswersIntegrationTest extends BaseKeycloakIntegrationTest {

  private static final Path REPORT = Path.of("target", "undocumented-answers.txt");

  @Test
  void everyRecordedAnswer_isDocumentedInTheSpec() throws IOException {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    ResponseEntity<JsonNode> spec =
        restTemplate.exchange(
            "/api-docs", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    assertThat(spec.getStatusCode()).isEqualTo(HttpStatus.OK);
    Set<RecordedAnswer> answers = AnswerRecorder.answers();
    assertThat(answers)
        .as("recorded answers, which only integration test classes that run before this one give")
        .isNotEmpty();

    List<String> undocumented = UndocumentedAnswers.report(spec.getBody(), answers);

    Files.createDirectories(REPORT.getParent());
    Files.write(REPORT, undocumented);
    log.info(
        "{} undocumented answers of {} recorded answers, see {}",
        undocumented.size(),
        answers.size(),
        REPORT.toAbsolutePath());
    assertThat(undocumented).as("undocumented answers, see %s", REPORT.toAbsolutePath()).isEmpty();
  }
}
