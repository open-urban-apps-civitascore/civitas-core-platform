package de.civitascore.portal.problem;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * An error that portal-backend answers with the problem of one catalog type. The title and detail
 * come from the message bundle: {@code problem.<CODE>.detail} formatted with {@link
 * #detailArguments()}. The exception message goes to the log.
 */
public abstract class ProblemException extends RuntimeException {

  private final ProblemType type;
  private final Object[] detailArguments;

  /**
   * For a type whose detail is free-form ({@code {0}}): the message is the detail of the answer as
   * well, so it must not reveal internal state.
   */
  protected ProblemException(ProblemType type, String message) {
    super(message);
    this.type = type;
    this.detailArguments = new Object[] {message};
  }

  /** For a type whose detail is fixed: the message goes to the log only. */
  protected ProblemException(ProblemType type, String message, @Nullable Throwable cause) {
    super(message, cause);
    this.type = type;
    this.detailArguments = new Object[0];
  }

  public ProblemType type() {
    return type;
  }

  public Object[] detailArguments() {
    return detailArguments.clone();
  }

  /**
   * Extension members of the answer, next to type, title, status, detail and instance. Every value
   * is part of the answer, so it must name only what the caller may read (TR-03187 W-18/W-19).
   */
  public Map<String, Object> properties() {
    return Map.of();
  }
}
