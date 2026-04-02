/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.frost;

/**
 * Shared utility methods for the FROST adapter module, used by both {@link FrostAdapter} and {@link
 * FrostSagaHandler}.
 */
final class FrostUtils {

  private FrostUtils() {}

  /**
   * Extracts the entity ID from a FROST Location header. The header has the format {@code
   * http://host/v1.1/EntityType(id)}, and this method returns the {@code id} portion.
   *
   * @param locationHeader the Location header value
   * @return the extracted ID
   * @throws IllegalStateException if the header is blank or does not contain a parseable ID
   */
  static String extractIdFromLocation(String locationHeader) {
    if (locationHeader == null || locationHeader.isBlank()) {
      throw new IllegalStateException(
          "FROST response missing Location header — cannot extract entity ID");
    }
    int start = locationHeader.lastIndexOf('(');
    int end = locationHeader.lastIndexOf(')');
    if (start >= 0 && end > start) {
      return locationHeader.substring(start + 1, end);
    }
    throw new IllegalStateException(
        "FROST Location header has unexpected format: " + locationHeader);
  }
}
