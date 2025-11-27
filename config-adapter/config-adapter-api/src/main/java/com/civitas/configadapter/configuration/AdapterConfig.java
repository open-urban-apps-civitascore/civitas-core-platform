/**
 * Copyright (c) 2012 - 2025 Data In Motion and others. All rights reserved.
 *
 * <p>This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * <p>SPDX-License-Identifier: EPL-2.0
 *
 * <p>Contributors: Data In Motion - initial API and implementation
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
