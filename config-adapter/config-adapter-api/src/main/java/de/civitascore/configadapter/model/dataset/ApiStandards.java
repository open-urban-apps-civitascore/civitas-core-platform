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
 * Controlled vocabulary for {@link NamedApi#standard()}. String constants — not a Java enum — so
 * the vocabulary can grow without lockstep changes in portal-model, portal-backend, and
 * config-adapter-api. {@code CUSTOM} stays free-form for non-standard APIs.
 *
 * <p>Use {@link #PATTERN} as a {@code @Pattern} regex on input DTOs to reject unknown values at the
 * API boundary; reference the named constants in business logic to avoid string literals.
 */
public final class ApiStandards {

  public static final String OWS = "OWS";
  public static final String STA = "STA";
  public static final String CUSTOM = "CUSTOM";

  /** {@code @Pattern} regex for {@link #OWS}, {@link #STA}, or {@link #CUSTOM}. */
  public static final String PATTERN = "^(OWS|STA|CUSTOM)$";

  private ApiStandards() {}
}
