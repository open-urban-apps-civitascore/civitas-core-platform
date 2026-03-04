/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.redpanda;

import de.civitascore.configadapter.model.AbstractApiModel;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** SQL select processor configuration for a RedPanda Connect pipeline. */
public final class SqlSelectProcessor extends AbstractApiModel {

  private static final String KEY_DRIVER = "driver";
  private static final String KEY_DSN = "dsn";
  private static final String KEY_TABLE = "table";
  private static final String KEY_COLUMNS = "columns";
  private static final String REDACTED = "[PRESENT]";

  private String driver;
  private String dsn;
  private String table;
  private List<String> columns;

  public SqlSelectProcessor() {}

  public String getDriver() {
    return driver;
  }

  public void setDriver(String driver) {
    this.driver = driver;
  }

  public String getDsn() {
    return dsn;
  }

  public void setDsn(String dsn) {
    this.dsn = dsn;
  }

  public String getTable() {
    return table;
  }

  public void setTable(String table) {
    this.table = table;
  }

  public List<String> getColumns() {
    return columns;
  }

  public void setColumns(List<String> columns) {
    this.columns = columns;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (driver != null) map.put(KEY_DRIVER, driver);
    if (dsn != null) map.put(KEY_DSN, dsn);
    if (table != null) map.put(KEY_TABLE, table);
    if (columns != null) map.put(KEY_COLUMNS, columns);
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (SqlSelectProcessor) obj;
    return Objects.equals(this.driver, that.driver)
        && Objects.equals(this.dsn, that.dsn)
        && Objects.equals(this.table, that.table)
        && Objects.equals(this.columns, that.columns)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(driver, dsn, table, columns, additionalProperties());
  }

  @Override
  public String toString() {
    return "SqlSelectProcessor["
        + "driver="
        + driver
        + ", dsn="
        + (dsn != null ? REDACTED : "null")
        + ", table="
        + table
        + ", columns="
        + columns
        + ']';
  }
}
