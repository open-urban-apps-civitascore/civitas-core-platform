/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.dataset;

import java.util.regex.Pattern;

/**
 * Regex for resource names that must be safe as a single REST URL path segment without
 * percent-encoding: one or more of {@code A-Z}, {@code a-z}, {@code 0-9}, {@code _}, or {@code -}.
 *
 * <p>Lives in {@code config-adapter-api} as a single source of truth: producers use {@link
 * #PATTERN} on their {@code @Pattern} input-validation annotations so unsafe names are rejected up
 * front, and consumers use {@link #COMPILED_PATTERN} as the runtime guard before a name is dropped
 * into a URL.
 */
public final class SafeNames {

  /**
   * Regex matching a name that is safe as a single REST URL path segment: one or more of {@code
   * A-Z}, {@code a-z}, {@code 0-9}, {@code _}, or {@code -}.
   */
  public static final String PATTERN = "[A-Za-z0-9_-]+";

  /** Compiled form of {@link #PATTERN}. */
  public static final Pattern COMPILED_PATTERN = Pattern.compile(PATTERN);

  private SafeNames() {}
}
