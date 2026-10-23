/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connectiontest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Makes the message of a result safe to return: every secret of the connector is masked, and the
 * message is cut to {@value #MAX_MESSAGE_CHARS} characters.
 */
final class ResultText {

  static final int MAX_MESSAGE_CHARS = 500;

  private static final String MASK = "********";
  private static final String ELLIPSIS = "…";

  /** Longest first, so that a secret containing another one is masked as a whole. */
  private final List<String> secrets;

  ResultText(List<String> secrets) {
    this.secrets =
        secrets.stream()
            .filter(Objects::nonNull)
            .filter(secret -> !secret.isEmpty())
            .distinct()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();
  }

  /**
   * The secrets of a connector document: every value that was encrypted in {@code original} and is
   * plain text in {@code decrypted}, at any depth.
   */
  static List<String> secretsOf(Map<String, Object> original, Map<String, Object> decrypted) {
    List<String> secrets = new ArrayList<>();
    collectSecrets(original, decrypted, secrets);
    return secrets;
  }

  private static void collectSecrets(
      Map<?, ?> original, Map<?, ?> decrypted, List<String> secrets) {
    for (Map.Entry<?, ?> entry : original.entrySet()) {
      Object plain = decrypted.get(entry.getKey());
      if (entry.getValue() instanceof Map<?, ?> nested && plain instanceof Map<?, ?> plainNested) {
        collectSecrets(nested, plainNested, secrets);
      } else if (plain instanceof String text && !text.equals(entry.getValue())) {
        secrets.add(text);
      }
    }
  }

  /** A message, masked and cut to {@value #MAX_MESSAGE_CHARS} characters. */
  String message(String text) {
    if (text == null) {
      return null;
    }
    String masked = mask(text);
    if (masked.length() <= MAX_MESSAGE_CHARS) {
      return masked;
    }
    int end = MAX_MESSAGE_CHARS - ELLIPSIS.length();
    if (Character.isHighSurrogate(masked.charAt(end - 1))) {
      end--;
    }
    return masked.substring(0, end) + ELLIPSIS;
  }

  private String mask(String text) {
    String masked = text;
    for (String secret : secrets) {
      masked = masked.replace(secret, MASK);
    }
    return masked;
  }
}
