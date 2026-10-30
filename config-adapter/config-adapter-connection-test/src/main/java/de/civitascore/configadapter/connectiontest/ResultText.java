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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Makes the texts of a result safe to return: every secret of the connector is masked, a message is
 * cut to {@value #MAX_MESSAGE_CHARS} characters and a preview to {@value #MAX_PREVIEW_BYTES} bytes.
 */
final class ResultText {

  static final int MAX_MESSAGE_CHARS = 500;
  static final int MAX_PREVIEW_BYTES = 8 * 1024;

  /** Read past the preview limit, longer than any secret, so that a cut cannot split one. */
  static final int MASK_MARGIN = 1024;

  static final String TRUNCATED = "\n[truncated]";

  static final String MASK = "********";
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

  /**
   * A preview, masked and cut to {@value #MAX_PREVIEW_BYTES} UTF-8 bytes including the marker that
   * says so; never in the middle of a character.
   *
   * @param truncated whether the source returned more than {@code text}
   */
  Preview preview(String text, boolean truncated) {
    if (text == null) {
      return new Preview(null, truncated);
    }
    String masked = mask(text);
    byte[] bytes = masked.getBytes(StandardCharsets.UTF_8);
    if (bytes.length <= MAX_PREVIEW_BYTES && !truncated) {
      return new Preview(masked, false);
    }
    int budget = MAX_PREVIEW_BYTES - TRUNCATED.getBytes(StandardCharsets.UTF_8).length;
    StringBuilder kept = new StringBuilder();
    int used = 0;
    int index = 0;
    while (index < masked.length()) {
      int codePoint = masked.codePointAt(index);
      int size = utf8Length(codePoint);
      if (used + size > budget) {
        break;
      }
      kept.appendCodePoint(codePoint);
      used += size;
      index += Character.charCount(codePoint);
    }
    return new Preview(kept + TRUNCATED, true);
  }

  /**
   * @param text the preview, ending in {@value #TRUNCATED} when cut; null if there is none
   * @param truncated whether the source returned more than {@code text}
   */
  record Preview(String text, boolean truncated) {}

  private String mask(String text) {
    String masked = text;
    for (String secret : secrets) {
      masked = masked.replace(secret, MASK);
    }
    return masked;
  }

  private static int utf8Length(int codePoint) {
    if (codePoint < 0x80) {
      return 1;
    }
    if (codePoint < 0x800) {
      return 2;
    }
    return codePoint < 0x10000 ? 3 : 4;
  }
}
