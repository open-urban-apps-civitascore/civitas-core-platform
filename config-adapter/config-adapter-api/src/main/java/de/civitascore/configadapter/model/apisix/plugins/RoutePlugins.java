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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Container for APISIX route plugins. Provides typed access to implemented plugins (proxy-rewrite,
 * response-rewrite, serverless-pre-function, serverless-post-function). All other plugins
 * (openid-connect, prometheus, loki, etc.) are captured in {@code additionalPlugins}.
 *
 * <p>This class intentionally does not extend {@link
 * de.civitascore.configadapter.model.AbstractApiModel}. While {@code AbstractApiModel} captures
 * unknown JSON properties as generic key-value pairs, this class manages {@code additionalPlugins}
 * with different semantics: each entry is a complete plugin configuration object keyed by plugin
 * name.
 */
public final class RoutePlugins {

  private static final Logger logger = LoggerFactory.getLogger(RoutePlugins.class);

  @JsonProperty("proxy-rewrite")
  private ProxyRewritePlugin proxyRewrite;

  @JsonProperty("response-rewrite")
  private ResponseRewritePlugin responseRewrite;

  @JsonProperty("serverless-pre-function")
  private ServerlessFunctionPlugin serverlessPreFunction;

  @JsonProperty("serverless-post-function")
  private ServerlessFunctionPlugin serverlessPostFunction;

  private final Map<String, Object> additionalPlugins = new LinkedHashMap<>();

  public RoutePlugins() {}

  public ProxyRewritePlugin getProxyRewrite() {
    return proxyRewrite;
  }

  public void setProxyRewrite(ProxyRewritePlugin proxyRewrite) {
    this.proxyRewrite = proxyRewrite;
  }

  public ResponseRewritePlugin getResponseRewrite() {
    return responseRewrite;
  }

  public void setResponseRewrite(ResponseRewritePlugin responseRewrite) {
    this.responseRewrite = responseRewrite;
  }

  public ServerlessFunctionPlugin getServerlessPreFunction() {
    return serverlessPreFunction;
  }

  public void setServerlessPreFunction(ServerlessFunctionPlugin serverlessPreFunction) {
    this.serverlessPreFunction = serverlessPreFunction;
  }

  public ServerlessFunctionPlugin getServerlessPostFunction() {
    return serverlessPostFunction;
  }

  public void setServerlessPostFunction(ServerlessFunctionPlugin serverlessPostFunction) {
    this.serverlessPostFunction = serverlessPostFunction;
  }

  @JsonAnyGetter
  public Map<String, Object> getAdditionalPlugins() {
    return Collections.unmodifiableMap(additionalPlugins);
  }

  @JsonAnySetter
  public void handleUnknownPlugin(String key, Object value) {
    logger.debug(
        "Untyped plugin '{}' in {} — captured in additionalPlugins",
        Encode.forJava(key),
        getClass().getSimpleName());
    additionalPlugins.put(key, value);
  }

  /**
   * Check if a specific plugin is configured (typed or untyped).
   *
   * @param pluginName the name of the plugin
   * @return true if the plugin is configured
   */
  public boolean hasPlugin(String pluginName) {
    return switch (pluginName) {
      case "proxy-rewrite" -> proxyRewrite != null;
      case "response-rewrite" -> responseRewrite != null;
      case "serverless-pre-function" -> serverlessPreFunction != null;
      case "serverless-post-function" -> serverlessPostFunction != null;
      default -> additionalPlugins.containsKey(pluginName);
    };
  }

  /**
   * Get an untyped plugin configuration by name (for plugins not yet typed).
   *
   * @param pluginName the name of the plugin
   * @return the plugin configuration, or null if not present
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> getAdditionalPlugin(String pluginName) {
    Object value = additionalPlugins.get(pluginName);
    if (value instanceof Map) {
      return (Map<String, Object>) value;
    }
    if (value != null) {
      logger.debug(
          "Plugin '{}' has value of type {} instead of Map — returning null",
          Encode.forJava(pluginName),
          value.getClass().getSimpleName());
    }
    return null;
  }

  /**
   * Converts typed fields and additional properties to a plain map for the APISIX Admin API.
   *
   * @return an unmodifiable map of configuration key-value pairs
   */
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (proxyRewrite != null) map.put("proxy-rewrite", proxyRewrite.toApiMap());
    if (responseRewrite != null) map.put("response-rewrite", responseRewrite.toApiMap());
    if (serverlessPreFunction != null)
      map.put("serverless-pre-function", serverlessPreFunction.toApiMap());
    if (serverlessPostFunction != null)
      map.put("serverless-post-function", serverlessPostFunction.toApiMap());
    additionalPlugins.forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (RoutePlugins) obj;
    return Objects.equals(this.proxyRewrite, that.proxyRewrite)
        && Objects.equals(this.responseRewrite, that.responseRewrite)
        && Objects.equals(this.serverlessPreFunction, that.serverlessPreFunction)
        && Objects.equals(this.serverlessPostFunction, that.serverlessPostFunction)
        && Objects.equals(this.additionalPlugins, that.additionalPlugins);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        proxyRewrite,
        responseRewrite,
        serverlessPreFunction,
        serverlessPostFunction,
        additionalPlugins);
  }

  @Override
  public String toString() {
    return "RoutePlugins["
        + "proxyRewrite="
        + proxyRewrite
        + ", responseRewrite="
        + responseRewrite
        + ", serverlessPreFunction="
        + serverlessPreFunction
        + ", serverlessPostFunction="
        + serverlessPostFunction
        + ", additionalPlugins="
        + additionalPlugins.keySet()
        + ']';
  }
}
