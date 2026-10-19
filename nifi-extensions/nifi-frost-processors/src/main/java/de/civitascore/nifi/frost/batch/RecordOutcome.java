/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost.batch;

/**
 * What the batch did to one record.
 *
 * @param recordIndex the record the outcome belongs to
 * @param entity the entity that failed, or null when the record was written
 * @param status the HTTP status of the failing sub-request, or 0 when no sub-request ran
 * @param message the reason FROST gave, or the reason this processor derived
 * @param retryable whether the same record may succeed later without a change: a parent another
 *     Pipeline has not written yet, or a server that could not answer. A record that is wrong
 *     itself is not retryable — sending it again gives the same answer.
 */
public record RecordOutcome(
    int recordIndex, String entity, int status, String message, boolean retryable) {

  /** The longest reason kept. A FROST stack trace must not become a FlowFile attribute. */
  private static final int MESSAGE_LIMIT = 1024;

  public static RecordOutcome written(int recordIndex) {
    return new RecordOutcome(recordIndex, null, 0, null, false);
  }

  /** A record that will fail the same way again: the error sink is its place. */
  public static RecordOutcome failed(int recordIndex, String entity, int status, String message) {
    return new RecordOutcome(recordIndex, entity, status, truncate(message), false);
  }

  /** A record that failed for a reason outside it, and may succeed when it comes again. */
  public static RecordOutcome retry(int recordIndex, String entity, int status, String message) {
    return new RecordOutcome(recordIndex, entity, status, truncate(message), true);
  }

  public boolean successful() {
    return entity == null;
  }

  private static String truncate(String message) {
    if (message == null) {
      return null;
    }
    return message.length() <= MESSAGE_LIMIT ? message : message.substring(0, MESSAGE_LIMIT);
  }
}
