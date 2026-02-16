/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model.apisix.plugins;

import com.civitas.configadapter.model.AbstractApiModel;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for the APISIX response-rewrite plugin. Modifies response status code, headers, and
 * body before sending to the client.
 */
public final class ResponseRewritePlugin extends AbstractApiModel {

  @JsonProperty("status_code")
  private Integer statusCode;

  private RewriteHeaders headers;
  private String body;

  @JsonProperty("body_base64")
  private Boolean bodyBase64;

  private List<ResponseFilter> filters;

  public ResponseRewritePlugin() {}

  public Integer getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(Integer statusCode) {
    this.statusCode = statusCode;
  }

  public RewriteHeaders getHeaders() {
    return headers;
  }

  public void setHeaders(RewriteHeaders headers) {
    this.headers = headers;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public Boolean getBodyBase64() {
    return bodyBase64;
  }

  public void setBodyBase64(Boolean bodyBase64) {
    this.bodyBase64 = bodyBase64;
  }

  public List<ResponseFilter> getFilters() {
    return filters;
  }

  public void setFilters(List<ResponseFilter> filters) {
    this.filters = filters;
  }

  /**
   * Converts typed fields and additional properties to a plain map for the APISIX Admin API.
   *
   * @return an unmodifiable map of configuration key-value pairs
   */
  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (statusCode != null) map.put("status_code", statusCode);
    if (headers != null) map.put("headers", headers.toApiMap());
    if (body != null) map.put("body", body);
    if (bodyBase64 != null) map.put("body_base64", bodyBase64);
    if (filters != null) {
      map.put("filters", filters.stream().map(ResponseFilter::toApiMap).toList());
    }
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (ResponseRewritePlugin) obj;
    return Objects.equals(this.statusCode, that.statusCode)
        && Objects.equals(this.headers, that.headers)
        && Objects.equals(this.body, that.body)
        && Objects.equals(this.bodyBase64, that.bodyBase64)
        && Objects.equals(this.filters, that.filters)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(statusCode, headers, body, bodyBase64, filters, additionalProperties());
  }

  @Override
  public String toString() {
    return "ResponseRewritePlugin[statusCode="
        + statusCode
        + ", body="
        + body
        + ", bodyBase64="
        + bodyBase64
        + ']';
  }
}
