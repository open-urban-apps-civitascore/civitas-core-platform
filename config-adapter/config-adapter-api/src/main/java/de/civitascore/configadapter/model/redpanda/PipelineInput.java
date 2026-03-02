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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Input container for a RedPanda Connect pipeline. Supports MQTT, SQL raw, and generate sources.
 */
public final class PipelineInput extends AbstractApiModel {

  private MqttInput mqtt;

  @JsonProperty("sql_raw")
  private SqlRawInput sqlRaw;

  private GenerateInput generate;

  private String label;

  public PipelineInput() {}

  public MqttInput getMqtt() {
    return mqtt;
  }

  public void setMqtt(MqttInput mqtt) {
    this.mqtt = mqtt;
  }

  public SqlRawInput getSqlRaw() {
    return sqlRaw;
  }

  public void setSqlRaw(SqlRawInput sqlRaw) {
    this.sqlRaw = sqlRaw;
  }

  public GenerateInput getGenerate() {
    return generate;
  }

  public void setGenerate(GenerateInput generate) {
    this.generate = generate;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (label != null) map.put("label", label);
    if (mqtt != null) map.put("mqtt", mqtt.toApiMap());
    if (sqlRaw != null) map.put("sql_raw", sqlRaw.toApiMap());
    if (generate != null) map.put("generate", generate.toApiMap());
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (PipelineInput) obj;
    return Objects.equals(this.mqtt, that.mqtt)
        && Objects.equals(this.sqlRaw, that.sqlRaw)
        && Objects.equals(this.generate, that.generate)
        && Objects.equals(this.label, that.label)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(mqtt, sqlRaw, generate, label, additionalProperties());
  }

  @Override
  public String toString() {
    return "PipelineInput["
        + "mqtt="
        + mqtt
        + ", sqlRaw="
        + sqlRaw
        + ", generate="
        + generate
        + ", label="
        + label
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}
