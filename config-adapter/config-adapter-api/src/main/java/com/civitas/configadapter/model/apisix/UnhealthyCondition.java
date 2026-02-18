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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Defines what constitutes an unhealthy upstream node in APISIX health checks. Used within both
 * active and passive health check configurations.
 *
 * @see <a href="https://apisix.apache.org/docs/apisix/tutorials/health-check/">APISIX Health
 *     Checks</a>
 */
public final class UnhealthyCondition extends AbstractApiModel {

  private Integer interval;

  @JsonProperty("http_failures")
  private Integer httpFailures;

  @JsonProperty("tcp_failures")
  private Integer tcpFailures;

  private Integer timeouts;

  @JsonProperty("http_statuses")
  private List<Integer> httpStatuses;

  public UnhealthyCondition() {}

  public Integer getInterval() {
    return interval;
  }

  public void setInterval(Integer interval) {
    this.interval = interval;
  }

  public Integer getHttpFailures() {
    return httpFailures;
  }

  public void setHttpFailures(Integer httpFailures) {
    this.httpFailures = httpFailures;
  }

  public Integer getTcpFailures() {
    return tcpFailures;
  }

  public void setTcpFailures(Integer tcpFailures) {
    this.tcpFailures = tcpFailures;
  }

  public Integer getTimeouts() {
    return timeouts;
  }

  public void setTimeouts(Integer timeouts) {
    this.timeouts = timeouts;
  }

  public List<Integer> getHttpStatuses() {
    return httpStatuses;
  }

  public void setHttpStatuses(List<Integer> httpStatuses) {
    this.httpStatuses = httpStatuses;
  }

  /**
   * Converts typed fields and additional properties to a plain map for the APISIX Admin API.
   *
   * @return an unmodifiable map of configuration key-value pairs
   */
  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (interval != null) map.put("interval", interval);
    if (httpFailures != null) map.put("http_failures", httpFailures);
    if (tcpFailures != null) map.put("tcp_failures", tcpFailures);
    if (timeouts != null) map.put("timeouts", timeouts);
    if (httpStatuses != null) map.put("http_statuses", httpStatuses);
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (UnhealthyCondition) obj;
    return Objects.equals(this.interval, that.interval)
        && Objects.equals(this.httpFailures, that.httpFailures)
        && Objects.equals(this.tcpFailures, that.tcpFailures)
        && Objects.equals(this.timeouts, that.timeouts)
        && Objects.equals(this.httpStatuses, that.httpStatuses)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        interval, httpFailures, tcpFailures, timeouts, httpStatuses, additionalProperties());
  }

  @Override
  public String toString() {
    return "UnhealthyCondition["
        + "interval="
        + interval
        + ", httpFailures="
        + httpFailures
        + ", tcpFailures="
        + tcpFailures
        + ", timeouts="
        + timeouts
        + ", httpStatuses="
        + httpStatuses
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}
