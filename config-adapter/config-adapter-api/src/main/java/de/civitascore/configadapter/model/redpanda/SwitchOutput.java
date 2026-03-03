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

/** Switch output configuration for a RedPanda Connect pipeline with conditional routing. */
public final class SwitchOutput extends AbstractApiModel {

  private static final String KEY_CASES = "cases";

  private List<SwitchCase> cases;

  public SwitchOutput() {}

  public List<SwitchCase> getCases() {
    return cases;
  }

  public void setCases(List<SwitchCase> cases) {
    this.cases = cases;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (cases != null) {
      map.put(KEY_CASES, cases.stream().map(SwitchCase::toApiMap).toList());
    }
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (SwitchOutput) obj;
    return Objects.equals(this.cases, that.cases)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(cases, additionalProperties());
  }

  @Override
  public String toString() {
    return "SwitchOutput["
        + "cases="
        + (cases != null ? cases.size() : 0)
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}
