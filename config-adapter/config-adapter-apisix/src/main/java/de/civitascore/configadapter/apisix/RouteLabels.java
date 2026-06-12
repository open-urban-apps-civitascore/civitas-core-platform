/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.apisix;

import java.util.HashMap;
import java.util.Map;

/**
 * Read/write access to the {@code labels} block of an APISIX route body. APISIX labels are plain
 * string key-value pairs; the adapter uses them to track its own managed state on a route (e.g.
 * {@link RouteAuthConfigurer#MANAGED_AUTH_HEADER_LABEL}). All writes defensively copy the map
 * (deserialisers may hand out immutable views) and an emptied block is dropped entirely instead of
 * being written as {@code labels: {}}.
 */
final class RouteLabels {

  private static final String LABELS_KEY = "labels";

  private RouteLabels() {}

  @SuppressWarnings("unchecked")
  static String read(Map<String, Object> route, String key) {
    Map<String, Object> labels = (Map<String, Object>) route.get(LABELS_KEY);
    if (labels == null) {
      return null;
    }
    Object value = labels.get(key);
    return value instanceof String s && !s.isBlank() ? s : null;
  }

  @SuppressWarnings("unchecked")
  static void put(Map<String, Object> route, String key, String value) {
    Map<String, Object> labels = new HashMap<>();
    if (route.get(LABELS_KEY) instanceof Map<?, ?> existing) {
      labels.putAll((Map<String, Object>) existing);
    }
    labels.put(key, value);
    route.put(LABELS_KEY, labels);
  }

  @SuppressWarnings("unchecked")
  static void remove(Map<String, Object> route, String key) {
    if (!(route.get(LABELS_KEY) instanceof Map<?, ?> existing)) {
      return;
    }
    Map<String, Object> labels = new HashMap<>((Map<String, Object>) existing);
    labels.remove(key);
    if (labels.isEmpty()) {
      route.remove(LABELS_KEY);
    } else {
      route.put(LABELS_KEY, labels);
    }
  }
}
