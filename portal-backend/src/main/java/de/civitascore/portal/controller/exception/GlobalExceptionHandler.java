package de.civitascore.portal.controller.exception;

import de.civitascore.portal.util.DataSetNotEditableException;
import de.civitascore.portal.util.DataSourceScopeViolationException;
import de.civitascore.portal.util.ExternalSystemRejectionException;
import de.civitascore.portal.util.ExternalSystemTimeoutException;
import de.civitascore.portal.util.ForbiddenException;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.SagaInFlightException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.owasp.encoder.Encode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Global exception handler that translates application and persistence exceptions into RFC 9457
 * Problem Detail responses.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final String ERROR_URN_PREFIX = "urn:civitas:error:";
  private static final Pattern UNIQUE_DETAIL_PATTERN =
      Pattern.compile("Key \\((.+?)\\)=\\((.+?)\\) already exists");

  private static final String UNIQUE_VIOLATION_STATE = "23505";
  private static final String FOREIGN_KEY_VIOLATION_STATE = "23503";
  private static final String NOT_NULL_VIOLATION_STATE = "23502";

  /**
   * Handles resource-not-found exceptions and returns a 404 Problem Detail response.
   *
   * @param ex the resource not found exception
   * @param request the current HTTP request
   * @return a Problem Detail with HTTP 404 status
   */
  @ExceptionHandler(ResourceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
    log.warn("Resource not found: {}", Encode.forJava(ex.getMessage()));
    // TR-03187 W-18/W-19: message echoes back the UUID the client already submitted in the URL.
    // Resources are UUID-addressed (not enumerable), so returning it is not a disclosure.
    return createProblemDetail(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request);
  }

  /**
   * Handles invalid input exceptions and returns a 400 Problem Detail response.
   *
   * @param ex the invalid input exception
   * @param request the current HTTP request
   * @return a Problem Detail with HTTP 400 status
   */
  @ExceptionHandler(InvalidInputException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ProblemDetail handleInvalidInput(InvalidInputException ex, HttpServletRequest request) {
    log.warn("Invalid input: {}", Encode.forJava(ex.getMessage()));
    // TR-03187 W-18/W-19: message is constructed by our own validation code (not reflected from
    // DB or framework internals). Clients rely on it to render actionable errors.
    return createProblemDetail(HttpStatus.BAD_REQUEST, "INVALID_INPUT", ex.getMessage(), request);
  }

  @ExceptionHandler(DataSetNotEditableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ProblemDetail handleDataSetNotEditable(
      DataSetNotEditableException ex, HttpServletRequest request) {
    log.warn("DataSet not editable: {}", Encode.forJava(ex.getMessage()));
    // TR-03187 W-18/W-19: the detail is a fixed constant with no runtime data interpolated.
    return createProblemDetail(
        HttpStatus.BAD_REQUEST, "DATASET_NOT_EDITABLE", ex.getMessage(), request);
  }

  /**
   * Handles unique constraint violation exceptions and returns a 409 Problem Detail response.
   *
   * @param ex the unique constraint violation exception
   * @param request the current HTTP request
   * @return a Problem Detail with HTTP 409 status
   */
  @ExceptionHandler(UniqueConstraintViolationException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ProblemDetail handleUniqueConstraint(
      UniqueConstraintViolationException ex, HttpServletRequest request) {
    log.warn("Unique constraint violation: {}", Encode.forJava(ex.getMessage()));
    // TR-03187 W-18/W-19: message contains the column name (public API schema) and the value the
    // client just submitted. Required for clients to render which field collided.
    return createProblemDetail(
        HttpStatus.CONFLICT, "UNIQUE_CONSTRAINT_VIOLATION", ex.getMessage(), request);
  }

  /**
   * Handles resource-in-use exceptions and returns a 409 Problem Detail response.
   *
   * @param ex the resource in use exception
   * @param request the current HTTP request
   * @return a Problem Detail with HTTP 409 status
   */
  @ExceptionHandler(ResourceInUseException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ProblemDetail handleResourceInUse(ResourceInUseException ex, HttpServletRequest request) {
    // TR-03187 W-18/W-19: what holds the resource is logged, not returned — the URNs carry the
    // names of artifacts the caller need not be scoped for.
    log.warn(
        "Resource in use: {} blockedBy={}",
        Encode.forJava(ex.getMessage()),
        Encode.forJava(ex.getBlockedBy().toString()));
    return createProblemDetail(HttpStatus.CONFLICT, "RESOURCE_IN_USE", ex.getMessage(), request);
  }

  @ExceptionHandler(SagaInFlightException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ProblemDetail handleSagaInFlight(SagaInFlightException ex, HttpServletRequest request) {
    log.warn(
        "Saga in flight on dataset {}: {}", ex.getDataSetId(), Encode.forJava(ex.getMessage()));
    // TR-03187 W-18/W-19: the saga type is already part of the detail message, so promoting it to
    // its own property discloses nothing further.
    ProblemDetail problemDetail =
        createProblemDetail(HttpStatus.CONFLICT, "SAGA_IN_FLIGHT", ex.getMessage(), request);
    problemDetail.setProperty("pendingSagaType", ex.getPendingSagaType());
    return problemDetail;
  }

  /**
   * Handles forbidden exceptions and returns a 403 Problem Detail response.
   *
   * @param ex the forbidden exception
   * @param request the current HTTP request
   * @return a Problem Detail with HTTP 403 status
   */
  @ExceptionHandler(DataSourceScopeViolationException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public ProblemDetail handleDataSourceScopeViolation(
      DataSourceScopeViolationException ex, HttpServletRequest request) {
    log.warn("DataSource scope violation: offending IDs {}", ex.getOffendingDataSourceIds());
    ProblemDetail pd =
        createProblemDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "DATASOURCE_SCOPE_VIOLATION",
            ex.getMessage(),
            request);
    pd.setProperty("offendingDataSourceIds", ex.getOffendingDataSourceIds());
    return pd;
  }

  @ExceptionHandler(ForbiddenException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ProblemDetail handleForbidden(ForbiddenException ex, HttpServletRequest request) {
    log.warn("Forbidden: {}", Encode.forJava(ex.getMessage()));
    return createProblemDetail(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), request);
  }

  /**
   * Handles data integrity violations (unique, foreign key, not-null constraints) and returns the
   * appropriate Problem Detail response.
   *
   * @param ex the data integrity violation exception
   * @param request the current HTTP request
   * @return a Problem Detail response with a status code matching the constraint type
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    if (ex.getCause() instanceof ConstraintViolationException cve) {
      String sqlState = cve.getSQLState();

      if (UNIQUE_VIOLATION_STATE.equals(sqlState)) {
        String dbMessage = ex.getMostSpecificCause().getMessage();
        UniqueConstraintViolationException mapped = extractUniqueViolationException(dbMessage);
        log.warn("Unique constraint violation: {}", Encode.forJava(mapped.getMessage()));
        // TR-03187 W-18/W-19: same rationale as handleUniqueConstraint — column name + submitted
        // value are needed by clients; extracted via regex from the DB error, not the raw DB
        // message.
        return toProblemDetailResponse(
            HttpStatus.CONFLICT, "UNIQUE_CONSTRAINT_VIOLATION", mapped.getMessage(), request);
      }

      if (FOREIGN_KEY_VIOLATION_STATE.equals(sqlState)) {
        log.warn(
            "Foreign key violation on constraint '{}': {}",
            Encode.forJava(cve.getConstraintName()),
            Encode.forJava(cve.getMessage()));
        return toProblemDetailResponse(
            HttpStatus.CONFLICT,
            "FOREIGN_KEY_VIOLATION",
            "Referenced entity does not exist or is still in use",
            request);
      }

      if (NOT_NULL_VIOLATION_STATE.equals(sqlState)) {
        log.warn(
            "Not-null violation on constraint '{}': {}",
            Encode.forJava(cve.getConstraintName()),
            Encode.forJava(cve.getMessage()));
        return toProblemDetailResponse(
            HttpStatus.BAD_REQUEST, "NOT_NULL_VIOLATION", "A required field is missing", request);
      }
    }

    log.error(
        "Data integrity violation: {}", Encode.forJava(ex.getMostSpecificCause().getMessage()));
    return toProblemDetailResponse(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "DATA_INTEGRITY_ERROR",
        "A data integrity error occurred",
        request);
  }

  /**
   * Handles JPA persistence exceptions, distinguishing entity validation failures from general
   * persistence errors.
   *
   * @param ex the persistence exception
   * @param request the current HTTP request
   * @return a Problem Detail response with HTTP 400 for validation failures or 500 for other errors
   */
  @ExceptionHandler(PersistenceException.class)
  public ResponseEntity<ProblemDetail> handlePersistenceException(
      PersistenceException ex, HttpServletRequest request) {
    if (ex.getCause() instanceof IllegalStateException ise) {
      log.warn("Entity validation failed: {}", Encode.forJava(ise.getMessage()));
      return toProblemDetailResponse(
          HttpStatus.BAD_REQUEST, "ENTITY_VALIDATION_FAILED", "Entity validation failed", request);
    }
    log.error("Persistence error: {}", Encode.forJava(ex.getMessage()), ex);
    return toProblemDetailResponse(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "PERSISTENCE_ERROR",
        "A persistence error occurred",
        request);
  }

  /**
   * Handles external system rejection exceptions and returns a 502 Problem Detail response.
   *
   * @param ex the external system rejection exception
   * @param request the current HTTP request
   * @return a Problem Detail with HTTP 502 status
   */
  @ExceptionHandler(ExternalSystemRejectionException.class)
  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  public ProblemDetail handleExternalSystemRejection(
      ExternalSystemRejectionException ex, HttpServletRequest request) {
    log.error("External system rejected request: {}", Encode.forJava(ex.getMessage()));
    return createProblemDetail(
        HttpStatus.BAD_GATEWAY,
        "EXTERNAL_SYSTEM_ERROR",
        "The request was rejected by an external system",
        request);
  }

  /**
   * Handles external system timeout exceptions and returns a 504 Problem Detail response.
   *
   * @param ex the external system timeout exception
   * @param request the current HTTP request
   * @return a Problem Detail with HTTP 504 status
   */
  @ExceptionHandler(ExternalSystemTimeoutException.class)
  @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
  public ProblemDetail handleExternalSystemTimeout(
      ExternalSystemTimeoutException ex, HttpServletRequest request) {
    log.error("External system timed out: {}", Encode.forJava(ex.getMessage()));
    return createProblemDetail(
        HttpStatus.GATEWAY_TIMEOUT,
        "EXTERNAL_SYSTEM_TIMEOUT",
        "An external system did not respond in time",
        request);
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
    List<Map<String, String>> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                fe ->
                    Map.of(
                        "field",
                        fe.getField(),
                        "message",
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : ""))
            .toList();

    ProblemDetail problemDetail =
        createProblemDetail(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Input validation failed");
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
    ProblemDetail problemDetail =
        createProblemDetail(
            HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or malformed");
    return ResponseEntity.badRequest().body(problemDetail);
  }

  private ProblemDetail createProblemDetail(
      HttpStatusCode status, String errorCode, String detail) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setType(URI.create(ERROR_URN_PREFIX + errorCode));
    problemDetail.setTitle(HttpStatus.valueOf(status.value()).getReasonPhrase());
    return problemDetail;
  }

  private ProblemDetail createProblemDetail(
      HttpStatusCode status, String errorCode, String detail, HttpServletRequest request) {
    ProblemDetail problemDetail = createProblemDetail(status, errorCode, detail);
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    return problemDetail;
  }

  private ResponseEntity<ProblemDetail> toProblemDetailResponse(
      HttpStatusCode status, String errorCode, String detail, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(createProblemDetail(status, errorCode, detail, request));
  }

  private UniqueConstraintViolationException extractUniqueViolationException(String dbMessage) {
    Matcher matcher = UNIQUE_DETAIL_PATTERN.matcher(dbMessage);
    if (matcher.find()) {
      return new UniqueConstraintViolationException(matcher.group(1), matcher.group(2));
    }
    return new UniqueConstraintViolationException();
  }
}
