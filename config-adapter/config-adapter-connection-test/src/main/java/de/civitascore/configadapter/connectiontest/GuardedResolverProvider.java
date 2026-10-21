/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connectiontest;

import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;

/**
 * Installs {@link GuardedResolver} as the JVM's resolver, on top of the built-in one. The JVM finds
 * it through {@code META-INF/services} at the first lookup.
 */
public final class GuardedResolverProvider extends InetAddressResolverProvider {

  @Override
  public InetAddressResolver get(Configuration configuration) {
    return new GuardedResolver(configuration.builtinResolver());
  }

  @Override
  public String name() {
    return "connection-test-destination-guard";
  }
}
