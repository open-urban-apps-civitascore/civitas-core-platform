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
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.util.PayloadConverter;
import jakarta.ws.rs.client.Client;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
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

  private static final String OP_DEPLOY = "DEPLOY_PIPELINES";
  private static final String OP_UPDATE = "UPDATE_PIPELINES";
  private static final String OP_DELETE = "DELETE_PIPELINES";
  private static final String OP_RESTORE = "RESTORE_PIPELINES";

  private static final String TYPE_COMPENSATE = "COMPENSATE_STEP";

  private static final String FIELD_DATA_PIPELINES = "dataPipelines";
  private static final String FIELD_PIPELINE_IDS = "pipelineIds";
  private static final String FIELD_TARGET_URL = "targetUrl";
  private static final String FIELD_DATASOURCES = "datasources";
  private static final String FIELD_ID = "id";
  private static final String FIELD_ACTION = "action";
  private static final String FIELD_DATA = "data";
  private static final String FIELD_CONFIGURATION = "configuration";

  private static final String ACTION_ADD = "ADD";
  private static final String ACTION_UPDATE = "UPDATE";
  private static final String ACTION_DELETE = "DELETE";

  private RedpandaConnectClient redpandaClient;

  /** No-arg constructor for ServiceLoader discovery. Call {@link #initialize} before use. */
  public RedpandaSagaHandler() {
    super(ADAPTER_NAME);
  }

  @Override
  protected void doInitialize(AdapterConfig config) {
    String baseUrl = getProperty("url", DEFAULT_URL);
    String masterKeyHex = getProperty("master-key", null);
    byte[] stretchedKey;

    if (masterKeyHex != null) {
      try {
        stretchedKey =
            CryptoKeyLoader.stretchMasterKey(CryptoKeyLoader.hexStringToBytes(masterKeyHex));
      } catch (GeneralSecurityException e) {
        throw new IllegalStateException("Invalid master key in adapter config", e);
      }
    } else {
      stretchedKey = CryptoKeyLoader.loadAndStretchKeyFromEnv(MASTER_KEY_ENV);
    }

    if (stretchedKey.length == 0) {
      log.warn("{} not set — encrypted credentials cannot be decrypted", MASTER_KEY_ENV);
    }

    if (this.redpandaClient == null) {
      Client jaxrsClient = client();
      this.redpandaClient =
          jaxrsClient != null
              ? new RedpandaConnectClient(baseUrl, stretchedKey, jaxrsClient)
              : new RedpandaConnectClient(baseUrl, stretchedKey);
    }
    Arrays.fill(stretchedKey, (byte) 0);

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
      case OP_DEPLOY -> handleDeployPipelines(command);
      case OP_UPDATE -> handleUpdatePipelines(command);
      case OP_DELETE -> handleDeletePipelines(command);
      case OP_RESTORE -> handleRestorePipelines(command);
      default -> unknownOperation(command);
    };
  }

  private SagaCommandResult handleDeployPipelines(SagaCommandMessage command) {
    List<Map<String, Object>> dataPipelines = extractPipelineList(command, FIELD_DATA_PIPELINES);
    String targetUrl = extractTargetUrl(command);
    List<String> deployedIds = new ArrayList<>();

    try {
      List<Datasource> datasources = extractDatasources(command);
      for (Map<String, Object> pipeline : dataPipelines) {
        String id = requirePipelineField(pipeline, FIELD_ID);
        Map<String, Object> data = resolvePipelineData(pipeline, datasources, targetUrl);

        redpandaClient.createPipeline(id, data);
        deployedIds.add(id);
      }

      Map<String, Object> resultData = Map.of(FIELD_PIPELINE_IDS, List.copyOf(deployedIds));
      Map<String, Object> compensationData = Map.of(FIELD_PIPELINE_IDS, List.copyOf(deployedIds));

      log.info(
          "Deployed {} pipelines for saga {}",
          deployedIds.size(),
          Encode.forJava(command.sagaId()));

      return SagaCommandResult.success(
          command.sagaId(), command.stepId(), resultData, compensationData);

    } catch (FatalAdapterException | RetryableAdapterException e) {
      return pipelineError(command, OP_DEPLOY, false, e);
    }
  }

  private SagaCommandResult handleUpdatePipelines(SagaCommandMessage command) {
    List<Map<String, Object>> dataPipelines = extractPipelineList(command, FIELD_DATA_PIPELINES);
    String targetUrl = extractTargetUrl(command);
    List<String> processedIds = new ArrayList<>();
    boolean isCompensation = TYPE_COMPENSATE.equals(command.type());

    try {
      List<Datasource> datasources = extractDatasources(command);
      for (Map<String, Object> pipeline : dataPipelines) {
        String id = requirePipelineField(pipeline, FIELD_ID);
        String action = requirePipelineField(pipeline, FIELD_ACTION);
        Map<String, Object> data = resolvePipelineData(pipeline, datasources, targetUrl);

        switch (action) {
          case ACTION_ADD -> redpandaClient.createPipeline(id, data);
          case ACTION_UPDATE -> redpandaClient.updatePipeline(id, data);
          case ACTION_DELETE -> redpandaClient.deletePipeline(id);
          default ->
              throw new IllegalArgumentException(
                  "Unknown pipeline action: " + action + " for pipeline: " + id);
        }
        processedIds.add(id);
      }

      Map<String, Object> resultData = Map.of(FIELD_PIPELINE_IDS, List.copyOf(processedIds));
      Map<String, Object> compensationData = Map.of(FIELD_PIPELINE_IDS, List.copyOf(processedIds));

      log.info(
          "Updated {} pipelines for saga {}",
          processedIds.size(),
          Encode.forJava(command.sagaId()));

      return SagaCommandResult.success(
          command.sagaId(), command.stepId(), resultData, compensationData);

    } catch (FatalAdapterException | RetryableAdapterException e) {
      return pipelineError(command, OP_UPDATE, isCompensation, e);
    }
  }

  private SagaCommandResult handleDeletePipelines(SagaCommandMessage command) {
    List<String> pipelineIds = extractStringList(command, FIELD_PIPELINE_IDS);
    boolean isCompensation = TYPE_COMPENSATE.equals(command.type());

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
      return pipelineError(command, OP_DELETE, isCompensation, e);
    }
  }

  private SagaCommandResult handleRestorePipelines(SagaCommandMessage command) {
    List<Map<String, Object>> dataPipelines = extractPipelineList(command, FIELD_DATA_PIPELINES);
    String targetUrl = extractTargetUrl(command);

    try {
      List<Datasource> datasources = extractDatasources(command);
      for (Map<String, Object> pipeline : dataPipelines) {
        String id = requirePipelineField(pipeline, FIELD_ID);
        Map<String, Object> data = resolvePipelineData(pipeline, datasources, targetUrl);
        redpandaClient.updatePipeline(id, data);
      }

      log.info(
          "Restored {} pipelines for saga {}",
          dataPipelines.size(),
          Encode.forJava(command.sagaId()));

      return SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId());

    } catch (FatalAdapterException | RetryableAdapterException e) {
      return pipelineError(command, OP_RESTORE, true, e);
    }
  }

  // ─── Pipeline data resolution helpers ──────────────────────────────────────

  private Map<String, Object> resolvePipelineData(
      Map<String, Object> pipeline, List<Datasource> datasources, String targetUrl)
      throws FatalAdapterException {
    Map<String, Object> data = optionalPipelineMapField(pipeline, FIELD_DATA);
    List<Datasource> decrypted = decryptDatasourceCredentials(datasources);
    data = DatasourceInjector.resolve(data, decrypted);
    return PlaceholderResolver.resolve(data, targetUrl, decrypted);
  }

  /**
   * Decrypts datasource credentials so SQL DSNs can be completed from username/password fields and
   * then re-encrypts the final DSN as a single ENC(...) value for the remaining pipeline flow.
   */
  private List<Datasource> decryptDatasourceCredentials(List<Datasource> datasources)
      throws FatalAdapterException {
    List<Datasource> result = new ArrayList<>(datasources.size());
    for (Datasource ds : datasources) {
      Map<String, Object> props = ds.getAdditionalProperties();
      if (props.isEmpty()) {
        result.add(ds);
        continue;
      }
      Map<String, Object> decryptedProps = redpandaClient.decryptDatasourceCredentials(props);
      if (ConnectorType.fromRaw(ds.getType()).orElse(null) == ConnectorType.SQL) {
        decryptedProps = reencryptSqlDsn(ds, decryptedProps);
      }
      result.add(copyDatasource(ds, decryptedProps));
    }
    return List.copyOf(result);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> reencryptSqlDsn(Datasource datasource, Map<String, Object> props)
      throws FatalAdapterException {
    if (props == null || props.isEmpty()) {
      return props;
    }

    Datasource resolvedDatasource = copyDatasource(datasource, props);
    Map<String, Object> cfg = DatasourceParser.configuration(resolvedDatasource);
    String dsn = DatasourceField.DSN.asString(cfg).orElse(null);
    if (dsn == null) {
      dsn = DatasourceParser.buildDsn(resolvedDatasource, cfg);
    } else {
      dsn = DatasourceParser.mergeCredentialsIntoDsnIfMissing(dsn, cfg);
    }
    if (dsn == null) {
      return props;
    }

    String encryptedDsn = redpandaClient.encryptDatasourceValue(dsn);
    Map<String, Object> updatedProps = new LinkedHashMap<>(props);

    if (updatedProps.get(FIELD_CONFIGURATION) instanceof Map<?, ?> nested) {
      Map<String, Object> updatedConfiguration = new LinkedHashMap<>((Map<String, Object>) nested);
      updatedConfiguration.put("dsn", encryptedDsn);
      updatedProps.put(FIELD_CONFIGURATION, updatedConfiguration);
    } else {
      updatedProps.put("dsn", encryptedDsn);
    }
    return updatedProps;
  }

  private Datasource copyDatasource(Datasource source, Map<String, Object> props) {
    Datasource copy = new Datasource();
    copy.setId(source.getId());
    copy.setType(source.getType());
    copy.setName(source.getName());
    copy.setDescription(source.getDescription());
    copy.setHost(source.getHost());
    copy.setPort(source.getPort());
    for (Map.Entry<String, Object> entry : props.entrySet()) {
      copy.handleUnknownProperty(entry.getKey(), entry.getValue());
    }
    return copy;
  }

  private SagaCommandResult pipelineError(
      SagaCommandMessage command, String operation, boolean isCompensation, Exception e) {
    String error = operation + " failed: " + e.getMessage();
    log.error(
        "{} failed for saga {}: {}",
        operation,
        Encode.forJava(command.sagaId()),
        Encode.forJava(e.getMessage()));
    return isCompensation
        ? SagaCommandResult.compensationFailure(command.sagaId(), command.stepId(), error)
        : SagaCommandResult.failure(command.sagaId(), command.stepId(), error);
  }

  // ─── Payload validation helpers ───────────────────────────────────────────

  private static List<Datasource> extractDatasources(SagaCommandMessage command)
      throws FatalAdapterException {
    Object value = command.payload().getOrDefault(FIELD_DATASOURCES, List.of());
    if (!(value instanceof List<?> list) || list.isEmpty()) {
      return List.of();
    }
    List<Datasource> result = new ArrayList<>(list.size());
    for (Object item : list) {
      try {
        result.add(PayloadConverter.fromValue(item, Datasource.class));
      } catch (IllegalArgumentException e) {
        throw new FatalAdapterException(
            AdapterErrorCode.INVALID_PAYLOAD,
            e,
            "Invalid datasource entry in payload: " + e.getMessage());
      }
    }
    return List.copyOf(result);
  }

  private static String extractTargetUrl(SagaCommandMessage command) {
    Object value = command.payload().get(FIELD_TARGET_URL);
    return value instanceof String s ? s : null;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> extractPipelineList(
      SagaCommandMessage command, String key) {
    Object value = command.payload().getOrDefault(key, List.of());
    if (!(value instanceof List<?> list)) {
      throw new IllegalArgumentException(
          command.operation() + " payload field '" + key + "' is not a List: " + value.getClass());
    }
    // Spot-check first element type only — JSON deserialization produces homogeneous lists
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
    // Spot-check first element type only — JSON deserialization produces homogeneous lists
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
