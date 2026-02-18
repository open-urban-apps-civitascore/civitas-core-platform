/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model.apisix;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Timeout configuration for an APISIX upstream. All values are in seconds and may be fractional
 * (e.g. {@code 0.5}).
 *
 * <p>Maps to the APISIX upstream {@code timeout} object:
 *
 * <pre>{@code
 * {
 *   "connect": 6,
 *   "send": 6,
 *   "read": 6
 * }
 * }</pre>
 *
 * @param connect connection timeout in seconds
 * @param send send timeout in seconds
 * @param read read timeout in seconds
 * @see <a href="https://apisix.apache.org/docs/apisix/admin-api/#upstream">APISIX Upstream API</a>
 */
public record UpstreamTimeout(
    @JsonProperty("connect") Number connect,
    @JsonProperty("send") Number send,
    @JsonProperty("read") Number read) {

  /**
   * Converts this timeout configuration to a plain map for the APISIX Admin API. Only non-null
   * fields are included.
   *
   * @return an unmodifiable map of timeout key-value pairs (may be empty)
   */
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (connect != null) map.put("connect", connect);
    if (send != null) map.put("send", send);
    if (read != null) map.put("read", read);
    return Collections.unmodifiableMap(map);
  }
}
