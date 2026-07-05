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
 * Pins the node-kind vocabulary. The type strings are the editor's node types and half of the
 * editor/adapter mirror — the frontend's node-flow declarations assign the same roles to the same
 * strings; a change on either side must consciously touch both.
 */
class NodeKindTest {

  @Test
  void pinsTypeStringToRoleTable() {
    Map<String, Role> expected =
        Map.of(
            "dataSource", Role.SOURCE,
            "mapping", Role.TRANSFORM,
            "frost", Role.SINK,
            "geoPersistence", Role.SINK,
            "cron", Role.TRIGGER,
            "start", Role.CONTROL,
            "end", Role.CONTROL);

    assertEquals(expected.size(), NodeKind.values().length);
    for (Map.Entry<String, Role> entry : expected.entrySet()) {
      NodeKind kind =
          NodeKind.fromTypeString(entry.getKey())
              .orElseThrow(() -> new AssertionError("no kind for '" + entry.getKey() + "'"));
      assertEquals(entry.getValue(), kind.role(), entry.getKey());
      assertEquals(entry.getKey(), kind.typeString());
    }
  }

  @Test
  void unknownAndNullTypesHaveNoKind() {
    assertTrue(NodeKind.fromTypeString("filter").isEmpty());
    assertTrue(NodeKind.fromTypeString(null).isEmpty());
  }
}
