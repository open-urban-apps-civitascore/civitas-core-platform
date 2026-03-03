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

import com.fasterxml.jackson.annotation.JsonProperty;
import de.civitascore.configadapter.model.AbstractApiModel;
import de.civitascore.configadapter.util.StringUtils;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A single processor step in a RedPanda Connect pipeline. Supports Bloblang mapping, branch,
 * unarchive, sql_select, and http processors.
 */
public final class ProcessorStep extends AbstractApiModel {

  private static final String KEY_MAPPING = "mapping";
  private static final String KEY_BRANCH = "branch";
  private static final String KEY_UNARCHIVE = "unarchive";
  private static final String KEY_SQL_SELECT = "sql_select";
  private static final String KEY_HTTP = "http";
  private static final int TOSTRING_MAX_LENGTH = 50;

  private String mapping;
  private BranchProcessor branch;
  private UnarchiveProcessor unarchive;

  @JsonProperty("sql_select")
  private SqlSelectProcessor sqlSelect;

  private HttpProcessor http;

  public ProcessorStep() {}

  public String getMapping() {
    return mapping;
  }

  public void setMapping(String mapping) {
    this.mapping = mapping;
  }

  public BranchProcessor getBranch() {
    return branch;
  }

  public void setBranch(BranchProcessor branch) {
    this.branch = branch;
  }

  public UnarchiveProcessor getUnarchive() {
    return unarchive;
  }

  public void setUnarchive(UnarchiveProcessor unarchive) {
    this.unarchive = unarchive;
  }

  public SqlSelectProcessor getSqlSelect() {
    return sqlSelect;
  }

  public void setSqlSelect(SqlSelectProcessor sqlSelect) {
    this.sqlSelect = sqlSelect;
  }

  public HttpProcessor getHttp() {
    return http;
  }

  public void setHttp(HttpProcessor http) {
    this.http = http;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (mapping != null) map.put(KEY_MAPPING, mapping);
    if (branch != null) map.put(KEY_BRANCH, branch.toApiMap());
    if (unarchive != null) map.put(KEY_UNARCHIVE, unarchive.toApiMap());
    if (sqlSelect != null) map.put(KEY_SQL_SELECT, sqlSelect.toApiMap());
    if (http != null) map.put(KEY_HTTP, http.toApiMap());
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (ProcessorStep) obj;
    return Objects.equals(this.mapping, that.mapping)
        && Objects.equals(this.branch, that.branch)
        && Objects.equals(this.unarchive, that.unarchive)
        && Objects.equals(this.sqlSelect, that.sqlSelect)
        && Objects.equals(this.http, that.http)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(mapping, branch, unarchive, sqlSelect, http, additionalProperties());
  }

  @Override
  public String toString() {
    return "ProcessorStep["
        + "mapping="
        + StringUtils.truncate(mapping, TOSTRING_MAX_LENGTH)
        + ", branch="
        + branch
        + ", unarchive="
        + unarchive
        + ", sqlSelect="
        + sqlSelect
        + ", http="
        + http
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}
