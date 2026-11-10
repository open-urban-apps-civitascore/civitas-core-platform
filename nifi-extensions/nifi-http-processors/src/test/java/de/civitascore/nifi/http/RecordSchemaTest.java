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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordSchemaTest {

  /** Without byte offsets, the delivered entries would be cut from the wrong bytes. */
  @Test
  void verdict_withAPathToEntriesInAUtf16Response_rejectsTheResponse() throws Exception {
    RecordSchema schema = RecordSchema.compile("{\"type\":\"object\"}");
    byte[] response = "{\"data\":[{}]}".getBytes(StandardCharsets.UTF_16BE);

    RecordSchema.Verdict verdict =
        schema.verdict(
            new ByteArrayInputStream(response),
            new ResponseShape.EntriesAt(new JsonPath("$.data"), List.of()));

    assertEquals(new RecordSchema.Verdict.Rejected("the response is not UTF-8"), verdict);
  }
}
