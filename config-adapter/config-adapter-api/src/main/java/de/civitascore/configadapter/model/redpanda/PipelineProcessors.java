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

/** Container for processor steps in a RedPanda Connect pipeline. */
public final class PipelineProcessors extends AbstractApiModel {

  private List<ProcessorStep> processors;

  public PipelineProcessors() {}

  public List<ProcessorStep> getProcessors() {
    return processors;
  }

  public void setProcessors(List<ProcessorStep> processors) {
    this.processors = processors;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (processors != null) {
      map.put("processors", processors.stream().map(ProcessorStep::toApiMap).toList());
    }
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (PipelineProcessors) obj;
    return Objects.equals(this.processors, that.processors)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(processors, additionalProperties());
  }

  @Override
  public String toString() {
    return "PipelineProcessors["
        + "processors="
        + (processors != null ? processors.size() : 0)
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}
