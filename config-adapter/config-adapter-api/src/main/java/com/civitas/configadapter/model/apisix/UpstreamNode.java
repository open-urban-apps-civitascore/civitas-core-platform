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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Represents a single upstream node in the APISIX array format. This is one of two formats APISIX
 * supports for defining upstream backend nodes.
 *
 * <p>Array format example:
 *
 * <pre>{@code
 * {
 *   "host": "backend1.example.com",
 *   "port": 8080,
 *   "weight": 1,
 *   "priority": 0,
 *   "metadata": {"idc": "dc1"}
 * }
 * }</pre>
 *
 * @param host the backend host address (IP or hostname)
 * @param port the backend port (defaults to 80 in APISIX if omitted)
 * @param weight the load balancing weight for this node
 * @param priority the node priority (optional; lower value = higher priority, default 0 in APISIX)
 * @param metadata arbitrary metadata key-value pairs (optional)
 * @see <a href="https://apisix.apache.org/docs/apisix/admin-api/#upstream">APISIX Upstream API</a>
 * @see UpstreamNodes
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpstreamNode(
    String host, Integer port, Integer weight, Integer priority, Map<String, Object> metadata) {}
