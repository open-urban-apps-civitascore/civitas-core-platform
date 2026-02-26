/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.apisix;

import de.civitascore.configadapter.model.AbstractApiModel;
import de.civitascore.configadapter.model.ConfigValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration value for APISIX API Gateway upstream resources. Also used as a fallback for route
 * configurations that don't use the full RouteConfigValue structure.
 *
 * <p>Common APISIX upstream configuration fields:
 *
 * <ul>
 *   <li>type - Load balancing algorithm (roundrobin, chash, ewma, least_conn)
 *   <li>nodes - Backend nodes as map ({"host:port": weight}) or array ([{host, port, weight}])
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
public final class ApisixConfigValue extends AbstractApiModel implements ConfigValue {

  private String type;
  private UpstreamNodes nodes;
  private UpstreamTimeout timeout;
  private HealthCheck checks;
  private String scheme;
  private UpstreamTls tls;

  public ApisixConfigValue() {}

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public UpstreamNodes getNodes() {
    return nodes;
  }

  public void setNodes(UpstreamNodes nodes) {
    this.nodes = nodes;
  }

  public UpstreamTimeout getTimeout() {
    return timeout;
  }

  public void setTimeout(UpstreamTimeout timeout) {
    this.timeout = timeout;
  }

  public HealthCheck getChecks() {
    return checks;
  }

  public void setChecks(HealthCheck checks) {
    this.checks = checks;
  }

  public String getScheme() {
    return scheme;
  }

  public void setScheme(String scheme) {
    this.scheme = scheme;
  }

  public UpstreamTls getTls() {
    return tls;
  }

  public void setTls(UpstreamTls tls) {
    this.tls = tls;
  }

  /**
   * Converts typed fields and additional properties to a plain map for the APISIX Admin API.
   *
   * @return an unmodifiable map of configuration key-value pairs
   */
  @Override
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (type != null) map.put("type", type);
    if (nodes != null) map.put("nodes", nodes.toApiValue());
    if (timeout != null) {
      Map<String, Object> timeoutMap = timeout.toApiMap();
      if (!timeoutMap.isEmpty()) map.put("timeout", timeoutMap);
    }
    if (checks != null) map.put("checks", checks.toApiMap());
    if (scheme != null) map.put("scheme", scheme);
    if (tls != null) map.put("tls", tls.toApiMap());
    additionalProperties().forEach(map::putIfAbsent);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (ApisixConfigValue) obj;
    return Objects.equals(this.type, that.type)
        && Objects.equals(this.nodes, that.nodes)
        && Objects.equals(this.timeout, that.timeout)
        && Objects.equals(this.checks, that.checks)
        && Objects.equals(this.scheme, that.scheme)
        && Objects.equals(this.tls, that.tls)
        && Objects.equals(this.additionalProperties(), that.additionalProperties());
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, nodes, timeout, checks, scheme, tls, additionalProperties());
  }

  @Override
  public String toString() {
    return "ApisixConfigValue["
        + "type="
        + type
        + ", nodes="
        + nodes
        + ", scheme="
        + scheme
        + ", timeout="
        + timeout
        + ", checks="
        + checks
        + ", tls="
        + tls
        + ", additionalProperties="
        + additionalProperties().keySet()
        + ']';
  }
}
