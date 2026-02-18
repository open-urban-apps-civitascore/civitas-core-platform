/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model.redpanda;

import com.civitas.configadapter.model.AbstractApiModel;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** SQL raw database source input configuration for a RedPanda Connect pipeline. */
public final class SqlRawInput extends AbstractApiModel {

  private String driver;
  private String dsn;
  private String query;

  @JsonProperty("args_mapping")
  private String argsMapping;

  public SqlRawInput() {}

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

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public String getArgsMapping() {
    return argsMapping;
  }

  public void setArgsMapping(String argsMapping) {
    this.argsMapping = argsMapping;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (driver != null) map.put("driver", driver);
    if (dsn != null) map.put("dsn", dsn);
    if (query != null) map.put("query", query);
    if (argsMapping != null) map.put("args_mapping", argsMapping);
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (SqlRawInput) obj;
    return Objects.equals(this.driver, that.driver)
        && Objects.equals(this.dsn, that.dsn)
        && Objects.equals(this.query, that.query)
        && Objects.equals(this.argsMapping, that.argsMapping)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(driver, dsn, query, argsMapping, additionalProperties());
  }

  @Override
  public String toString() {
    return "SqlRawInput["
        + "driver="
        + driver
        + ", dsn="
        + (dsn != null ? "[PRESENT]" : "null")
        + ", query="
        + query
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}
