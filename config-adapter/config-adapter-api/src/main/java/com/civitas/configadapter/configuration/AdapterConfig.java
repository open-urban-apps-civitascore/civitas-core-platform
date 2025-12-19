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
package com.civitas.configadapter.configuration;

/**
 * Configuration interface for config adapters. Provides access to configuration properties with
 * support for environment variable overrides and default values.
 *
 * <p>Implementations should support reading from multiple configuration sources with the following
 * priority order:
 *
 * <ol>
 *   <li>Environment variables (property keys converted to uppercase with dots/dashes replaced by
 *       underscores)
 *   <li>Configuration file properties
 *   <li>Default values (when specified)
 * </ol>
 */
public interface AdapterConfig {

  /**
   * Gets a property value.
   *
   * @param key the property key
   * @return the property value, or null if not found
   */
  String getProperty(String key);

  /**
   * Gets a property value with a default fallback.
   *
   * @param key the property key
   * @param defaultValue the default value if not found
   * @return the property value, or defaultValue if not found
   */
  String getProperty(String key, String defaultValue);
}
