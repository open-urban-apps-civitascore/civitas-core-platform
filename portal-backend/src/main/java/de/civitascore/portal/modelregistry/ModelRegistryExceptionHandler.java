/*
 * Placeholder license header — Spotless replaces this with the project header.
 */
package de.civitascore.portal.modelregistry;

import de.civitascore.modelforge.contract.ArtifactInUseException;
import de.civitascore.modelforge.contract.Diagnostic;
import de.civitascore.modelforge.contract.RegistryConflictException;
import de.civitascore.modelforge.contract.RegistryUnavailableException;
import de.civitascore.modelforge.contract.ValidationFailedException;
import de.civitascore.portal.problem.ProblemDetails;
import de.civitascore.portal.problem.ProblemType;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
// Spring also matches handlers against an exception's cause, so without precedence the global
// handler claims a registry exception through the database exception it carries.
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ModelRegistryExceptionHandler {

  private final ProblemDetails problemDetails;

  public ModelRegistryExceptionHandler(ProblemDetails problemDetails) {
    this.problemDetails = problemDetails;
  }

  /** A document the registry refuses to store, reported with the paths it objected to. */
  @ExceptionHandler(ValidationFailedException.class)
  public ProblemDetail handleValidationFailed(
      ValidationFailedException ex, HttpServletRequest request) {
    String detail = withDiagnostics(ex);
    log.warn("Registry rejected a document: {}", Encode.forJava(detail));
    return problemDetails.of(ProblemType.INVALID_INPUT, instance(request), detail);
  }

  /**
   * A delete the registry refuses because other artifacts still reference the target. What holds it
   * goes to the log only.
   */
  @ExceptionHandler(ArtifactInUseException.class)
  public ProblemDetail handleArtifactInUse(ArtifactInUseException ex, HttpServletRequest request) {
    log.warn("Registry refused a delete: {}", Encode.forJava(ex.getMessage()));
    return problemDetails.of(
        ProblemType.RESOURCE_IN_USE,
        instance(request),
        "The artifact is still referenced by another artifact");
  }

  /** A write that collides with data the registry already holds, such as a taken version. */
  @ExceptionHandler(RegistryConflictException.class)
  public ProblemDetail handleRegistryConflict(
      RegistryConflictException ex, HttpServletRequest request) {
    log.warn("Registry refused a conflicting write", ex);
    return ProblemType.UNIQUE_CONSTRAINT_VIOLATION.problemDetail(ex.getMessage(), instance(request));
  }

  /** The registry cannot be reached; the caller may retry. */
  @ExceptionHandler(RegistryUnavailableException.class)
  public ProblemDetail handleRegistryUnavailable(
      RegistryUnavailableException ex, HttpServletRequest request) {
    log.error("Model registry unavailable", ex);
    return problemDetails.of(ProblemType.REGISTRY_UNAVAILABLE, instance(request));
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

  /**
   * A diagnostic as one clause: its path, then its message. The registry derives both from the same
   * validation error and the message already opens with the path, so prefixing unconditionally
   * would print it twice.
   */
  private static String describe(Diagnostic diagnostic) {
    String path = diagnostic.path();
    String message = diagnostic.message();
    if (path == null || path.isBlank() || message == null) {
      return message;
    }
    return message.startsWith(path) ? message : path + ": " + message;
  }

  private static URI instance(HttpServletRequest request) {
    return URI.create(request.getRequestURI());
  }
}
