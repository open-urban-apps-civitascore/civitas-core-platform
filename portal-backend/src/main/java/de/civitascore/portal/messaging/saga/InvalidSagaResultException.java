package de.civitascore.portal.messaging.saga;

/** A saga result that no redelivery can process. */
public class InvalidSagaResultException extends RuntimeException {

  public InvalidSagaResultException(String message, Throwable cause) {
    super(message, cause);
  }
}
