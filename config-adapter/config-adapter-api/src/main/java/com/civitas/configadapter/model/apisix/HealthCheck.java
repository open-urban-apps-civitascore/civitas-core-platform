/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.model.apisix;

import com.civitas.configadapter.model.AbstractApiModel;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Top-level health check configuration for an APISIX upstream. Contains optional active and passive
 * health check settings.
 *
 * <p>Example configuration:
 *
 * <pre>{@code
 * {
 *   "active": {
 *     "type": "http",
 *     "http_path": "/health",
 *     "timeout": 5,
 *     "healthy": { "interval": 2, "successes": 1 },
 *     "unhealthy": { "interval": 1, "http_failures": 3 }
 *   },
 *   "passive": {
 *     "type": "http",
 *     "healthy": { "http_statuses": [200, 201], "successes": 3 },
 *     "unhealthy": { "http_statuses": [500], "http_failures": 3 }
 *   }
 * }
 * }</pre>
 *
 * @see <a href="https://apisix.apache.org/docs/apisix/tutorials/health-check/">APISIX Health
 *     Checks</a>
 */
public final class HealthCheck extends AbstractApiModel {

  private ActiveHealthCheck active;
  private PassiveHealthCheck passive;

  public HealthCheck() {}

  public ActiveHealthCheck getActive() {
    return active;
  }

  public void setActive(ActiveHealthCheck active) {
    this.active = active;
  }

  public PassiveHealthCheck getPassive() {
    return passive;
  }

  public void setPassive(PassiveHealthCheck passive) {
    this.passive = passive;
  }

  /**
   * Converts typed fields and additional properties to a plain map for the APISIX Admin API.
   *
   * @return an unmodifiable map of configuration key-value pairs
   */
  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (active != null) map.put("active", active.toApiMap());
    if (passive != null) map.put("passive", passive.toApiMap());
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (HealthCheck) obj;
    return Objects.equals(this.active, that.active)
        && Objects.equals(this.passive, that.passive)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(active, passive, additionalProperties());
  }

  @Override
  public String toString() {
    return "HealthCheck["
        + "active="
        + active
        + ", passive="
        + passive
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}
