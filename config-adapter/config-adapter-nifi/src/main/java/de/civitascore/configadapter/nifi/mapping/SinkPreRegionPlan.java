/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.mapping;

/**
 * A plan-time handoff from the transform compilation to the sink's build region — data the sink's
 * pre-region processors need but that only the compilation can produce (today: the STA envelope
 * rebuild for a mapped FROST sink). The slot is sink-neutral: each sink validates in its build half
 * that it received the variant it can consume, and rejects any other — the builder and the build
 * spec carry the plan without knowing any sink's specifics.
 */
public sealed interface SinkPreRegionPlan permits FrostEntityPlan {}
