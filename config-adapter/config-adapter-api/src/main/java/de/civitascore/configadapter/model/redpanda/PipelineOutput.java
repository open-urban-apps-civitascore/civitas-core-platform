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
 * Output container for a RedPanda Connect pipeline. Supports HTTP client output and switch
 * (conditional routing) output.
 */
public final class PipelineOutput extends AbstractApiModel {

  private static final String KEY_HTTP_CLIENT = "http_client";
  private static final String KEY_SWITCH = "switch";

  @JsonProperty("http_client")
  private HttpClientOutput httpClient;

  @JsonProperty("switch")
  private SwitchOutput switchOutput;

  public PipelineOutput() {}

  public HttpClientOutput getHttpClient() {
    return httpClient;
  }

  public void setHttpClient(HttpClientOutput httpClient) {
    this.httpClient = httpClient;
  }

  public SwitchOutput getSwitchOutput() {
    return switchOutput;
  }

  public void setSwitchOutput(SwitchOutput switchOutput) {
    this.switchOutput = switchOutput;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (httpClient != null) map.put(KEY_HTTP_CLIENT, httpClient.toApiMap());
    if (switchOutput != null) map.put(KEY_SWITCH, switchOutput.toApiMap());
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (PipelineOutput) obj;
    return Objects.equals(this.httpClient, that.httpClient)
        && Objects.equals(this.switchOutput, that.switchOutput)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(httpClient, switchOutput, additionalProperties());
  }

  @Override
  public String toString() {
    return "PipelineOutput["
        + "httpClient="
        + httpClient
        + ", switch="
        + switchOutput
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}
