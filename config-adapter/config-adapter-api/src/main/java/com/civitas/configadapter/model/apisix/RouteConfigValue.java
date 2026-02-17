/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.model.apisix;

import com.civitas.configadapter.model.AbstractApiModel;
import com.civitas.configadapter.model.ConfigValue;
import com.civitas.configadapter.model.apisix.plugins.RoutePlugins;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration value for APISIX Route resources.
 *
 * <p>Common APISIX route configuration fields:
 *
 * <ul>
 *   <li>uri - The request URI path (e.g., "/api/v1/*")
 *   <li>uris - Array of URIs for multiple path matching
 *   <li>methods - HTTP methods to match (GET, POST, PUT, DELETE, etc.)
 *   <li>host - Host header to match
 *   <li>hosts - Array of hosts for multiple host matching
 *   <li>upstream_id - Reference to an upstream for backend routing
 *   <li>upstream - Inline upstream configuration
 *   <li>service_id - Reference to a service
 *   <li>plugins - Map of plugin configurations
 *   <li>priority - Route priority for matching order
 *   <li>enable_websocket - Enable WebSocket support
 *   <li>status - Route status (1 = enabled, 0 = disabled)
 * </ul>
 *
 * <p>Supported plugins (as per CIVITAS/CORE V1):
 *
 * <ul>
 *   <li>openid-connect - OpenID Connect authentication
 *   <li>serverless-post-function - Post-request serverless function
 *   <li>serverless-pre-function - Pre-request serverless function
 *   <li>response-rewrite - Response header/body rewriting
 *   <li>proxy-rewrite - Request URI/header rewriting
 *   <li>prometheus - Prometheus metrics collection
 *   <li>loki - Loki logging integration
 * </ul>
 *
 * <p>Example route configuration:
 *
 * <pre>{@code
 * {
 *   "uri": "/api/v1/users/*",
 *   "methods": ["GET", "POST", "PUT", "DELETE"],
 *   "upstream_id": "backend-users",
 *   "plugins": {
 *     "openid-connect": {
 *       "discovery": "https://keycloak.example.com/.well-known/openid-configuration",
 *       "client_id": "api-gateway",
 *       "client_secret": "secret"
 *     },
 *     "prometheus": {},
 *     "proxy-rewrite": {
 *       "regex_uri": ["/api/v1/(.*)", "/$1"]
 *     }
 *   }
 * }
 * }</pre>
 */
public final class RouteConfigValue extends AbstractApiModel implements ConfigValue {

  private String uri;
  private List<String> uris;
  private List<String> methods;

  @JsonProperty("upstream_id")
  private String upstreamId;

  private ApisixConfigValue upstream;
  private RoutePlugins plugins;
  private Integer priority;
  private Integer status;
  private String host;
  private List<String> hosts;

  @JsonProperty("service_id")
  private String serviceId;

  @JsonProperty("enable_websocket")
  private Boolean enableWebsocket;

  public RouteConfigValue() {}

  public String getUri() {
    return uri;
  }

  public void setUri(String uri) {
    this.uri = uri;
  }

  public List<String> getUris() {
    return uris;
  }

  public void setUris(List<String> uris) {
    this.uris = uris;
  }

  public List<String> getMethods() {
    return methods;
  }

  public void setMethods(List<String> methods) {
    this.methods = methods;
  }

  public String getUpstreamId() {
    return upstreamId;
  }

  public void setUpstreamId(String upstreamId) {
    this.upstreamId = upstreamId;
  }

  public ApisixConfigValue getUpstream() {
    return upstream;
  }

  public void setUpstream(ApisixConfigValue upstream) {
    this.upstream = upstream;
  }

  public RoutePlugins getPlugins() {
    return plugins;
  }

  public void setPlugins(RoutePlugins plugins) {
    this.plugins = plugins;
  }

  public Integer getPriority() {
    return priority;
  }

  public void setPriority(Integer priority) {
    this.priority = priority;
  }

  public Integer getStatus() {
    return status;
  }

  public void setStatus(Integer status) {
    this.status = status;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public List<String> getHosts() {
    return hosts;
  }

  public void setHosts(List<String> hosts) {
    this.hosts = hosts;
  }

  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  public Boolean getEnableWebsocket() {
    return enableWebsocket;
  }

  public void setEnableWebsocket(Boolean enableWebsocket) {
    this.enableWebsocket = enableWebsocket;
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
    if (uris != null) map.put("uris", uris);
    if (methods != null) map.put("methods", methods);
    if (upstreamId != null) map.put("upstream_id", upstreamId);
    if (upstream != null) map.put("upstream", upstream.toApiMap());
    if (plugins != null) map.put("plugins", plugins.toApiMap());
    if (priority != null) map.put("priority", priority);
    if (status != null) map.put("status", status);
    if (host != null) map.put("host", host);
    if (hosts != null) map.put("hosts", hosts);
    if (serviceId != null) map.put("service_id", serviceId);
    if (enableWebsocket != null) map.put("enable_websocket", enableWebsocket);
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (RouteConfigValue) obj;
    return Objects.equals(this.uri, that.uri)
        && Objects.equals(this.uris, that.uris)
        && Objects.equals(this.methods, that.methods)
        && Objects.equals(this.upstreamId, that.upstreamId)
        && Objects.equals(this.upstream, that.upstream)
        && Objects.equals(this.plugins, that.plugins)
        && Objects.equals(this.priority, that.priority)
        && Objects.equals(this.status, that.status)
        && Objects.equals(this.host, that.host)
        && Objects.equals(this.hosts, that.hosts)
        && Objects.equals(this.serviceId, that.serviceId)
        && Objects.equals(this.enableWebsocket, that.enableWebsocket)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        uri,
        uris,
        methods,
        upstreamId,
        upstream,
        plugins,
        priority,
        status,
        host,
        hosts,
        serviceId,
        enableWebsocket,
        additionalProperties());
  }

  @Override
  public String toString() {
    return "RouteConfigValue["
        + "uri="
        + uri
        + ", uris="
        + uris
        + ", methods="
        + methods
        + ", upstreamId="
        + upstreamId
        + ", upstream="
        + upstream
        + ", plugins="
        + plugins
        + ", priority="
        + priority
        + ", status="
        + status
        + ", host="
        + host
        + ", hosts="
        + hosts
        + ", serviceId="
        + serviceId
        + ", enableWebsocket="
        + enableWebsocket
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}
