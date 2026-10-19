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

/**
 * The data structure's JSON Schema does not identify a single target class: it declares no class
 * definitions at all, or several without a designated root, so {@link
 * DataStructureSchema#resolveDefinition(java.util.Map)} cannot pick the one class a sink maps to.
 *
 * <p>Distinct from a plain {@link IllegalArgumentException} (a structurally broken schema — a
 * dangling {@code $ref}, a mis-shaped node) because the remedy differs: this defect is fixed by the
 * modeller — designate a root element in the data structure (delivered as its top-level {@code
 * $ref}) or model exactly one class — so sink adapters surface that actionable message instead of a
 * generic validation failure.
 */
public class UnresolvableDataStructureException extends IllegalArgumentException {

  /** serialVersionUID */
  private static final long serialVersionUID = 1L;

  public UnresolvableDataStructureException(String message) {
    super(message);
  }
}
