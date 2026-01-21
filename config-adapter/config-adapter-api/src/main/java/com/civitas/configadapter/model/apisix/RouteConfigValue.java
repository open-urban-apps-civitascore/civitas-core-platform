/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.model.apisix;

import com.civitas.configadapter.model.ConfigValue;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public final class RouteConfigValue implements ConfigValue {

  private final Map<String, Object> data;

  @JsonCreator
  public RouteConfigValue() {
    this.data = new LinkedHashMap<>();
  }

  public RouteConfigValue(Map<String, Object> data) {
    this.data = data != null ? new LinkedHashMap<>(data) : new LinkedHashMap<>();
  }

  @JsonAnyGetter
  public Map<String, Object> data() {
    return data;
  }

  @JsonAnySetter
  public void setProperty(String key, Object value) {
    data.put(key, value);
  }

  /**
   * Get a property value by key.
   *
   * @param key the property key
   * @return the property value, or null if not present
   */
  public Object get(String key) {
    return data.get(key);
  }

  /**
   * Check if a property exists.
   *
   * @param key the property key
   * @return true if the property exists
   */
  public boolean has(String key) {
    return data.containsKey(key);
  }

  /**
   * Get the route URI.
   *
   * @return the URI path, or null if not specified
   */
  @JsonIgnore
  public String getUri() {
    return (String) data.get("uri");
  }

  /**
   * Get the route URIs array.
   *
   * @return list of URIs, or null if not specified
   */
  @JsonIgnore
  @SuppressWarnings("unchecked")
  public List<String> getUris() {
    return (List<String>) data.get("uris");
  }

  /**
   * Get the HTTP methods this route matches.
   *
   * @return list of HTTP methods, or null if not specified
   */
  @JsonIgnore
  @SuppressWarnings("unchecked")
  public List<String> getMethods() {
    return (List<String>) data.get("methods");
  }

  /**
   * Get the upstream ID for this route.
   *
   * @return the upstream ID, or null if not specified
   */
  @JsonIgnore
  public String getUpstreamId() {
    return (String) data.get("upstream_id");
  }

  /**
   * Get the inline upstream configuration.
   *
   * @return map of upstream configuration, or null if not specified
   */
  @JsonIgnore
  @SuppressWarnings("unchecked")
  public Map<String, Object> getUpstream() {
    return (Map<String, Object>) data.get("upstream");
  }

  /**
   * Get the plugins configuration.
   *
   * @return map of plugin configurations, or null if not specified
   */
  @JsonIgnore
  @SuppressWarnings("unchecked")
  public Map<String, Object> getPlugins() {
    return (Map<String, Object>) data.get("plugins");
  }

  /**
   * Get a specific plugin configuration.
   *
   * @param pluginName the name of the plugin
   * @return the plugin configuration, or null if not present
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> getPlugin(String pluginName) {
    Map<String, Object> plugins = getPlugins();
    if (plugins == null) {
      return null;
    }
    return (Map<String, Object>) plugins.get(pluginName);
  }

  /**
   * Check if a specific plugin is configured.
   *
   * @param pluginName the name of the plugin
   * @return true if the plugin is configured
   */
  public boolean hasPlugin(String pluginName) {
    Map<String, Object> plugins = getPlugins();
    return plugins != null && plugins.containsKey(pluginName);
  }

  /**
   * Get the route priority.
   *
   * @return the priority value, or null if not specified
   */
  @JsonIgnore
  public Integer getPriority() {
    Object priority = data.get("priority");
    if (priority instanceof Number) {
      return ((Number) priority).intValue();
    }
    return null;
  }

  /**
   * Get the route status.
   *
   * @return 1 for enabled, 0 for disabled, or null if not specified
   */
  @JsonIgnore
  public Integer getStatus() {
    Object status = data.get("status");
    if (status instanceof Number) {
      return ((Number) status).intValue();
    }
    return null;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (RouteConfigValue) obj;
    return Objects.equals(this.data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(data);
  }

  @Override
  public String toString() {
    return "RouteConfigValue[" + "data=" + data + ']';
  }
}
