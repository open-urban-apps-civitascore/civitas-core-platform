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

/** HTTP processor configuration for a RedPanda Connect pipeline. */
public final class HttpProcessor extends AbstractApiModel {

  private static final String KEY_URL = "url";
  private static final String KEY_VERB = "verb";
  private static final String KEY_HEADERS = "headers";
  private static final String KEY_TIMEOUT = "timeout";

  private String url;
  private String verb;
  private Map<String, String> headers;
  private String timeout;

  public HttpProcessor() {}

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

  public String getTimeout() {
    return timeout;
  }

  public void setTimeout(String timeout) {
    this.timeout = timeout;
  }

  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (url != null) map.put(KEY_URL, url);
    if (verb != null) map.put(KEY_VERB, verb);
    if (headers != null) map.put(KEY_HEADERS, headers);
    if (timeout != null) map.put(KEY_TIMEOUT, timeout);
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (HttpProcessor) obj;
    return Objects.equals(this.url, that.url)
        && Objects.equals(this.verb, that.verb)
        && Objects.equals(this.headers, that.headers)
        && Objects.equals(this.timeout, that.timeout)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(url, verb, headers, timeout, additionalProperties());
  }

  @Override
  public String toString() {
    return "HttpProcessor[" + "url=" + url + ", verb=" + verb + ']';
  }
}
