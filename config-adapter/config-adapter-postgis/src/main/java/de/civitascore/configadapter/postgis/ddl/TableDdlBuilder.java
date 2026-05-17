/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.postgis.ddl;

import de.civitascore.configadapter.model.postgis.TableConfig;
import de.civitascore.configadapter.postgis.dialect.SqlDialect;
import java.util.List;

/**
 * Builds ordered DDL statements for a {@link TableConfig} via a {@link SqlDialect}. Stateless; one
 * instance can be reused across events.
 */
public final class TableDdlBuilder {

  private final SqlDialect dialect;

  public TableDdlBuilder(SqlDialect dialect) {
    this.dialect = dialect;
  }

  public List<String> buildCreate(TableConfig table) {
    return dialect.createTable(table);
  }

  public List<String> buildDrop(TableConfig table) {
    return dialect.dropTable(table.getSchema(), table.getName());
  }
}
