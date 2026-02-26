/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.apisix.plugins;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.civitascore.configadapter.model.AbstractApiModel;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for the APISIX proxy-rewrite plugin. Rewrites upstream request URI, host, and
 * headers before proxying.
 */
public final class ProxyRewritePlugin extends AbstractApiModel {

  private String uri;

  @JsonProperty("regex_uri")
  private List<String> regexUri;

  private String host;
  private RewriteHeaders headers;

  public ProxyRewritePlugin() {}

  public String getUri() {
    return uri;
  }

  public void setUri(String uri) {
    this.uri = uri;
  }

  public List<String> getRegexUri() {
    return regexUri;
  }

  public void setRegexUri(List<String> regexUri) {
    this.regexUri = regexUri;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public RewriteHeaders getHeaders() {
    return headers;
  }

  public void setHeaders(RewriteHeaders headers) {
    this.headers = headers;
  }

  /**
   * Converts typed fields and additional properties to a plain map for the APISIX Admin API.
   *
   * @return an unmodifiable map of configuration key-value pairs
   */
  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (uri != null) map.put("uri", uri);
    if (regexUri != null) map.put("regex_uri", regexUri);
    if (host != null) map.put("host", host);
    if (headers != null) map.put("headers", headers.toApiMap());
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (ProxyRewritePlugin) obj;
    return Objects.equals(this.uri, that.uri)
        && Objects.equals(this.regexUri, that.regexUri)
        && Objects.equals(this.host, that.host)
        && Objects.equals(this.headers, that.headers)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(uri, regexUri, host, headers, additionalProperties());
  }

  @Override
  public String toString() {
    return "ProxyRewritePlugin[uri="
        + uri
        + ", regexUri="
        + regexUri
        + ", host="
        + host
        + ", headers="
        + headers
        + ']';
  }
}
