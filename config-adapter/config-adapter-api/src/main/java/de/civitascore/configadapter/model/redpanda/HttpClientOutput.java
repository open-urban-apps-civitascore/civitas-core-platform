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
import de.civitascore.configadapter.model.BasicAuth;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** HTTP client output configuration for a RedPanda Connect pipeline. */
public final class HttpClientOutput extends AbstractApiModel {

  private String url;
  private String verb;
  private Map<String, String> headers;

  @JsonProperty("rate_limit")
  private String rateLimit;

  private String timeout;

  @JsonProperty("max_in_flight")
  private Integer maxInFlight;

  @JsonProperty("basic_auth")
  private BasicAuth basicAuth;

  public HttpClientOutput() {}

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getVerb() {
    return verb;
  }

  public void setVerb(String verb) {
    this.verb = verb;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public void setHeaders(Map<String, String> headers) {
    this.headers = headers;
  }

  public String getRateLimit() {
    return rateLimit;
  }

  public void setRateLimit(String rateLimit) {
    this.rateLimit = rateLimit;
  }

  public String getTimeout() {
    return timeout;
  }

  public void setTimeout(String timeout) {
    this.timeout = timeout;
  }

  public Integer getMaxInFlight() {
    return maxInFlight;
  }

  public void setMaxInFlight(Integer maxInFlight) {
    this.maxInFlight = maxInFlight;
  }

  public BasicAuth getBasicAuth() {
    return basicAuth;
  }

  public void setBasicAuth(BasicAuth basicAuth) {
    this.basicAuth = basicAuth;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (url != null) map.put("url", url);
    if (verb != null) map.put("verb", verb);
    if (headers != null) map.put("headers", headers);
    if (rateLimit != null) map.put("rate_limit", rateLimit);
    if (timeout != null) map.put("timeout", timeout);
    if (maxInFlight != null) map.put("max_in_flight", maxInFlight);
    if (basicAuth != null) map.put("basic_auth", basicAuth.toApiMap());
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (HttpClientOutput) obj;
    return Objects.equals(this.url, that.url)
        && Objects.equals(this.verb, that.verb)
        && Objects.equals(this.headers, that.headers)
        && Objects.equals(this.rateLimit, that.rateLimit)
        && Objects.equals(this.timeout, that.timeout)
        && Objects.equals(this.maxInFlight, that.maxInFlight)
        && Objects.equals(this.basicAuth, that.basicAuth)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        url, verb, headers, rateLimit, timeout, maxInFlight, basicAuth, additionalProperties());
  }

  @Override
  public String toString() {
    return "HttpClientOutput["
        + "url="
        + url
        + ", verb="
        + verb
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}
