/**
 * Copyright (c) 2012 - 2025 Data In Motion and others.
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     Data In Motion - initial API and implementation
 */
package com.civitas.configadapter.util;

import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.function.Predicate;

/**
 * Helper class for {@link ServiceLoader}
 * @author Mark Hoffmann
 * @since 26.11.2025
 */
public class ServiceLoaderUtils {
	
	/**
	 * Returns an {@link Optional} instance out of the {@link ServiceLoader} that matches the provided predicate
	 * @param <T> the type of the service loader interface
	 * @param serviceClass the class of this type
	 * @param filter the predicate to match the right instance
	 * @return the optional containing the instance or an empty {@link Optional}
	 */
	public static <T> Optional<T> getInstanceByFilter(Class<T> serviceClass, Predicate<T> filter) {
	    Objects.requireNonNull(serviceClass);
	    Objects.requireNonNull(filter);
	    ServiceLoader<T> loader = ServiceLoader.load(serviceClass);
	    return loader.stream().map(Provider::get).filter(filter).findFirst();
	  }

}
