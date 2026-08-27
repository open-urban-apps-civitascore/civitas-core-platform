/*
 * Placeholder license header — Spotless replaces this with the project header.
 */
package de.civitascore.portal.modelregistry;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.modelforge.contract.ArtifactInUseException;
import de.civitascore.modelforge.contract.Diagnostic;
import de.civitascore.modelforge.contract.DiagnosticSeverity;
import de.civitascore.modelforge.contract.RegistryUnavailableException;
import de.civitascore.modelforge.contract.ValidationFailedException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("Registry failures are given an HTTP meaning instead of surfacing unmapped")
class ModelRegistryExceptionHandlerTest {

  private final ModelRegistryExceptionHandler handler = new ModelRegistryExceptionHandler();

  private static MockHttpServletRequest request() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/v1/mappings");
    return request;
  }

  @Test
  @DisplayName("a rejected document is a client error naming the path that was objected to")
  void rejectedDocumentIsBadRequestWithDiagnostics() {
    ValidationFailedException ex =
        new ValidationFailedException(
            "Artifact does not satisfy its CORE schema",
            List.of(
                new Diagnostic(
                    DiagnosticSeverity.ERROR,
                    "integer found, object expected",
                    "type",
                    "$.fields")));

    ProblemDetail problem = handler.handleValidationFailed(ex, request());

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problem.getType()).hasToString("urn:civitas:error:INVALID_INPUT");
    assertThat(problem.getDetail()).contains("$.fields").contains("integer found, object expected");
    assertThat(problem.getInstance()).hasToString("/v1/mappings");
  }

  @Test
  @DisplayName("a document rejected without diagnostics still reports the reason")
  void rejectedDocumentWithoutDiagnostics() {
    ProblemDetail problem =
        handler.handleValidationFailed(
            new ValidationFailedException("Artifact does not satisfy its CORE schema", List.of()),
            request());

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problem.getDetail()).isEqualTo("Artifact does not satisfy its CORE schema");
  }

  @Test
  @DisplayName("a delete blocked by other artifacts is a conflict listing what blocks it")
  void blockedDeleteIsConflict() {
    ProblemDetail problem =
        handler.handleArtifactInUse(
            new ArtifactInUseException(
                "urn:core:platform:civitas:mapping:common:M:abcdefghij",
                List.of("urn:core:platform:civitas:pipeline:common:P:abcdefghij")),
            request());

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(problem.getType()).hasToString("urn:civitas:error:RESOURCE_IN_USE");
    assertThat(problem.getDetail()).contains("pipeline:common:P");
  }

  @Test
  @DisplayName("an unreachable registry is a temporary fault, not a client error")
  void unreachableRegistryIsServiceUnavailable() {
    ProblemDetail problem =
        handler.handleRegistryUnavailable(
            new RegistryUnavailableException("connection refused"), request());

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    assertThat(problem.getType()).hasToString("urn:civitas:error:REGISTRY_UNAVAILABLE");
  }
}
