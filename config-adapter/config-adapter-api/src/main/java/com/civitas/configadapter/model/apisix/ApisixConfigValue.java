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

import com.civitas.configadapter.model.ConfigValue;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration value for APISIX API Gateway resources such as upstreams, routes, and services.
 *
 * <p>Common APISIX upstream configuration fields:
 *
 * <ul>
 *   <li>type - Load balancing algorithm (roundrobin, chash, ewma, least_conn)
 *   <li>nodes - Map of backend nodes with weights (e.g., {"host:port": weight})
 *   <li>timeout - Connection, send, and read timeouts
 *   <li>checks - Active and passive health check configuration
 *   <li>scheme - Protocol scheme (http, https, grpc, grpcs)
 *   <li>tls - TLS configuration for upstream connections
 * </ul>
 *
 * <p>Example upstream configuration:
 *
 * <pre>{@code
 * {
 *   "type": "roundrobin",
 *   "nodes": {
 *     "backend1:8080": 1,
 *     "backend2:8080": 2
 *   },
 *   "timeout": {
 *     "connect": 6,
 *     "send": 6,
 *     "read": 6
 *   }
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ApisixConfigValue implements ConfigValue {

  private final Map<String, Object> data;

  @JsonCreator
  public ApisixConfigValue() {
    this.data = new LinkedHashMap<>();
  }

  public ApisixConfigValue(Map<String, Object> data) {
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
   * Get the load balancing type (roundrobin, chash, ewma, least_conn).
   *
   * @return the load balancing type, or null if not specified
   */
  public String getType() {
    return (String) data.get("type");
  }

  /**
   * Get the backend nodes configuration.
   *
   * @return map of node addresses to weights, or null if not specified
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> getNodes() {
    return (Map<String, Object>) data.get("nodes");
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (ApisixConfigValue) obj;
    return Objects.equals(this.data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(data);
  }

  @Override
  public String toString() {
    return "ApisixConfigValue[" + "data=" + data + ']';
  }
}
