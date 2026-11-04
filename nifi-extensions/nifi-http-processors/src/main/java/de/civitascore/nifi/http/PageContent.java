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
import java.util.Map;

/**
 * What a page whose records agree with the Record Schema gave.
 *
 * @param records the number of records: the elements of an array, 1 for an object
 * @param values the value at each path of {@link ResponseShape.RecordObject}, a {@code MissingNode}
 *     where the response has none; empty for any other shape
 * @param numberTexts the JSON text of each number in {@code values}, as the response writes it
 */
record PageContent(int records, Map<JsonPath, JsonNode> values, Map<JsonPath, String> numberTexts) {

  PageContent {
    values = Map.copyOf(values);
    numberTexts = Map.copyOf(numberTexts);
  }
}
