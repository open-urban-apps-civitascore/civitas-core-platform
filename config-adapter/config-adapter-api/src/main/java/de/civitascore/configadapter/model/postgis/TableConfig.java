/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.postgis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for a SQL table, including optional geometry columns and indexes.
 *
 * <p>When {@link #getGeometryColumns()} is empty the table is purely relational; the dialect
 * generates no PostGIS-specific tokens, keeping the same payload usable by future non-spatial SQL
 * flavors.
 *
 * <p>Example JSON:
 *
 * <pre>{@code
 * {
 *   "resourceType": "postgis-table",
 *   "schema": "iot",
 *   "name": "sensor_readings",
 *   "columns": [
 *     {"name": "id", "type": "BIGINT", "nullable": false},
 *     {"name": "recorded_at", "type": "TIMESTAMPTZ", "nullable": false, "defaultExpr": "now()"}
 *   ],
 *   "geometryColumns": [
 *     {"name": "location", "geometryType": "POINT", "srid": 4326, "nullable": false}
 *   ],
 *   "primaryKey": ["id"],
 *   "indexes": [
 *     {"name": "idx_sensor_readings_location", "columns": ["location"], "method": "GIST"}
 *   ]
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class TableConfig implements PostgisConfigValue {

  private String schema;
  private String name;
  private List<ColumnConfig> columns;
  private List<GeometryColumnConfig> geometryColumns;
  private List<String> primaryKey;
  private List<IndexConfig> indexes;

  public TableConfig() {}

  public String getSchema() {
    return schema;
  }

  public void setSchema(String schema) {
    this.schema = schema;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<ColumnConfig> getColumns() {
    return columns == null ? List.of() : columns;
  }

  public void setColumns(List<ColumnConfig> columns) {
    this.columns = columns == null ? null : new ArrayList<>(columns);
  }

  public List<GeometryColumnConfig> getGeometryColumns() {
    return geometryColumns == null ? List.of() : geometryColumns;
  }

  public void setGeometryColumns(List<GeometryColumnConfig> geometryColumns) {
    this.geometryColumns = geometryColumns == null ? null : new ArrayList<>(geometryColumns);
  }

  public List<String> getPrimaryKey() {
    return primaryKey == null ? List.of() : primaryKey;
  }

  public void setPrimaryKey(List<String> primaryKey) {
    this.primaryKey = primaryKey == null ? null : new ArrayList<>(primaryKey);
  }

  public List<IndexConfig> getIndexes() {
    return indexes == null ? List.of() : indexes;
  }

  public void setIndexes(List<IndexConfig> indexes) {
    this.indexes = indexes == null ? null : new ArrayList<>(indexes);
  }

  /** Returns {@code "schema.name"} when a schema is set, otherwise just {@code "name"}. */
  public String qualifiedName() {
    return schema == null || schema.isBlank() ? name : schema + "." + name;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (TableConfig) obj;
    return Objects.equals(this.schema, that.schema)
        && Objects.equals(this.name, that.name)
        && Objects.equals(this.columns, that.columns)
        && Objects.equals(this.geometryColumns, that.geometryColumns)
        && Objects.equals(this.primaryKey, that.primaryKey)
        && Objects.equals(this.indexes, that.indexes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(schema, name, columns, geometryColumns, primaryKey, indexes);
  }

  @Override
  public String toString() {
    return "TableConfig["
        + "qualifiedName="
        + qualifiedName()
        + ", columns="
        + getColumns().size()
        + ", geometryColumns="
        + getGeometryColumns().size()
        + ", primaryKey="
        + getPrimaryKey()
        + ", indexes="
        + getIndexes().size()
        + ']';
  }
}
