/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.common;

import com.zaxxer.hikari.HikariDataSource;
import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.flowable.bpmn.BpmnProcessDeployer;
import de.civitascore.configadapter.flowable.coded.CodedProcessDeployer;
import de.civitascore.configadapter.flowable.common.kafka.FlowableResultPublisher;
import de.civitascore.configadapter.flowable.common.kafka.FlowableTriggerConsumer;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import org.flowable.engine.ProcessEngine;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Top-level facade for the Flowable-based saga orchestrator. Manages the lifecycle of the Flowable
 * engine, Kafka trigger consumer, and result publisher. Drop-in replacement for {@code
 * DatasetSagaOrchestrator}.
 *
 * <p>Infrastructure creation (DataSource, Kafka clients) is delegated to {@link
 * FlowableInfrastructureFactory}. Process deployment is delegated to {@link BpmnProcessDeployer} or
 * {@link CodedProcessDeployer}.
 */
public class FlowableSagaOrchestrator implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(FlowableSagaOrchestrator.class);

  private static final String PROP_APPROACH = "flowable.approach";

  // Only the unconditional saga steps require a handler at startup. The pipeline adapter
  // (pipeline/nifi) is conditional — it runs only when hasPipelines==true — so it is resolved
  // lazily per step and a saga that has no pipelines runs fine without it. See SagaStepDelegate.
  private static final Set<String> REQUIRED_HANDLERS = Set.of("frost", "apisix");

  private final AdapterConfig config;
  private final Map<String, SagaCommandHandler> handlers;

  private ProcessEngine processEngine;
  private FlowableTriggerConsumer triggerConsumer;
  private FlowableResultPublisher resultPublisher;
  private HikariDataSource dataSource;

  public FlowableSagaOrchestrator(AdapterConfig config, Map<String, SagaCommandHandler> handlers) {
    this.config = config;
    this.handlers = Map.copyOf(handlers);
  }

  /** Initialize the Flowable engine and deploy processes. Cleans up on failure. */
  @SuppressWarnings("PMD.AvoidCatchingGenericException") // Cleanup must catch everything
  public void initialize() {
    LOG.info("Initializing FlowableSagaOrchestrator...");

    validateRequiredHandlers();

    try {
      dataSource = FlowableInfrastructureFactory.createDataSource(config);

      SagaHandlerRegistry registry = new SagaHandlerRegistry();
      handlers.values().forEach(registry::register);

      resultPublisher =
          new FlowableResultPublisher(FlowableInfrastructureFactory.createKafkaProducer(config));

      processEngine =
          FlowableEngineFactory.create(
              dataSource,
              Map.of("sagaHandlerRegistry", registry, "resultPublisher", resultPublisher));

      deployProcesses();

      triggerConsumer =
          new FlowableTriggerConsumer(
              processEngine.getRuntimeService(), processEngine.getHistoryService());

      DeploymentApproach approach =
          DeploymentApproach.fromConfig(config.getProperty(PROP_APPROACH));
      LOG.info(
          "FlowableSagaOrchestrator initialized (approach={}, handlers={})",
          approach,
          Encode.forJava(String.valueOf(handlers.keySet())));
    } catch (Exception e) {
      LOG.error("Initialization failed, cleaning up partially created resources", e);
      try {
        close();
      } catch (Exception closeEx) {
        e.addSuppressed(closeEx);
      }
      throw e;
    }
  }

  /** Start consuming saga triggers from Kafka. */
  public void start() {
    if (triggerConsumer == null) {
      throw new IllegalStateException("initialize() must be called before start()");
    }
    triggerConsumer.start(FlowableInfrastructureFactory.createKafkaConsumer(config));
    LOG.info("FlowableSagaOrchestrator started");
  }

  private void validateRequiredHandlers() {
    var missing = new ArrayList<String>();
    for (String required : REQUIRED_HANDLERS) {
      if (!handlers.containsKey(required)) {
        missing.add(required);
      }
    }
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "Required SagaCommandHandlers not registered: "
              + missing
              + ". BPMN processes require at least: "
              + REQUIRED_HANDLERS);
    }
  }

  /** Stop the orchestrator and release resources. Idempotent — safe to call multiple times. */
  @Override
  public void close() {
    LOG.info("Shutting down FlowableSagaOrchestrator...");
    closeQuietly(
        "triggerConsumer",
        () -> {
          if (triggerConsumer != null) {
            triggerConsumer.stop();
          }
        });
    triggerConsumer = null;
    closeQuietly(
        "resultPublisher",
        () -> {
          if (resultPublisher != null) {
            resultPublisher.close();
          }
        });
    resultPublisher = null;
    closeQuietly(
        "processEngine",
        () -> {
          if (processEngine != null) {
            processEngine.close();
          }
        });
    processEngine = null;
    closeQuietly(
        "dataSource",
        () -> {
          if (dataSource != null) {
            dataSource.close();
          }
        });
    dataSource = null;
    LOG.info("FlowableSagaOrchestrator shut down");
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException") // Intentional: best-effort cleanup
  private void closeQuietly(String name, Runnable closeAction) {
    try {
      closeAction.run();
    } catch (Exception e) {
      LOG.error("Error closing {}", name, e);
    }
  }

  private void deployProcesses() {
    DeploymentApproach approach = DeploymentApproach.fromConfig(config.getProperty(PROP_APPROACH));
    if (approach == DeploymentApproach.CODED) {
      CodedProcessDeployer.deploy(processEngine.getRepositoryService());
    } else {
      BpmnProcessDeployer.deploy(processEngine.getRepositoryService());
    }
  }
}
