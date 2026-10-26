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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResultTextTest {

  private static final String EMOJI = "😀";

  private static int utf8Length(String text) {
    return text.getBytes(StandardCharsets.UTF_8).length;
  }

  @Test
  void messageAndPreviewMaskEverySecret() {
    ResultText text = new ResultText(List.of("hunter2", "token"));

    assertEquals("a ******** b ********", text.message("a hunter2 b token"));
    assertEquals("a ******** b ********", text.preview("a hunter2 b token", false).text());
  }

  @Test
  void longestSecretIsMaskedFirst() {
    ResultText text = new ResultText(List.of("abc", "abcdef"));

    assertEquals("x ******** y", text.message("x abcdef y"));
  }

  @Test
  void emptyAndNullSecretsAreIgnored() {
    ResultText text = new ResultText(java.util.Arrays.asList("", null));

    assertEquals("unchanged", text.message("unchanged"));
  }

  @Test
  void secretsOfFindsNestedSecretsOnlyWhereValueWasDecrypted() {
    Map<String, Object> original =
        Map.of(
            "user", "alice",
            "password", "ENC(abc)",
            "authentication", Map.of("password", "ENC(def)", "type", "basic"));
    Map<String, Object> decrypted =
        Map.of(
            "user", "alice",
            "password", "top-secret",
            "authentication", Map.of("password", "nested-secret", "type", "basic"));

    List<String> secrets = ResultText.secretsOf(original, decrypted);

    assertEquals(2, secrets.size());
    assertTrue(secrets.containsAll(List.of("top-secret", "nested-secret")));
  }

  @Test
  void secretsOfIsEmptyWhenNothingWasEncrypted() {
    Map<String, Object> document = Map.of("user", "alice", "n", Map.of("k", "v"));

    assertTrue(ResultText.secretsOf(document, document).isEmpty());
  }

  @Test
  void shortMessageIsKept() {
    assertEquals("ok", new ResultText(List.of()).message("ok"));
  }

  @Test
  void messageOfExactlyMaximumLengthIsKept() {
    String exact = "a".repeat(500);

    assertEquals(exact, new ResultText(List.of()).message(exact));
  }

  @Test
  void longMessageIsCutToMaximumWithEllipsis() {
    String result = new ResultText(List.of()).message("a".repeat(2000));

    assertEquals(500, result.length());
    assertTrue(result.endsWith("…"));
  }

  @Test
  void messageCutNeverSplitsSurrogatePair() {
    // The emoji straddles the cut position: chars 498 and 499 are its two halves.
    String input = "a".repeat(498) + EMOJI + "tail";

    String result = new ResultText(List.of()).message(input);

    assertEquals("a".repeat(498) + "…", result);
    assertFalse(Character.isHighSurrogate(result.charAt(result.length() - 2)));
  }

  @Test
  void nullPassesThrough() {
    ResultText text = new ResultText(List.of("s"));

    assertNull(text.message(null));
    assertNull(text.preview(null, false).text());
  }

  @Test
  void previewWithinLimitIsKeptAsIs() {
    String preview = "a".repeat(8192);

    assertEquals(preview, new ResultText(List.of()).preview(preview, false).text());
    assertFalse(new ResultText(List.of()).preview(preview, false).truncated());
  }

  @Test
  void truncatedFlagAddsMarkerEvenForShortPreview() {
    ResultText.Preview cut = new ResultText(List.of()).preview("abc", true);

    assertEquals("abc\n[truncated]", cut.text());
    assertTrue(cut.truncated());
  }

  @Test
  void longPreviewIsCutIncludingMarker() {
    String result = new ResultText(List.of()).preview("a".repeat(20_000), false).text();

    assertEquals("a".repeat(8180) + "\n[truncated]", result);
    assertEquals(8192, utf8Length(result));
  }

  @Test
  void previewCutNeverSplitsTwoByteCharacter() {
    String result = new ResultText(List.of()).preview("ä".repeat(10_000), false).text();

    assertEquals("ä".repeat(4090) + "\n[truncated]", result);
  }

  @Test
  void previewCutNeverSplitsFourByteCharacter() {
    String result = new ResultText(List.of()).preview(EMOJI.repeat(5_000), false).text();

    assertEquals(EMOJI.repeat(2045) + "\n[truncated]", result);
  }

  @Test
  void previewWithMultiByteCharacterAtBoundaryStaysWithinLimit() {
    // 8180 bytes fit beside the 12-byte marker: one byte short, then a 2-byte character.
    String input = "a".repeat(8179) + "ä" + "tail";

    String result = new ResultText(List.of()).preview(input, true).text();

    assertEquals("a".repeat(8179) + "\n[truncated]", result);
  }
}
