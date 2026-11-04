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

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** What each 2xx body of a run must be, and how it holds its records. */
sealed interface ResponseShape {

  /** The type the body must have, for a cause, for example {@code an array}. */
  String expected();

  /** The paths whose values the pagination reads from a response that is one record. */
  default List<JsonPath> paths() {
    return List.of();
  }

  /** The values at {@link #paths()}, as in {@link PageContent#values()}. */
  default Map<JsonPath, JsonNode> valuesIn(JsonNode record) {
    Map<JsonPath, JsonNode> values = new HashMap<>();
    for (JsonPath path : paths()) {
      values.put(path, path.valueIn(record));
    }
    return values;
  }

  /** A JSON array; each element is one record, and {@code []} holds none. */
  record RecordArray() implements ResponseShape {

    @Override
    public String expected() {
      return "an array";
    }
  }

  /**
   * A JSON object that is one record.
   *
   * @param paths the paths whose values the pagination reads from the record
   */
  record RecordObject(List<JsonPath> paths) implements ResponseShape {

    public RecordObject {
      paths = List.copyOf(paths);
    }

    @Override
    public String expected() {
      return "an object";
    }
  }

  /**
   * The value at a path holds the records: a JSON array one in each element, a JSON object one,
   * {@code null} none. The rest of the response holds no record.
   *
   * @param paths the paths whose values the pagination reads from the response
   */
  record EntriesAt(JsonPath entriesPath, List<JsonPath> paths) implements ResponseShape {

    public EntriesAt {
      paths = List.copyOf(paths);
    }

    @Override
    public String expected() {
      return "an object or an array";
    }
  }

  /** A JSON object is one record, and a JSON array holds one record in each element. */
  record ObjectOrArray() implements ResponseShape {

    @Override
    public String expected() {
      return "an object or an array";
    }
  }
}
