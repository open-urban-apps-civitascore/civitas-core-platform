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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.exception.FlowFileAccessException;

final class RecordShape {

  private static final JsonFactory JSON = new JsonFactory();

  private RecordShape() {}

  /**
   * Whether the content of the FlowFile is one JSON object or one array of JSON objects. Content
   * that is not JSON is not an error but {@code false}.
   *
   * @throws FlowFileAccessException when the content cannot be read
   */
  static boolean holdsRecords(ProcessSession session, FlowFile flowFile) {
    try (InputStream content = session.read(flowFile);
        JsonParser parser = JSON.createParser(content)) {
      JsonToken root = parser.nextToken();
      if (root == JsonToken.START_OBJECT) {
        parser.skipChildren();
      } else if (root == JsonToken.START_ARRAY) {
        for (JsonToken element = parser.nextToken();
            element != JsonToken.END_ARRAY;
            element = parser.nextToken()) {
          if (element != JsonToken.START_OBJECT) {
            return false;
          }
          parser.skipChildren();
        }
      } else {
        return false;
      }
      return parser.nextToken() == null;
    } catch (JsonProcessingException e) {
      return false;
    } catch (IOException e) {
      throw new FlowFileAccessException("the response body could not be read", e);
    }
  }
}
