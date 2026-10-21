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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.JsonEOFException;
import java.io.IOException;

/**
 * Tells what the HTTP pipeline reads from a response body: records from a JSON object or from the
 * objects of a JSON array, nothing from other JSON, and an error from anything that is not JSON.
 */
enum JsonBody {
  RECORDS,
  NO_RECORDS,
  NOT_JSON;

  private static final JsonFactory FACTORY = new JsonFactory();

  /**
   * @param text the body, or its first part
   * @param truncated whether {@code text} is only the first part; its end is then not judged
   */
  static JsonBody classify(String text, boolean truncated) {
    try (JsonParser parser = FACTORY.createParser(text)) {
      JsonToken root = parser.nextToken();
      if (root == null) {
        return NOT_JSON;
      }
      JsonToken first = root == JsonToken.START_ARRAY ? parser.nextToken() : null;
      parser.skipChildren();
      // Reads to the end, so that a malformed part further on is found.
      while (parser.nextToken() != null) {
        parser.skipChildren();
      }
      boolean records =
          root == JsonToken.START_OBJECT
              || root == JsonToken.START_ARRAY && first == JsonToken.START_OBJECT;
      return records ? RECORDS : NO_RECORDS;
    } catch (JsonEOFException e) {
      return truncated ? recordsSoFar(text) : NOT_JSON;
    } catch (IOException e) {
      return NOT_JSON;
    }
  }

  /** A cut body is judged by its start: the rest was not read. */
  private static JsonBody recordsSoFar(String text) {
    try (JsonParser parser = FACTORY.createParser(text)) {
      JsonToken root = parser.nextToken();
      if (root == JsonToken.START_OBJECT) {
        return RECORDS;
      }
      return root == JsonToken.START_ARRAY && parser.nextToken() == JsonToken.START_OBJECT
          ? RECORDS
          : NO_RECORDS;
    } catch (IOException e) {
      return NOT_JSON;
    }
  }
}
