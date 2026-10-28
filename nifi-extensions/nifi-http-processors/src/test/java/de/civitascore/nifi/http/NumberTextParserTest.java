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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NumberTextParserTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void texts_holdTheJsonTextOfEachNumberAtAPath() throws IOException {
    String json =
        """
        {"meta": {"next": 1.50}, "list": [7, 1e2], "a/b~c": -0, "text": "1.5", "0": 3}
        """;
    List<JsonPath> paths =
        List.of(
            new JsonPath("$.meta.next"),
            new JsonPath("$.list[1]"),
            new JsonPath("$['a/b~c']"),
            new JsonPath("$.text"),
            new JsonPath("$.next"),
            new JsonPath("$[0]"));

    try (NumberTextParser parser = new NumberTextParser(MAPPER.createParser(json), paths)) {
      MAPPER.readTree(parser);

      assertEquals(
          Map.of(
              new JsonPath("$.meta.next"), "1.50",
              new JsonPath("$.list[1]"), "1e2",
              new JsonPath("$['a/b~c']"), "-0"),
          parser.texts());
    }
  }
}
