/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.util;

/**
 * Utility methods for masking personally identifiable information (PII) in log messages and error
 * outputs. Prevents accidental exposure of emails, UUIDs, and file paths.
 */
public final class PiiMaskingUtil {

  private PiiMaskingUtil() {}

  /**
   * Masks email addresses for log output.
   *
   * @param email the email to mask
   * @return masked email (e.g., "use***@***.***"), or "***" if null or invalid
   */
  public static String maskEmail(String email) {
    if (email == null || !email.contains("@")) {
      return "***";
    }
    int at = email.indexOf('@');
    return email.substring(0, Math.min(3, at)) + "***@***.***";
  }

  /**
   * Masks UUIDs/IDs for log output, showing only the first 8 characters.
   *
   * @param id the ID to mask
   * @return masked ID (e.g., "06702f15***"), or "***" if null or too short
   */
  public static String maskId(String id) {
    if (id == null || id.length() <= 8) {
      return "***";
    }
    return id.substring(0, 8) + "***";
  }

  /**
   * Masks potential PII patterns in error messages: email addresses, UUIDs, and file paths.
   *
   * @param message the message to mask
   * @return masked message, or "Unknown error" if null
   */
  public static String maskPII(String message) {
    if (message == null) {
      return "Unknown error";
    }
    // Mask email patterns
    String masked =
        message.replaceAll("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "***@***.***");
    // Mask UUIDs
    masked =
        masked.replaceAll(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
            "***-***-***");
    // Mask potential file paths
    masked = masked.replaceAll("/[a-zA-Z0-9/_.-]+", "/***");
    return masked;
  }
}
