/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.adapter;

/**
 * Handler for saga commands dispatched by the orchestrator. Each adapter module provides an
 * implementation that knows how to execute forward operations and compensations for its domain.
 *
 * <p>Discovered at runtime via {@link java.util.ServiceLoader}, analogous to {@link ConfigAdapter}.
 *
 * <p>Implementations must be stateless and thread-safe — a single instance handles all saga
 * commands for the adapter.
 */
public interface SagaCommandHandler {

  /**
   * Returns the adapter name this handler is responsible for (e.g. {@code "frost"}, {@code
   * "apisix"}, {@code "redpanda"}). Must match the {@code adapter} field in {@link
   * SagaCommandMessage}.
   */
  String adapter();

  /**
   * Handles a saga command (execute or compensate) and returns the result. Implementations should
   * catch all exceptions internally and return an appropriate {@link SagaCommandResult#failure} or
   * {@link SagaCommandResult#compensationFailure} — the consumer will publish whatever is returned.
   *
   * @param command the incoming saga command
   * @return the result to publish back to the orchestrator
   */
  SagaCommandResult handle(SagaCommandMessage command);
}
