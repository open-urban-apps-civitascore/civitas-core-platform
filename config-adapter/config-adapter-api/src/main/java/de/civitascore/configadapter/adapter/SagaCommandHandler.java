/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.adapter;

import de.civitascore.configadapter.configuration.AdapterConfig;
import java.util.Map;

/**
 * Handler for saga commands dispatched by the orchestrator. Each adapter module provides an
 * implementation that knows how to execute forward operations and compensations for its domain.
 *
 * <p>Discovered at runtime via {@link java.util.ServiceLoader}, analogous to {@link ConfigAdapter}.
 * Lifecycle: ServiceLoader creates the instance (no-arg constructor), then {@link
 * #initialize(AdapterConfig)} is called before any commands are dispatched.
 *
 * <p>Implementations must be thread-safe — a single instance handles all saga commands for the
 * adapter.
 */
public interface SagaCommandHandler extends AutoCloseable {

  /**
   * Returns the adapter name this handler is responsible for (e.g. {@code "frost"}, {@code
   * "apisix"}, {@code "nifi"}). Must match the {@code adapter} field in {@link SagaCommandMessage}.
   */
  String adapter();

  /**
   * Initializes the handler with adapter configuration. Called once after ServiceLoader discovery,
   * before any commands are dispatched. Implementations should read their connection parameters
   * (URLs, API keys, etc.) from the config using the adapter name as prefix.
   *
   * @param config the adapter configuration
   */
  void initialize(AdapterConfig config);

  /**
   * Handles a saga command (execute or compensate) and returns the result. Implementations should
   * catch all exceptions internally and return an appropriate {@link SagaCommandResult#failure} or
   * {@link SagaCommandResult#compensationFailure} — the consumer will publish whatever is returned.
   *
   * @param command the incoming saga command
   * @return the result to publish back to the orchestrator
   */
  SagaCommandResult handle(SagaCommandMessage command);

  /**
   * Declares incoming-payload field renames this adapter understands. Each entry maps a source
   * field name to the target field name this adapter expects (e.g. {@code "baseUrl" →
   * "upstreamUrl"} for APISIX, where FROST upstream produced {@code baseUrl}). Lets adapters
   * declare their own upstream-field naming differences without leaking adapter-specific knowledge
   * into the orchestrator. Consumers must not overwrite existing target keys.
   *
   * @return map from source field name to target field name; default: empty
   */
  default Map<String, String> fieldAliases() {
    return Map.of();
  }

  /** Injects the application-owned publisher for asynchronous pipeline runtime events. */
  default void setPipelineStatusPublisher(PipelineStatusPublisher publisher) {}

  @Override
  default void close() {}
}
