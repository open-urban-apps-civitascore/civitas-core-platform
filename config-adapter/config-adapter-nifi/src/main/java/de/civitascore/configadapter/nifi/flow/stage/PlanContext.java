/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Output collector for the plan-time half of a stage: the property maps the planner assembles into
 * the build spec, plus the sensitive properties pushed post-upload over REST.
 *
 * <p>The sensitive map deliberately never reaches the {@link BuildContext} — secrets must not enter
 * the snapshot; the REST client matches them by controller-service friendly name after upload.
 *
 * <p>All maps preserve insertion order: property application order is part of the byte-stable
 * snapshot contract.
 */
public final class PlanContext {

  private final Map<String, String> sourceProperties = new LinkedHashMap<>();
  private final Map<String, String> sinkProperties = new LinkedHashMap<>();
  private final Map<String, Map<String, String>> controllerServiceProperties =
      new LinkedHashMap<>();
  private final Map<String, Map<String, String>> sensitive = new LinkedHashMap<>();

  public void putSourceProperty(String key, String value) {
    sourceProperties.put(key, value);
  }

  public void putSinkProperty(String key, String value) {
    sinkProperties.put(key, value);
  }

  public void putControllerServiceProperty(String friendlyName, String key, String value) {
    controllerServiceProperties
        .computeIfAbsent(friendlyName, k -> new LinkedHashMap<>())
        .put(key, value);
  }

  public void putSensitive(String componentFriendlyName, String key, String value) {
    sensitive.computeIfAbsent(componentFriendlyName, k -> new LinkedHashMap<>()).put(key, value);
  }

  public Map<String, String> sourceProperties() {
    return sourceProperties;
  }

  public Map<String, String> sinkProperties() {
    return sinkProperties;
  }

  public Map<String, Map<String, String>> controllerServiceProperties() {
    return controllerServiceProperties;
  }

  public Map<String, Map<String, String>> sensitive() {
    return sensitive;
  }
}
