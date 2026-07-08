/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class PostgisSinkSpecTest {

  @Test
  void escapesExpressionLanguageInTheSchemaName() {
    // "Schema Name" is EL-enabled in PutDatabaseRecord too, so a tenant ${…} must be escaped for
    // the same reason as the table name.
    PostgisSinkSpec spec = new PostgisSinkSpec("t", "${HOSTNAME}", List.of());
    assertEquals("$${HOSTNAME}", spec.schemaName());
  }

  @Test
  void normalizesABlankSchemaToNull() {
    // A blank schema means "unset": bind() then omits "Schema Name" and the write resolves via the
    // connection search_path, rather than pinning an empty schema.
    assertNull(new PostgisSinkSpec("t", "  ", List.of()).schemaName());
    assertNull(new PostgisSinkSpec("t").schemaName());
  }

  @Test
  void escapesExpressionLanguageInTheTableName() {
    // "Table Name" is EL-enabled in PutDatabaseRecord: an unescaped tenant ${…} would expand
    // against the NiFi process environment at write time and land in the tenant's table. The spec
    // must escape $ to $$ at construction so the identifier reaches the processor as literal text.
    PostgisSinkSpec spec = new PostgisSinkSpec("${SINGLE_USER_CREDENTIALS_PASSWORD}");
    assertEquals("$${SINGLE_USER_CREDENTIALS_PASSWORD}", spec.tableName());
  }

  @Test
  void escapesExpressionLanguageInThePrimaryKeyColumns() {
    // "Update Keys" is EL-enabled for the same reason; every key column carries the same escape.
    PostgisSinkSpec spec = new PostgisSinkSpec("t", List.of("stationId", "${HOSTNAME}"));
    assertEquals(List.of("stationId", "$${HOSTNAME}"), spec.primaryKeyColumns());
  }

  @Test
  void leavesAPlainTableNameUnchanged() {
    // The escape is a no-op for a legitimate identifier (no $), so it does not alter normal tables.
    assertEquals("measurements", new PostgisSinkSpec("measurements").tableName());
  }

  @Test
  void rejectsABlankTableName() {
    assertThrows(IllegalArgumentException.class, () -> new PostgisSinkSpec(" "));
  }

  @Test
  void rejectsBlankKeyColumnsAndDeduplicatesPreservingOrder() {
    assertThrows(IllegalArgumentException.class, () -> new PostgisSinkSpec("t", List.of("a", " ")));
    assertEquals(
        List.of("a", "b"), new PostgisSinkSpec("t", List.of("a", "b", "a")).primaryKeyColumns());
  }
}
