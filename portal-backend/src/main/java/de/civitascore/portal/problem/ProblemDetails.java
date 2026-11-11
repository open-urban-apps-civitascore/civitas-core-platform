package de.civitascore.portal.problem;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds every problem that portal-backend answers. Title and detail of a catalog type come from
 * the message bundle keys {@code problem.<CODE>.title} and {@code problem.<CODE>.detail}.
 *
 * <p>A built problem has no instance: Spring MVC sets it to the request URI when it writes a
 * ProblemDetail that a handler returns.
 */
@Component
public class ProblemDetails {

  private final MessageSource messageSource;

  public ProblemDetails(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  /**
   * @param detailArguments the arguments of the type's detail pattern; a free-form type takes the
   *     detail text as its only argument, which must not reveal internal state
   */
  public ProblemDetail of(ProblemType type, Object... detailArguments) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(type.status(), message(type, "detail", detailArguments));
    return stamp(problemDetail, type);
  }

  public ProblemDetail of(ProblemException ex) {
    ProblemDetail problemDetail = of(ex.type(), ex.detailArguments());
    ex.properties().forEach(problemDetail::setProperty);
    return problemDetail;
  }

  /** Sets type and title of the catalog type, and keeps status and detail. */
  public ProblemDetail stamp(ProblemDetail problemDetail, ProblemType type) {
    problemDetail.setType(type.type());
    problemDetail.setTitle(message(type, "title"));
    return problemDetail;
  }

  private String message(ProblemType type, String part, Object... arguments) {
    return messageSource.getMessage(
        "problem." + type.name() + "." + part, arguments, LocaleContextHolder.getLocale());
  }
}
