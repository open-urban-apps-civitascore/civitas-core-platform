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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.util.JsonParserDelegate;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the JSON text of each number at the given paths while a tree is read from the parser. The
 * tree keeps only the value: it holds {@code 1.50} as 1.5, {@code 1e2} as 100.0 and {@code -0} as
 * 0.
 */
final class NumberTextParser extends JsonParserDelegate {

  private final List<JsonPath> paths;
  private final Map<JsonPath, String> texts = new HashMap<>();

  NumberTextParser(JsonParser parser, List<JsonPath> paths) {
    super(parser);
    this.paths = List.copyOf(paths);
  }

  @Override
  public JsonToken nextToken() throws IOException {
    JsonToken token = super.nextToken();
    if (token != null && token.isNumeric()) {
      for (JsonPath path : paths) {
        if (path.isAt(getParsingContext())) {
          texts.put(path, getText());
        }
      }
    }
    return token;
  }

  /** The JSON text of the number at each path that holds a number. */
  Map<JsonPath, String> texts() {
    return Map.copyOf(texts);
  }
}
