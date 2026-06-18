/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.geoserver;

import de.civitascore.configadapter.model.ConfigValue;
import java.util.Map;

/**
 * Marker interface for GeoServer REST API configuration values. Each permitted sub-type corresponds
 * to one GeoServer resource type and produces the GeoServer REST API JSON structure (including the
 * entity wrapper key) via {@link #toApiMap()}.
 *
 * <p>Registered sub-types (Jackson {@code resourceType} discriminator):
 *
 * <ul>
 *   <li>{@code geoserver-workspace} → {@link WorkspaceConfig}
 *   <li>{@code geoserver-datastore} → {@link DataStoreConfig}
 *   <li>{@code geoserver-featuretype} → {@link FeatureTypeConfig}
 *   <li>{@code geoserver-layer} → {@link LayerConfig}
 *   <li>{@code geoserver-style} → {@link StyleConfig}
 * </ul>
 */
public interface GeoServerConfigValue extends ConfigValue {

  String GEOSERVER_RESULT_TYPE = "de.civitascore.geo.processing.result";

  /**
   * Converts this value to a plain map matching the GeoServer REST API request body, including the
   * entity wrapper key (e.g. {@code {"workspace": {...}}}).
   *
   * @return an unmodifiable map ready to POST/PUT to the GeoServer REST API
   */
  Map<String, Object> toApiMap();
}
