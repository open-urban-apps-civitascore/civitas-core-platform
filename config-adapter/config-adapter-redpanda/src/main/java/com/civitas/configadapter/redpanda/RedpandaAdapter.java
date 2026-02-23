/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.redpanda;

import com.civitas.configadapter.adapter.AbstractConfigAdapter;
import com.civitas.configadapter.configuration.AdapterConfig;
import com.civitas.configadapter.crypto.CredentialDecryptor;
import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.exception.RetryableAdapterException;
import com.civitas.configadapter.model.AdapterErrorCode;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigValue;
import com.civitas.configadapter.model.Operation;
import com.civitas.configadapter.model.redpanda.PipelineConfigValue;
import java.util.Map;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RedPanda Connect adapter that processes pipeline configuration events. Supports CREATE, UPDATE,
 * and DELETE operations for data pipelines via the RedPanda Connect Streams API.
 *
 * <p>Error handling:
 *
 * <ul>
 *   <li>Network errors → {@link RetryableAdapterException}
 *   <li>HTTP 5xx → {@link RetryableAdapterException}
 *   <li>HTTP 4xx → {@link FatalAdapterException}
 *   <li>Unknown operations → {@link FatalAdapterException}
 * </ul>
 */
public class RedpandaAdapter extends AbstractConfigAdapter {

  private static final Logger logger = LoggerFactory.getLogger(RedpandaAdapter.class);

  public static final String ADAPTER_NAME = "redpanda";
  private static final String DEFAULT_URL = "http://localhost:4195";
  private static final String URL_PROPERTY = "url";
  private static final String MASTER_KEY_ENV = "CIVITAS_MASTER_KEY";
  private static final String MASTER_SALT_ENV = "CIVITAS_MASTER_SALT";

  private RedpandaConnectClient redpandaClient;

  @Override
  public void initialize(AdapterConfig config) {
    super.initialize(config);

    String baseUrl = getAdapterProperty(URL_PROPERTY, DEFAULT_URL);
    byte[] masterKey = CredentialDecryptor.loadKeyFromEnv(MASTER_KEY_ENV);
    byte[] salt = CredentialDecryptor.loadKeyFromEnv(MASTER_SALT_ENV);

    if (masterKey.length == 0 || salt.length == 0) {
      logger.warn(
          "{} or {} not set — encrypted credentials cannot be decrypted",
          MASTER_KEY_ENV,
          MASTER_SALT_ENV);
    }

    if (this.redpandaClient == null) {
      this.redpandaClient = new RedpandaConnectClient(baseUrl, masterKey, salt);
    }

    logger.info(
        "RedPanda adapter '{}' initialized for: {}",
        Encode.forJava(getName()),
        Encode.forJava(baseUrl));
    logger.info(
        "Subscribed to {} Kafka topics: {}",
        getSubscribedTopics().size(),
        Encode.forJava(getSubscribedTopics().toString()));
  }

  @Override
  public String getName() {
    return ADAPTER_NAME;
  }

  @Override
  protected void doProcessConfigEvent(String topic, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    Operation operation = event.payload().operation();
    String pipelineId = extractPipelineId(event);

    logger.info(
        "Processing pipeline event - Topic: {}, Operation: {}, PipelineId: {}",
        Encode.forJava(topic),
        operation,
        Encode.forJava(pipelineId));

    switch (operation) {
      case CREATE -> handleCreate(pipelineId, event);
      case UPDATE -> handleUpdate(pipelineId, event);
      case DELETE -> handleDelete(pipelineId, event);
      default -> {
        logger.warn("Unknown operation: {}", Encode.forJava(String.valueOf(operation)));
        throw new FatalAdapterException(AdapterErrorCode.UNSUPPORTED_OPERATION, operation);
      }
    }
  }

  @Override
  protected String getResultType() {
    return PipelineConfigValue.REDPANDA_RESULT_TYPE;
  }

  private void handleCreate(String pipelineId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    Map<String, Object> pipelineData = extractPipelineData(event);
    redpandaClient.createPipeline(pipelineId, pipelineData);
    publishSuccessResult(event, "Pipeline created successfully: " + pipelineId, pipelineId);
  }

  private void handleUpdate(String pipelineId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    Map<String, Object> pipelineData = extractPipelineData(event);
    redpandaClient.updatePipeline(pipelineId, pipelineData);
    publishSuccessResult(event, "Pipeline updated successfully: " + pipelineId, pipelineId);
  }

  private void handleDelete(String pipelineId, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    redpandaClient.deletePipeline(pipelineId);
    publishSuccessResult(event, "Pipeline deleted successfully: " + pipelineId, pipelineId);
  }

  private String extractPipelineId(ConfigEvent event) throws FatalAdapterException {
    ConfigValue configValue = event.payload().config().value();
    if (configValue instanceof PipelineConfigValue pipelineConfig) {
      String id = pipelineConfig.getPipelineId();
      if (id == null || id.isBlank()) {
        throw new FatalAdapterException(
            AdapterErrorCode.INVALID_PAYLOAD, "Pipeline ID is required");
      }
      return id;
    }
    throw new FatalAdapterException(
        AdapterErrorCode.INVALID_PAYLOAD, "Expected PipelineConfigValue");
  }

  private Map<String, Object> extractPipelineData(ConfigEvent event) throws FatalAdapterException {
    ConfigValue configValue = event.payload().config().value();
    if (configValue instanceof PipelineConfigValue pipelineConfig) {
      return pipelineConfig.toApiMap();
    }
    throw new FatalAdapterException(
        AdapterErrorCode.INVALID_PAYLOAD, "Expected PipelineConfigValue");
  }

  void setRedpandaClient(RedpandaConnectClient client) {
    this.redpandaClient = client;
  }

  @Override
  public void close() {
    if (redpandaClient != null) {
      redpandaClient.close();
    }
    logger.info("RedPanda adapter closed");
  }
}
