/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2012 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.util;

import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.function.Predicate;

/**
 * Helper class for {@link ServiceLoader}
 *
 * @author Mark Hoffmann
 * @since 26.11.2025
 */
public class ServiceLoaderUtils {

  /**
   * Returns an {@link Optional} instance from the {@link ServiceLoader} that matches the provided
   * predicate. Uses the thread context class loader to discover service implementations.
   *
   * @param <T> the type of the service loader interface
   * @param serviceClass the class of the service interface, must not be null
   * @param filter the predicate to match the desired instance, must not be null
   * @return an {@link Optional} containing the first matching instance, or an empty {@link
   *     Optional} if no service matches the filter
   * @throws NullPointerException if serviceClass or filter is null
   */
  public static <T> Optional<T> getInstanceByFilter(Class<T> serviceClass, Predicate<T> filter) {
    Objects.requireNonNull(serviceClass);
    Objects.requireNonNull(filter);
    ServiceLoader<T> loader = ServiceLoader.load(serviceClass);
    return loader.stream().map(Provider::get).filter(filter).findFirst();
  }
}
