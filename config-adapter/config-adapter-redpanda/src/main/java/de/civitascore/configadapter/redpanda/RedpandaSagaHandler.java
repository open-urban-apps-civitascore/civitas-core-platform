/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.redpanda;

import de.civitascore.configadapter.adapter.AbstractSagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.crypto.CredentialDecryptor;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import jakarta.ws.rs.client.Client;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.owasp.encoder.Encode;

/**
 * Saga command handler for RedPanda Connect pipeline operations. Handles multi-pipeline operations
 * dispatched by the saga orchestrator:
 *
 * <ul>
 *   <li>{@code DEPLOY_PIPELINES} — Create pipelines for a new dataset
 *   <li>{@code UPDATE_PIPELINES} — Update pipelines (ADD/UPDATE/DELETE actions per pipeline)
 *   <li>{@code DELETE_PIPELINES} — Delete all pipelines for a dataset
 *   <li>{@code RESTORE_PIPELINES} — Restore pipelines to previous state (update compensation)
 * </ul>
 *
 * <p>Compensation operations: {@code DELETE_PIPELINES} compensates {@code DEPLOY_PIPELINES}, {@code
 * RESTORE_PIPELINES} compensates {@code UPDATE_PIPELINES}.
 */
public class RedpandaSagaHandler extends AbstractSagaCommandHandler {

  private static final String ADAPTER_NAME = "redpanda";
  private static final String DEFAULT_URL = "http://localhost:4195";
  private static final String MASTER_KEY_ENV = "CIVITAS_MASTER_KEY";
  private static final String MASTER_SALT_ENV = "CIVITAS_MASTER_SALT";

  private RedpandaConnectClient redpandaClient;

  /** No-arg constructor for ServiceLoader discovery. Call {@link #initialize} before use. */
  public RedpandaSagaHandler() {
    super(ADAPTER_NAME);
  }

  @Override
  protected void doInitialize(AdapterConfig config) {
    String baseUrl = getProperty("url", DEFAULT_URL);
    byte[] masterKey = CredentialDecryptor.loadKeyFromEnv(MASTER_KEY_ENV);
    byte[] salt = CredentialDecryptor.loadKeyFromEnv(MASTER_SALT_ENV);

    if (masterKey.length == 0 || salt.length == 0) {
      log.warn(
          "{} or {} not set — encrypted credentials cannot be decrypted",
          MASTER_KEY_ENV,
          MASTER_SALT_ENV);
    }

    if (this.redpandaClient == null) {
      jakarta.ws.rs.client.Client jaxrsClient = client();
      this.redpandaClient =
          jaxrsClient != null
              ? new RedpandaConnectClient(baseUrl, masterKey, salt, jaxrsClient)
              : new RedpandaConnectClient(baseUrl, masterKey, salt);
    }

    log.info("RedpandaSagaHandler initialized for: {}", Encode.forJava(baseUrl));
  }

  void setTestClient(Client client) {
    super.setClient(client);
  }

  void setTestRedpandaClient(RedpandaConnectClient client) {
    this.redpandaClient = client;
  }

  @Override
  protected SagaCommandResult doHandle(SagaCommandMessage command) {
    return switch (command.operation()) {
      case "DEPLOY_PIPELINES" -> handleDeployPipelines(command);
      case "UPDATE_PIPELINES" -> handleUpdatePipelines(command);
      case "DELETE_PIPELINES" -> handleDeletePipelines(command);
      case "RESTORE_PIPELINES" -> handleRestorePipelines(command);
      default -> unknownOperation(command);
    };
  }

  private SagaCommandResult handleDeployPipelines(SagaCommandMessage command) {
    List<Map<String, Object>> dataPipelines = extractPipelineList(command, "dataPipelines");
    List<String> deployedIds = new ArrayList<>();

    try {
      for (Map<String, Object> pipeline : dataPipelines) {
        String id = requirePipelineField(pipeline, "id");
        Map<String, Object> data = optionalPipelineMapField(pipeline, "data");

        redpandaClient.createPipeline(id, data);
        deployedIds.add(id);
      }

      Map<String, Object> resultData = Map.of("pipelineIds", List.copyOf(deployedIds));
      Map<String, Object> compensationData = Map.of("pipelineIds", List.copyOf(deployedIds));

      log.info(
          "Deployed {} pipelines for saga {}",
          deployedIds.size(),
          Encode.forJava(command.sagaId()));

      return SagaCommandResult.success(
          command.sagaId(), command.stepId(), resultData, compensationData);

    } catch (FatalAdapterException | RetryableAdapterException e) {
      log.error(
          "DEPLOY_PIPELINES failed for saga {}: {}",
          Encode.forJava(command.sagaId()),
          Encode.forJava(e.getMessage()));
      return SagaCommandResult.failure(
          command.sagaId(), command.stepId(), "DEPLOY_PIPELINES failed: " + e.getMessage());
    }
  }

  private SagaCommandResult handleUpdatePipelines(SagaCommandMessage command) {
    List<Map<String, Object>> dataPipelines = extractPipelineList(command, "dataPipelines");
    List<String> processedIds = new ArrayList<>();
    boolean isCompensation = "COMPENSATE_STEP".equals(command.type());

    try {
      for (Map<String, Object> pipeline : dataPipelines) {
        String id = requirePipelineField(pipeline, "id");
        String action = requirePipelineField(pipeline, "action");
        Map<String, Object> data = optionalPipelineMapField(pipeline, "data");

        switch (action) {
          case "ADD" -> redpandaClient.createPipeline(id, data);
          case "UPDATE" -> redpandaClient.updatePipeline(id, data);
          case "DELETE" -> redpandaClient.deletePipeline(id);
          default ->
              throw new IllegalArgumentException(
                  "Unknown pipeline action: " + action + " for pipeline: " + id);
        }
        processedIds.add(id);
      }

      Map<String, Object> resultData = Map.of("pipelineIds", List.copyOf(processedIds));
      Map<String, Object> compensationData = Map.of("pipelineIds", List.copyOf(processedIds));

      log.info(
          "Updated {} pipelines for saga {}",
          processedIds.size(),
          Encode.forJava(command.sagaId()));

      return SagaCommandResult.success(
          command.sagaId(), command.stepId(), resultData, compensationData);

    } catch (FatalAdapterException | RetryableAdapterException e) {
      String error = "UPDATE_PIPELINES failed: " + e.getMessage();
      log.error(
          "UPDATE_PIPELINES failed for saga {}: {}",
          Encode.forJava(command.sagaId()),
          Encode.forJava(e.getMessage()));
      return isCompensation
          ? SagaCommandResult.compensationFailure(command.sagaId(), command.stepId(), error)
          : SagaCommandResult.failure(command.sagaId(), command.stepId(), error);
    }
  }

  private SagaCommandResult handleDeletePipelines(SagaCommandMessage command) {
    List<String> pipelineIds = extractStringList(command, "pipelineIds");
    boolean isCompensation = "COMPENSATE_STEP".equals(command.type());

    try {
      for (String id : pipelineIds) {
        redpandaClient.deletePipeline(id);
      }

      log.info(
          "Deleted {} pipelines for saga {}", pipelineIds.size(), Encode.forJava(command.sagaId()));

      if (isCompensation) {
        return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());
      }
      return SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());

    } catch (FatalAdapterException | RetryableAdapterException e) {
      String error = "DELETE_PIPELINES failed: " + e.getMessage();
      log.error(
          "DELETE_PIPELINES failed for saga {}: {}",
          Encode.forJava(command.sagaId()),
          Encode.forJava(e.getMessage()));
      return isCompensation
          ? SagaCommandResult.compensationFailure(command.sagaId(), command.stepId(), error)
          : SagaCommandResult.failure(command.sagaId(), command.stepId(), error);
    }
  }

  private SagaCommandResult handleRestorePipelines(SagaCommandMessage command) {
    List<Map<String, Object>> dataPipelines = extractPipelineList(command, "dataPipelines");

    try {
      for (Map<String, Object> pipeline : dataPipelines) {
        String id = requirePipelineField(pipeline, "id");
        Map<String, Object> data = optionalPipelineMapField(pipeline, "data");
        redpandaClient.updatePipeline(id, data);
      }

      log.info(
          "Restored {} pipelines for saga {}",
          dataPipelines.size(),
          Encode.forJava(command.sagaId()));

      return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());

    } catch (FatalAdapterException | RetryableAdapterException e) {
      log.error(
          "RESTORE_PIPELINES failed for saga {}: {}",
          Encode.forJava(command.sagaId()),
          Encode.forJava(e.getMessage()));
      return SagaCommandResult.compensationFailure(
          command.sagaId(), command.stepId(), "RESTORE_PIPELINES failed: " + e.getMessage());
    }
  }

  // ─── Payload validation helpers ──────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> extractPipelineList(
      SagaCommandMessage command, String key) {
    Object value = command.payload().getOrDefault(key, List.of());
    if (!(value instanceof List<?> list)) {
      throw new IllegalArgumentException(
          command.operation() + " payload field '" + key + "' is not a List: " + value.getClass());
    }
    if (!list.isEmpty() && !(list.get(0) instanceof Map<?, ?>)) {
      throw new IllegalArgumentException(
          command.operation()
              + " payload field '"
              + key
              + "' contains non-Map elements: "
              + list.get(0).getClass());
    }
    return (List<Map<String, Object>>) list;
  }

  @SuppressWarnings("unchecked")
  private static List<String> extractStringList(SagaCommandMessage command, String key) {
    Object value = command.payload().getOrDefault(key, List.of());
    if (!(value instanceof List<?> list)) {
      throw new IllegalArgumentException(
          command.operation() + " payload field '" + key + "' is not a List: " + value.getClass());
    }
    if (!list.isEmpty() && !(list.get(0) instanceof String)) {
      throw new IllegalArgumentException(
          command.operation()
              + " payload field '"
              + key
              + "' contains non-String elements: "
              + list.get(0).getClass());
    }
    return (List<String>) list;
  }

  private static String requirePipelineField(Map<String, Object> pipeline, String field) {
    Object value = pipeline.get(field);
    if (value == null) {
      throw new IllegalArgumentException("Pipeline entry missing required field: " + field);
    }
    if (!(value instanceof String s)) {
      throw new IllegalArgumentException(
          "Pipeline field '" + field + "' is not a String: " + value.getClass());
    }
    return s;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> optionalPipelineMapField(
      Map<String, Object> pipeline, String field) {
    Object value = pipeline.get(field);
    if (value == null) {
      return Map.of();
    }
    if (!(value instanceof Map<?, ?>)) {
      throw new IllegalArgumentException(
          "Pipeline field '" + field + "' is not a Map: " + value.getClass());
    }
    return (Map<String, Object>) value;
  }
}
