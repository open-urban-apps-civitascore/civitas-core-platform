package de.civitascore.portal.controller.exception;

import de.civitascore.portal.model.connector.MalformedDataSourceRequestException;
import de.civitascore.portal.problem.ProblemDetails;
import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
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
   * @param request the current HTTP request
   * @return the Problem Detail with the status of the problem type
   */
  @ExceptionHandler(ProblemException.class)
  public ProblemDetail handleProblem(ProblemException ex, HttpServletRequest request) {
    if (ex.type().status().is5xxServerError()) {
      log.error("Answering {}: {}", ex.type(), Encode.forJava(ex.toString()));
    } else {
      log.warn("Answering {}: {}", ex.type(), Encode.forJava(ex.toString()));
    }
    return problemDetails.of(ex, instance(request));
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
        // TR-03187 W-18/W-19: column name and submitted value are needed by clients; extracted
        // via regex from the DB error, not the raw DB message.
        return toResponse(problemDetails.of(mapped, instance(request)));
      }

      if (FOREIGN_KEY_VIOLATION_STATE.equals(sqlState)) {
        log.warn(
            "Foreign key violation on constraint '{}': {}",
            Encode.forJava(cve.getConstraintName()),
            Encode.forJava(cve.getMessage()));
        return toProblemDetailResponse(ProblemType.FOREIGN_KEY_VIOLATION, request);
      }

      if (NOT_NULL_VIOLATION_STATE.equals(sqlState)) {
        log.warn(
            "Not-null violation on constraint '{}': {}",
            Encode.forJava(cve.getConstraintName()),
            Encode.forJava(cve.getMessage()));
        return toProblemDetailResponse(ProblemType.NOT_NULL_VIOLATION, request);
      }
    }

    log.error(
        "Data integrity violation: {}", Encode.forJava(ex.getMostSpecificCause().getMessage()));
    return toProblemDetailResponse(ProblemType.DATA_INTEGRITY_ERROR, request);
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
      return toProblemDetailResponse(ProblemType.ENTITY_VALIDATION_FAILED, request);
    }
    log.error("Persistence error: {}", Encode.forJava(ex.getMessage()), ex);
    return toProblemDetailResponse(ProblemType.PERSISTENCE_ERROR, request);
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

    ProblemDetail problemDetail = problemDetails.of(ProblemType.VALIDATION_FAILED, null);
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
      return ResponseEntity.badRequest().body(problemDetails.of(malformedRequest.get(), null));
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

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
    log.warn("Access denied: {}", Encode.forJava(ex.getMessage()));
    return problemDetails.of(ProblemType.ACCESS_DENIED, instance(request));
  }

  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleAuthentication(
      AuthenticationException ex, HttpServletRequest request) {
    log.warn("Authentication failed: {}", Encode.forJava(ex.getMessage()));
    return problemDetails.of(ProblemType.UNAUTHORIZED, instance(request));
  }

  /** Spring MVC leaves the type null, which RFC 9457 reads as {@code about:blank}. */
  private static boolean isBlank(@Nullable URI type) {
    return type == null || BLANK_TYPE.equals(type);
  }

  private static URI instance(HttpServletRequest request) {
    return URI.create(request.getRequestURI());
  }

  private ResponseEntity<ProblemDetail> toProblemDetailResponse(
      ProblemType type, HttpServletRequest request) {
    return toResponse(problemDetails.of(type, instance(request)));
  }

  private static ResponseEntity<ProblemDetail> toResponse(ProblemDetail problemDetail) {
    return ResponseEntity.status(problemDetail.getStatus()).body(problemDetail);
  }

  private UniqueConstraintViolationException extractUniqueViolationException(String dbMessage) {
    Matcher matcher = UNIQUE_DETAIL_PATTERN.matcher(dbMessage);
    if (matcher.find()) {
      return new UniqueConstraintViolationException(matcher.group(1), matcher.group(2));
    }
    return new UniqueConstraintViolationException();
  }
}
