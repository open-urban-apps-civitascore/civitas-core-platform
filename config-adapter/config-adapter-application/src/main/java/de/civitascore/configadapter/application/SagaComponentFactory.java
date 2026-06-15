/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.application;

import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.flowable.common.FlowableSagaOrchestrator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates and initializes the Flowable saga orchestration components during application bootstrap.
 */
class SagaComponentFactory {

  private static final Logger logger = LoggerFactory.getLogger(SagaComponentFactory.class);

  SagaComponents create(AppConfig config) {
    String engine = config.getProperty("orchestrator.engine", "flowable");
    if (!"flowable".equals(engine)) {
      throw new IllegalArgumentException(
          "Unsupported orchestrator.engine '"
              + engine
              + "' — the legacy custom orchestrator was removed; only 'flowable' is supported.");
    }
    logger.info("Using Flowable saga orchestrator");
    return createFlowableComponents(config);
  }

  private SagaComponents createFlowableComponents(AppConfig config) {
    Map<String, SagaCommandHandler> handlers = discoverSagaHandlers(config);
    FlowableSagaOrchestrator flowable = new FlowableSagaOrchestrator(config, handlers);
    try {
      flowable.initialize();
    } catch (Exception e) {
      try {
        flowable.close();
      } catch (Exception closeEx) {
        e.addSuppressed(closeEx);
      }
      for (SagaCommandHandler handler : handlers.values()) {
        try {
          handler.close();
        } catch (Exception closeEx) {
          e.addSuppressed(closeEx);
        }
      }
      // Surface the ACTUAL cause in the message — not just a blanket "check flowable.jdbc".
      // A dropped required handler (e.g. apisix missing apisix.api.host) fails here via
      // validateRequiredHandlers BEFORE the datasource is even built, so pointing operators at the
      // database would send them down the wrong path (issue #1368 debugging trap).
      throw new IllegalStateException(
          "Saga orchestrator initialization failed: "
              + e.getMessage()
              + " — if this is a database connectivity error, verify the flowable.jdbc.*"
              + " configuration.",
          e);
    }
    logger.info("FlowableSagaOrchestrator initialized successfully");
    return SagaComponents.flowable(flowable);
  }

  private Map<String, SagaCommandHandler> discoverSagaHandlers(AdapterConfig config) {
    Map<String, SagaCommandHandler> handlers = new HashMap<>();
    List<String> dropped = new ArrayList<>();
    ServiceLoader<SagaCommandHandler> loader = ServiceLoader.load(SagaCommandHandler.class);

    for (SagaCommandHandler handler : loader) {
      try {
        handler.initialize(config);
        handlers.put(handler.adapter(), handler);
        logger.info(
            "Discovered SagaCommandHandler: {} ({})",
            Encode.forJava(handler.adapter()),
            handler.getClass().getSimpleName());
      } catch (Exception e) {
        dropped.add(handler.getClass().getSimpleName());
        logger.error(
            "Failed to initialize SagaCommandHandler {}: {}",
            handler.getClass().getSimpleName(),
            Encode.forJava(e.getMessage()),
            e);
      }
    }
    // Aggregate boot summary so a degraded surface is operator-visible at a glance. Required
    // handlers (frost/apisix) are enforced fail-fast by FlowableSagaOrchestrator; an OPTIONAL
    // handler (e.g. the pipeline adapter) that fails to initialize is dropped here and would
    // otherwise only surface as a runtime saga-step failure — so call it out explicitly.
    if (!dropped.isEmpty()) {
      logger.warn(
          "Saga handler discovery completed with {} handler(s) dropped due to init failure: {}."
              + " Registered handlers: {}. Datasets needing a dropped adapter will fail at runtime.",
          dropped.size(),
          Encode.forJava(String.valueOf(dropped)),
          Encode.forJava(String.valueOf(handlers.keySet())));
    }
    return handlers;
  }
}
