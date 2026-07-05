/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow;

/**
 * Saga-level values a sink stage may need when parsing its catalog entry — values that are not part
 * of the datasink payload itself but of the surrounding saga run.
 *
 * @param frostProjectId the dataset's FROST project id (result of the saga's create-project step),
 *     or null when the saga carries none
 */
public record SinkResolutionContext(String frostProjectId) {}
