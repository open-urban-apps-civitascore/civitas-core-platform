/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.nifi.graph.NodeKind.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pins the node-kind vocabulary. The kind strings are the CORE Pipeline schema's node {@code kind}s
 * and half of the editor/adapter mirror — the pipeline document assigns the same roles to the same
 * strings; a change on either side must consciously touch both.
 */
class NodeKindTest {

  @Test
  void pinsKindStringToRoleTable() {
    Map<String, Role> expected =
        Map.of(
            "source", Role.SOURCE,
            "mapping", Role.TRANSFORM,
            "sink", Role.SINK,
            "cron", Role.TRIGGER,
            "start", Role.CONTROL,
            "end", Role.CONTROL);

    assertEquals(expected.size(), NodeKind.values().length);
    for (Map.Entry<String, Role> entry : expected.entrySet()) {
      NodeKind kind =
          NodeKind.fromKind(entry.getKey())
              .orElseThrow(() -> new AssertionError("no kind for '" + entry.getKey() + "'"));
      assertEquals(entry.getValue(), kind.role(), entry.getKey());
      assertEquals(entry.getKey(), kind.kindString());
    }
  }

  @Test
  void unsupportedAndNullKindsHaveNoKind() {
    // filter/enrich/split are CORE kinds this adapter does not deploy; the old editor type strings
    // (dataSource/frost/geoPersistence) are no longer the vocabulary either.
    assertTrue(NodeKind.fromKind("filter").isEmpty());
    assertTrue(NodeKind.fromKind("dataSource").isEmpty());
    assertTrue(NodeKind.fromKind(null).isEmpty());
  }
}
