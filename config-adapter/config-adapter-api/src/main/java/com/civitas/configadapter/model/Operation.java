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
package com.civitas.configadapter.model;

/** Represents the type of configuration operation to be performed. */
public enum Operation {
  /** Create a new resource */
  CREATE,

  /** Update an existing resource */
  UPDATE,

  /** Delete an existing resource */
  DELETE
}
