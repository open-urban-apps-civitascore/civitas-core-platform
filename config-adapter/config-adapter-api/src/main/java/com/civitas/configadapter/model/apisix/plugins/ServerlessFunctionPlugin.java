/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model.apisix.plugins;

import com.civitas.configadapter.model.AbstractApiModel;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for APISIX serverless-pre-function and serverless-post-function plugins. Executes
 * Lua functions at configurable request processing phases.
 */
public final class ServerlessFunctionPlugin extends AbstractApiModel {

  private String phase;
  private List<String> functions;

  public ServerlessFunctionPlugin() {}

  public String getPhase() {
    return phase;
  }

  public void setPhase(String phase) {
    this.phase = phase;
  }

  public List<String> getFunctions() {
    return functions;
  }

  public void setFunctions(List<String> functions) {
    this.functions = functions;
  }

  /**
   * Converts typed fields and additional properties to a plain map for the APISIX Admin API.
   *
   * @return an unmodifiable map of configuration key-value pairs
   */
  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (phase != null) map.put("phase", phase);
    if (functions != null) map.put("functions", functions);
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (ServerlessFunctionPlugin) obj;
    return Objects.equals(this.phase, that.phase)
        && Objects.equals(this.functions, that.functions)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(phase, functions, additionalProperties());
  }

  @Override
  public String toString() {
    return "ServerlessFunctionPlugin[phase=" + phase + ", functions=" + functions + ']';
  }
}
