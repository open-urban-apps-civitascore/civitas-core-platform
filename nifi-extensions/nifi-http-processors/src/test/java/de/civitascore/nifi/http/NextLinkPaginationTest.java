/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class NextLinkPaginationTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "g | http://a/b/c/g",
        "./g | http://a/b/c/g",
        "g/ | http://a/b/c/g/",
        "/g | http://a/g",
        "//g | http://g",
        "?y | http://a/b/c/d;p?y",
        "g?y | http://a/b/c/g?y",
        "#s | http://a/b/c/d;p?q#s",
        "g#s | http://a/b/c/g#s",
        "g?y#s | http://a/b/c/g?y#s",
        ";x | http://a/b/c/;x",
        "g;x | http://a/b/c/g;x",
        "g;x?y#s | http://a/b/c/g;x?y#s",
        ". | http://a/b/c/",
        "./ | http://a/b/c/",
        ".. | http://a/b/",
        "../ | http://a/b/",
        "../g | http://a/b/g",
        "../.. | http://a/",
        "../../ | http://a/",
        "../../g | http://a/g",
        "../../../g | http://a/g",
        "../../../../g | http://a/g",
        "/./g | http://a/g",
        "/../g | http://a/g",
        "g. | http://a/b/c/g.",
        ".g | http://a/b/c/.g",
        "g.. | http://a/b/c/g..",
        "..g | http://a/b/c/..g",
        "./../g | http://a/b/g",
        "./g/. | http://a/b/c/g/",
        "g/./h | http://a/b/c/g/h",
        "g/../h | http://a/b/c/h",
        "g;x=1/./y | http://a/b/c/g;x=1/y",
        "g;x=1/../y | http://a/b/c/y",
        "g?y/./x | http://a/b/c/g?y/./x",
        "g?y/../x | http://a/b/c/g?y/../x",
        "g#s/./x | http://a/b/c/g#s/./x",
        "g#s/../x | http://a/b/c/g#s/../x"
      })
  void resolve_withTheRfc3986Examples_resolvesAgainstTheRequest(String reference, String expected) {
    assertEquals(
        URI.create(expected),
        NextLinkUrl.resolve(URI.create("http://a/b/c/d;p?q"), URI.create(reference)));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "http://api.example | next | http://api.example/next",
        "http://api.example/a//b | ../c | http://api.example/a/c",
        "http://api.example/a/b%2Fc?x=1 | ?x=2 | http://api.example/a/b%2Fc?x=2"
      })
  void resolve_keepsRawComponents(String base, String reference, String expected) {
    assertEquals(
        URI.create(expected), NextLinkUrl.resolve(URI.create(base), URI.create(reference)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://API.example/next",
        "HTTPS://api.example:443/next",
        "https://api.example:443/a?token=x"
      })
  void sameHost_withTheSameSchemeHostAndEffectivePort_accepts(String link) {
    assertTrue(NextLinkUrl.sameHost(URI.create("https://api.example"), URI.create(link)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://api.example:443/next",
        "https://another.example/next",
        "https://api.example:444/next",
        "https://api.example.evil/next",
        "https://api.example@evil/next",
        "file:///next",
        "mailto:api.example"
      })
  void sameHost_withAnotherSchemeHostOrPort_rejects(String link) {
    assertFalse(NextLinkUrl.sameHost(URI.create("https://api.example"), URI.create(link)));
  }
}
