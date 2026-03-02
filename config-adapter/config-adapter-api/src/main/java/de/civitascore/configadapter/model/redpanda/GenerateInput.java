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
import java.util.Map;
import java.util.Objects;

/** Generate input configuration for a RedPanda Connect pipeline (synthetic data generation). */
public final class GenerateInput extends AbstractApiModel {

  private String interval;
  private Integer count;
  private String mapping;

  public GenerateInput() {}

  public String getInterval() {
    return interval;
  }

  public void setInterval(String interval) {
    this.interval = interval;
  }

  public Integer getCount() {
    return count;
  }

  public void setCount(Integer count) {
    this.count = count;
  }

  public String getMapping() {
    return mapping;
  }

  public void setMapping(String mapping) {
    this.mapping = mapping;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (interval != null) map.put("interval", interval);
    if (count != null) map.put("count", count);
    if (mapping != null) map.put("mapping", mapping);
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (GenerateInput) obj;
    return Objects.equals(this.interval, that.interval)
        && Objects.equals(this.count, that.count)
        && Objects.equals(this.mapping, that.mapping)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(interval, count, mapping, additionalProperties());
  }

  @Override
  public String toString() {
    return "GenerateInput["
        + "interval="
        + interval
        + ", count="
        + count
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}
