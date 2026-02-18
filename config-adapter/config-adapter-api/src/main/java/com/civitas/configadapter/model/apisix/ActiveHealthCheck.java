/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model.apisix;

import com.civitas.configadapter.model.AbstractApiModel;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Active health check configuration for an APISIX upstream. APISIX periodically probes the upstream
 * nodes to determine their health.
 *
 * <p>Supported types: {@code http}, {@code https}, {@code tcp}.
 *
 * @see <a href="https://apisix.apache.org/docs/apisix/tutorials/health-check/">APISIX Health
 *     Checks</a>
 */
public final class ActiveHealthCheck extends AbstractApiModel {

  private String type;

  @JsonProperty("http_path")
  private String httpPath;

  // Number (not Integer): APISIX allows fractional seconds, e.g. 0.5
  private Number timeout;
  private Integer concurrency;
  private HealthyCondition healthy;
  private UnhealthyCondition unhealthy;

  public ActiveHealthCheck() {}

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getHttpPath() {
    return httpPath;
  }

  public void setHttpPath(String httpPath) {
    this.httpPath = httpPath;
  }

  public Number getTimeout() {
    return timeout;
  }

  public void setTimeout(Number timeout) {
    this.timeout = timeout;
  }

  public Integer getConcurrency() {
    return concurrency;
  }

  public void setConcurrency(Integer concurrency) {
    this.concurrency = concurrency;
  }

  public HealthyCondition getHealthy() {
    return healthy;
  }

  public void setHealthy(HealthyCondition healthy) {
    this.healthy = healthy;
  }

  public UnhealthyCondition getUnhealthy() {
    return unhealthy;
  }

  public void setUnhealthy(UnhealthyCondition unhealthy) {
    this.unhealthy = unhealthy;
  }

  /**
   * Converts typed fields and additional properties to a plain map for the APISIX Admin API.
   *
   * @return an unmodifiable map of configuration key-value pairs
   */
  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (type != null) map.put("type", type);
    if (httpPath != null) map.put("http_path", httpPath);
    if (timeout != null) map.put("timeout", timeout);
    if (concurrency != null) map.put("concurrency", concurrency);
    if (healthy != null) map.put("healthy", healthy.toApiMap());
    if (unhealthy != null) map.put("unhealthy", unhealthy.toApiMap());
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (ActiveHealthCheck) obj;
    return Objects.equals(this.type, that.type)
        && Objects.equals(this.httpPath, that.httpPath)
        && Objects.equals(this.timeout, that.timeout)
        && Objects.equals(this.concurrency, that.concurrency)
        && Objects.equals(this.healthy, that.healthy)
        && Objects.equals(this.unhealthy, that.unhealthy)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        type, httpPath, timeout, concurrency, healthy, unhealthy, additionalProperties());
  }

  @Override
  public String toString() {
    return "ActiveHealthCheck["
        + "type="
        + type
        + ", httpPath="
        + httpPath
        + ", timeout="
        + timeout
        + ", concurrency="
        + concurrency
        + ", healthy="
        + healthy
        + ", unhealthy="
        + unhealthy
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}
