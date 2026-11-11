package de.civitascore.portal.controller.exception;

import de.civitascore.portal.model.connector.MalformedDataSourceRequestException;
import de.civitascore.portal.problem.FieldError;
import de.civitascore.portal.problem.ProblemDetails;
import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import jakarta.persistence.PersistenceException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.owasp.encoder.Encode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Global exception handler that translates application and persistence exceptions into RFC 9457
 * Problem Detail responses.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 1)
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final URI BLANK_TYPE = URI.create("about:blank");
  private static final Pattern UNIQUE_DETAIL_PATTERN =
      Pattern.compile("Key \\((.+?)\\)=\\((.+?)\\) already exists");

  private static final String UNIQUE_VIOLATION_STATE = "23505";
  private static final String FOREIGN_KEY_VIOLATION_STATE = "23503";
  private static final String NOT_NULL_VIOLATION_STATE = "23502";

  private final ProblemDetails problemDetails;

  public GlobalExceptionHandler(ProblemDetails problemDetails) {
    this.problemDetails = problemDetails;
  }

  /**
   * Answers a typed problem with its catalog type, title, detail and extension members.
   *
   * @param ex the problem
   * @return the Problem Detail with the status of the problem type
   */
  @ExceptionHandler(ProblemException.class)
  public ProblemDetail handleProblem(ProblemException ex) {
    if (ex.type().status().is5xxServerError()) {
      log.error("Answering {}: {}", ex.type(), Encode.forJava(ex.toString()));
    } else {
      log.warn("Answering {}: {}", ex.type(), Encode.forJava(ex.toString()));
    }
    return problemDetails.of(ex);
  }

  /**
   * Handles data integrity violations (unique, foreign key, not-null constraints) and returns the
   * appropriate Problem Detail response.
   *
   * @param ex the data integrity violation exception
   * @return a Problem Detail with a status code matching the constraint type
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
    if (ex.getCause() instanceof ConstraintViolationException cve) {
      String sqlState = cve.getSQLState();

      if (UNIQUE_VIOLATION_STATE.equals(sqlState)) {
        String dbMessage = ex.getMostSpecificCause().getMessage();
        UniqueConstraintViolationException mapped = extractUniqueViolationException(dbMessage);
        log.warn("Unique constraint violation: {}", Encode.forJava(mapped.getMessage()));
        // TR-03187 W-18/W-19: column name and submitted value are needed by clients; extracted
        // via regex from the DB error, not the raw DB message.
        return problemDetails.of(mapped);
      }

      if (FOREIGN_KEY_VIOLATION_STATE.equals(sqlState)) {
        log.warn(
            "Foreign key violation on constraint '{}': {}",
            Encode.forJava(cve.getConstraintName()),
            Encode.forJava(cve.getMessage()));
        return problemDetails.of(ProblemType.FOREIGN_KEY_VIOLATION);
      }

      if (NOT_NULL_VIOLATION_STATE.equals(sqlState)) {
        log.warn(
            "Not-null violation on constraint '{}': {}",
            Encode.forJava(cve.getConstraintName()),
            Encode.forJava(cve.getMessage()));
        return problemDetails.of(ProblemType.NOT_NULL_VIOLATION);
      }
    }

    log.error(
        "Data integrity violation: {}", Encode.forJava(ex.getMostSpecificCause().getMessage()));
    return problemDetails.of(ProblemType.DATA_INTEGRITY_ERROR);
  }

  /**
   * Handles JPA persistence exceptions, distinguishing entity validation failures from general
   * persistence errors.
   *
   * @param ex the persistence exception
   * @return a Problem Detail with HTTP 400 for validation failures or 500 for other errors
   */
  @ExceptionHandler(PersistenceException.class)
  public ProblemDetail handlePersistenceException(PersistenceException ex) {
    if (ex.getCause() instanceof IllegalStateException ise) {
      log.warn("Entity validation failed: {}", Encode.forJava(ise.getMessage()));
      return problemDetails.of(ProblemType.ENTITY_VALIDATION_FAILED);
    }
    log.error("Persistence error: {}", Encode.forJava(ex.getMessage()), ex);
    return problemDetails.of(ProblemType.PERSISTENCE_ERROR);
  }

  /**
   * Handles bean validation failures and returns a 400 Problem Detail response with field-level
   * error details.
   *
   * @param ex the method argument not valid exception containing field errors
   * @param headers the HTTP headers
   * @param status the HTTP status code
   * @param request the current web request
   * @return a Problem Detail response with field error details and HTTP 400 status
   */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return validationFailed(
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fieldError(fe.getField(), fe.getDefaultMessage()))
            .toList());
  }

  /**
   * Answers a failed validation of a handler method parameter, such as an element of a {@code
   * Set<@Valid ...>} body, with the same field errors as a failed {@code @Valid} body.
   */
  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return validationFailed(
        ex.getParameterValidationResults().stream()
            .flatMap(GlobalExceptionHandler::fieldErrors)
            .toList());
  }

  private static Stream<FieldError> fieldErrors(ParameterValidationResult result) {
    if (result instanceof ParameterErrors errors) {
      return errors.getFieldErrors().stream()
          .map(fe -> fieldError(fe.getField(), fe.getDefaultMessage()));
    }
    String parameterName = result.getMethodParameter().getParameterName();
    return result.getResolvableErrors().stream()
        .map(error -> fieldError(parameterName, error.getDefaultMessage()));
  }

  private static FieldError fieldError(@Nullable String field, @Nullable String message) {
    return new FieldError(field != null ? field : "", message != null ? message : "");
  }

  private ResponseEntity<Object> validationFailed(List<FieldError> fieldErrors) {
    ProblemDetail problemDetail = problemDetails.of(ProblemType.VALIDATION_FAILED);
    problemDetail.setProperty("fieldErrors", fieldErrors);
    log.warn("Validation failed: {} field error(s)", fieldErrors.size());
    return ResponseEntity.badRequest().body(problemDetail);
  }

  /**
   * Handles malformed or missing request body and returns a 400 Problem Detail response.
   *
   * @param ex the HTTP message not readable exception
   * @param headers the HTTP headers
   * @param status the HTTP status code
   * @param request the current web request
   * @return a Problem Detail response with HTTP 400 status
   */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    log.warn("Malformed request body: {}", Encode.forJava(ex.getMessage()));
    Optional<MalformedDataSourceRequestException> malformedRequest =
        malformedDataSourceRequestIn(ex);
    if (malformedRequest.isPresent()) {
      return ResponseEntity.badRequest().body(problemDetails.of(malformedRequest.get()));
    }
    return super.handleHttpMessageNotReadable(ex, headers, status, request);
  }

  private static Optional<MalformedDataSourceRequestException> malformedDataSourceRequestIn(
      Throwable ex) {
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      if (cause instanceof MalformedDataSourceRequestException malformedRequest) {
        return Optional.of(malformedRequest);
      }
    }
    return Optional.empty();
  }

  /**
   * Gives an error that Spring MVC raises itself the catalog type and title for its status, and
   * keeps the detail from the message bundle and the headers Spring MVC sets, such as {@code Allow}
   * on a 405.
   */
  @Override
  protected @Nullable ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    ResponseEntity<Object> response =
        super.handleExceptionInternal(ex, body, headers, statusCode, request);
    if (response != null
        && response.getBody() instanceof ProblemDetail problemDetail
        && isBlank(problemDetail.getType())) {
      ProblemType.forFrameworkException(ex, statusCode)
          .ifPresent(type -> problemDetails.stamp(problemDetail, type));
    }
    return response;
  }

  /** Spring MVC leaves the type null, which RFC 9457 reads as {@code about:blank}. */
  private static boolean isBlank(@Nullable URI type) {
    return type == null || BLANK_TYPE.equals(type);
  }

  private UniqueConstraintViolationException extractUniqueViolationException(String dbMessage) {
    Matcher matcher = UNIQUE_DETAIL_PATTERN.matcher(dbMessage);
    if (matcher.find()) {
      return new UniqueConstraintViolationException(matcher.group(1), matcher.group(2));
    }
    return new UniqueConstraintViolationException();
  }
}
