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
 * The compiled unit of one on-path transform node, carried in flow order in the build spec. The
 * flow builder materializes each unit via an exhaustive switch over this sealed hierarchy, so a new
 * transform kind fails compilation until the builder handles it.
 *
 * <p>Deterministic-id invariant: a unit's chain index counts positions <em>within its own kind</em>
 * (the k-th mapping among the mappings), never globally across all transforms. A globally counted
 * index would shift every downstream processor id when a unit of another kind is inserted
 * mid-chain, breaking redeploy stability for flows that did not change.
 */
public sealed interface CompiledTransform permits CompiledMapping {}
