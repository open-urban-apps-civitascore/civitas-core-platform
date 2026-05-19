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
 * Controlled vocabulary for {@link NamedApi#standard()} per ADR #1362. {@code CUSTOM} covers
 * non-standard APIs that still need a named route.
 */
public enum ApiStandard {
  WFS,
  WMS,
  STA,
  CUSTOM
}
