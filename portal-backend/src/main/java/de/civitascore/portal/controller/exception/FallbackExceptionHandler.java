package de.civitascore.portal.controller.exception;

import de.civitascore.portal.problem.ProblemDetails;
import de.civitascore.portal.problem.ProblemType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers every exception that no other advice handles with a 500 {@code INTERNAL_ERROR} Problem
 * Detail.
 *
 * <p>Ordered last and kept out of {@link GlobalExceptionHandler} because Spring matches an
 * exception's cause only when no handler in the same advice matches its type; every other advice
 * therefore needs a higher precedence.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class FallbackExceptionHandler {

  private final ProblemDetails problemDetails;

  public FallbackExceptionHandler(ProblemDetails problemDetails) {
    this.problemDetails = problemDetails;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unexpected error on {}", Encode.forJava(request.getRequestURI()), ex);
    return problemDetails.of(ProblemType.INTERNAL_ERROR);
  }
}
