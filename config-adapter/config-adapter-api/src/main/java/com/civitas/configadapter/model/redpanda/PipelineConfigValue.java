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
import com.civitas.configadapter.model.ConfigValue;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Top-level configuration value for RedPanda Connect pipeline resources. Contains the full pipeline
 * definition with input, processing, and output stages.
 */
public final class PipelineConfigValue extends AbstractApiModel implements ConfigValue {

  public static final String REDPANDA_RESULT_TYPE =
      "de.civitascore.data.pipeline.processing.result";

  @JsonProperty("pipeline_id")
  private String pipelineId;

  private PipelineInput input;
  private PipelineProcessors pipeline;
  private PipelineOutput output;

  public PipelineConfigValue() {}

  public String getPipelineId() {
    return pipelineId;
  }

  public void setPipelineId(String pipelineId) {
    this.pipelineId = pipelineId;
  }

  public PipelineInput getInput() {
    return input;
  }

  public void setInput(PipelineInput input) {
    this.input = input;
  }

  public PipelineProcessors getPipeline() {
    return pipeline;
  }

  public void setPipeline(PipelineProcessors pipeline) {
    this.pipeline = pipeline;
  }

  public PipelineOutput getOutput() {
    return output;
  }

  public void setOutput(PipelineOutput output) {
    this.output = output;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (input != null) map.put("input", input.toApiMap());
    if (pipeline != null) map.put("pipeline", pipeline.toApiMap());
    if (output != null) map.put("output", output.toApiMap());
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (PipelineConfigValue) obj;
    return Objects.equals(this.pipelineId, that.pipelineId)
        && Objects.equals(this.input, that.input)
        && Objects.equals(this.pipeline, that.pipeline)
        && Objects.equals(this.output, that.output)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(pipelineId, input, pipeline, output, additionalProperties());
  }

  @Override
  public String toString() {
    return "PipelineConfigValue["
        + "pipelineId="
        + pipelineId
        + ", input="
        + input
        + ", pipeline="
        + pipeline
        + ", output="
        + output
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}
