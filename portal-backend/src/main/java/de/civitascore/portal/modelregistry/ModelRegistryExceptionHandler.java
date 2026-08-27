/*
 * Placeholder license header — Spotless replaces this with the project header.
 */
package de.civitascore.portal.modelregistry;

import de.civitascore.modelforge.contract.ArtifactInUseException;
import de.civitascore.modelforge.contract.Diagnostic;
import de.civitascore.modelforge.contract.RegistryUnavailableException;
import de.civitascore.modelforge.contract.ValidationFailedException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Gives the model registry's own failures an HTTP meaning. A rejected document is a caller mistake,
 * a blocked delete is a conflict, and an unreachable registry is a temporary fault — without this
 * they would all surface as an unmapped server error carrying no reason.
 *
 * <p>Lives beside the gateway because the registry's exception types may not be referenced from
 * anywhere else in the host; that boundary is asserted by an architecture test. Handling them here
 * rather than at each call site keeps the gateway free of translation scaffolding.
 */
@Slf4j
@RestControllerAdvice
public class ModelRegistryExceptionHandler {

  private static final String ERROR_URN_PREFIX = "urn:civitas:error:";

  /** A document the registry refuses to store, reported with the paths it objected to. */
  @ExceptionHandler(ValidationFailedException.class)
  public ProblemDetail handleValidationFailed(
      ValidationFailedException ex, HttpServletRequest request) {
    String detail = withDiagnostics(ex);
    log.warn("Registry rejected a document: {}", Encode.forJava(detail));
    return problem(HttpStatus.BAD_REQUEST, "INVALID_INPUT", detail, request);
  }

  /** A delete the registry refuses because other artifacts still reference the target. */
  @ExceptionHandler(ArtifactInUseException.class)
  public ProblemDetail handleArtifactInUse(ArtifactInUseException ex, HttpServletRequest request) {
    log.warn("Registry refused a delete: {}", Encode.forJava(ex.getMessage()));
    return problem(HttpStatus.CONFLICT, "RESOURCE_IN_USE", ex.getMessage(), request);
  }

  /** The registry cannot be reached; the caller may retry. */
  @ExceptionHandler(RegistryUnavailableException.class)
  public ProblemDetail handleRegistryUnavailable(
      RegistryUnavailableException ex, HttpServletRequest request) {
    log.error("Model registry unavailable", ex);
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "REGISTRY_UNAVAILABLE",
        "The model registry is currently unavailable",
        request);
  }

  /**
   * Appends the registry's per-path diagnostics to the message, so a caller learns which field of
   * the submitted document was rejected rather than only that validation failed.
   */
  private static String withDiagnostics(ValidationFailedException ex) {
    String paths =
        ex.diagnostics().stream()
            .map(ModelRegistryExceptionHandler::describe)
            .collect(Collectors.joining("; "));
    return paths.isBlank() ? ex.getMessage() : ex.getMessage() + " — " + paths;
  }

  private static String describe(Diagnostic diagnostic) {
    String path = diagnostic.path();
    return path == null || path.isBlank()
        ? diagnostic.message()
        : path + ": " + diagnostic.message();
  }

  private static ProblemDetail problem(
      HttpStatusCode status, String errorCode, String detail, HttpServletRequest request) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setType(URI.create(ERROR_URN_PREFIX + errorCode));
    problemDetail.setTitle(HttpStatus.valueOf(status.value()).getReasonPhrase());
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    return problemDetail;
  }
}
