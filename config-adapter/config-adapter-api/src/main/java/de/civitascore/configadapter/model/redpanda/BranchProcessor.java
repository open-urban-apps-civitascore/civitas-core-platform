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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Branch processor configuration for a RedPanda Connect pipeline. Supports recursive processor
 * steps within the branch.
 */
public final class BranchProcessor extends AbstractApiModel {

  private static final String KEY_REQUEST_MAP = "request_map";
  private static final String KEY_PROCESSORS = "processors";
  private static final String KEY_RESULT_MAP = "result_map";
  private static final int TOSTRING_MAX_LENGTH = 50;

  @JsonProperty("request_map")
  private String requestMap;

  private List<ProcessorStep> processors;

  @JsonProperty("result_map")
  private String resultMap;

  public BranchProcessor() {}

  public String getRequestMap() {
    return requestMap;
  }

  public void setRequestMap(String requestMap) {
    this.requestMap = requestMap;
  }

  public List<ProcessorStep> getProcessors() {
    return processors;
  }

  public void setProcessors(List<ProcessorStep> processors) {
    this.processors = processors;
  }

  public String getResultMap() {
    return resultMap;
  }

  public void setResultMap(String resultMap) {
    this.resultMap = resultMap;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (requestMap != null) map.put(KEY_REQUEST_MAP, requestMap);
    if (processors != null) {
      map.put(KEY_PROCESSORS, processors.stream().map(ProcessorStep::toApiMap).toList());
    }
    if (resultMap != null) map.put(KEY_RESULT_MAP, resultMap);
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (BranchProcessor) obj;
    return Objects.equals(this.requestMap, that.requestMap)
        && Objects.equals(this.processors, that.processors)
        && Objects.equals(this.resultMap, that.resultMap)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(requestMap, processors, resultMap, additionalProperties());
  }

  @Override
  public String toString() {
    return "BranchProcessor["
        + "requestMap="
        + StringUtils.truncate(requestMap, TOSTRING_MAX_LENGTH)
        + ", processors="
        + (processors != null ? processors.size() : 0)
        + ", resultMap="
        + StringUtils.truncate(resultMap, TOSTRING_MAX_LENGTH)
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}
