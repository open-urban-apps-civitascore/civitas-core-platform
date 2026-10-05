package de.civitascore.portal.messaging.saga;

/**
 * A saga result that no redelivery can process. The saga listener error handler sends it to the
 * dead-letter topic without retries.
 */
public class InvalidSagaResultException extends RuntimeException {

  public InvalidSagaResultException(String message, Throwable cause) {
    super(message, cause);
  }
}
