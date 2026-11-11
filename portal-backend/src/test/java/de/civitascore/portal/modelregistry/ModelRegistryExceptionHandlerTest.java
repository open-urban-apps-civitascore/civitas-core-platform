/*
 * Placeholder license header — Spotless replaces this with the project header.
 */
package de.civitascore.portal.modelregistry;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.modelforge.contract.ArtifactInUseException;
import de.civitascore.modelforge.contract.Diagnostic;
import de.civitascore.modelforge.contract.DiagnosticSeverity;
import de.civitascore.modelforge.contract.RegistryConflictException;
import de.civitascore.modelforge.contract.RegistryUnavailableException;
import de.civitascore.modelforge.contract.ValidationFailedException;
import de.civitascore.portal.problem.TestProblemDetails;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

@DisplayName("Registry failures are given an HTTP meaning instead of surfacing unmapped")
class ModelRegistryExceptionHandlerTest {

  private final ModelRegistryExceptionHandler handler =
      new ModelRegistryExceptionHandler(TestProblemDetails.create());

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

    ProblemDetail problem = handler.handleValidationFailed(ex);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problem.getType()).hasToString("urn:civitas:error:INVALID_INPUT");
    assertThat(problem.getDetail()).contains("$.fields").contains("integer found, object expected");
  }

  @Test
  @DisplayName("a document rejected without diagnostics still reports the reason")
  void rejectedDocumentWithoutDiagnostics() {
    ProblemDetail problem =
        handler.handleValidationFailed(
            new ValidationFailedException("Artifact does not satisfy its CORE schema", List.of()));

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problem.getDetail()).isEqualTo("Artifact does not satisfy its CORE schema");
  }

  // Without this test, a regression would again reveal what holds the resource (W-18).
  @Test
  void handleArtifactInUse_whenDependentsBlockTheDelete_answersWithoutNamingThem() {
    ProblemDetail problem =
        handler.handleArtifactInUse(
            new ArtifactInUseException(
                "urn:core:platform:civitas:mapping:common:3bb4d91a-a4c3-8696-bbb3-ee3c4ef17c6d",
                List.of(
                    "urn:core:platform:civitas:pipeline:common:c71d3077-985f-85f7-b699-88738d3e0dd6")));

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(problem.getType()).hasToString("urn:civitas:error:RESOURCE_IN_USE");
    assertThat(problem.getDetail()).doesNotContain("urn:core");
  }

  @Test
  @DisplayName("a write that collides with a stored version is a conflict, not a server error")
  void conflictingWriteIsConflict() {
    ProblemDetail problem =
        handler.handleRegistryConflict(
            new RegistryConflictException(
                "The request conflicts with existing registry data",
                new IllegalStateException("duplicate key")));

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(problem.getType()).hasToString("urn:civitas:error:UNIQUE_CONSTRAINT_VIOLATION");
    assertThat(problem.getDetail()).doesNotContain("duplicate key");
  }

  @Test
  @DisplayName("an unreachable registry is a temporary fault, not a client error")
  void unreachableRegistryIsServiceUnavailable() {
    ProblemDetail problem =
        handler.handleRegistryUnavailable(new RegistryUnavailableException("connection refused"));

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    assertThat(problem.getType()).hasToString("urn:civitas:error:REGISTRY_UNAVAILABLE");
  }

  @Test
  @DisplayName("a diagnostic whose message already opens with its path is not prefixed twice")
  void doesNotRepeatThePathTheMessageAlreadyCarries() {
    ValidationFailedException failure =
        new ValidationFailedException(
            "Schema import validation failed",
            List.of(
                new Diagnostic(
                    DiagnosticSeverity.ERROR,
                    "$.fields: array found, object expected",
                    "validation",
                    "$.fields")));

    ProblemDetail problem = handler.handleValidationFailed(failure);

    assertThat(problem.getDetail()).contains("$.fields: array found, object expected");
    assertThat(problem.getDetail()).doesNotContain("$.fields: $.fields");
  }
}
