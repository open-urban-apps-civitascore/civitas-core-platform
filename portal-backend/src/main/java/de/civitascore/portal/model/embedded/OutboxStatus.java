package de.civitascore.portal.model.embedded;

/**
 * Lifecycle status of an outbox event.
 *
 * <p>Events transition through these states:
 *
 * <ul>
 *   <li>PENDING → Initial state, waiting for processing
 *   <li>PROCESSED → Successfully published to Kafka
 *   <li>FAILED → Exceeded max retries, moved to Dead Letter Queue
 * </ul>
 */
public enum OutboxStatus {
  /** Event is waiting to be processed and published. */
  PENDING,

  /** Event was successfully published to Kafka. */
  PROCESSED,

  /** Event failed after max retries and was moved to error topic. */
  FAILED;

  /**
   * Checks if this status is terminal (no further processing).
   *
   * @return true if status is PROCESSED or FAILED
   */
  public boolean isTerminal() {
    return this == PROCESSED || this == FAILED;
  }

  /**
   * Checks if event in this status can be retried.
   *
   * @return true if status is PENDING
   */
  public boolean canRetry() {
    return this == PENDING;
  }
}
