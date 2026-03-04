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

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.AdapterOperation;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for the RedPanda Connect Streams API. Handles pipeline CRUD operations by converting JSON
 * pipeline definitions to YAML and sending them to the streams endpoint.
 *
 * <p>Credentials in pipeline data marked with {@code ENC(...)} are decrypted before being sent.
 *
 * <p>Error handling:
 *
 * <ul>
 *   <li>HTTP 2xx → success
 *   <li>HTTP 4xx → {@link FatalAdapterException} (permanent error)
 *   <li>HTTP 5xx → {@link RetryableAdapterException} (transient error)
 *   <li>Network errors → {@link RetryableAdapterException}
 * </ul>
 */
class RedpandaConnectClient implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(RedpandaConnectClient.class);

  private static final String STREAMS_PATH = "streams";
  private static final int CONNECT_TIMEOUT_SECONDS = 10;
  private static final int READ_TIMEOUT_SECONDS = 30;
  private static final int HTTP_CLIENT_ERROR_MIN = 400;
  private static final int HTTP_SERVER_ERROR_MIN = 500;

  /**
   * Allowed characters for pipeline IDs: alphanumeric start, then alphanumeric, dots, dashes,
   * underscores.
   */
  private static final Pattern VALID_PIPELINE_ID =
      Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}");

  private final String baseUrl;
  private final PipelineSerializer serializer;
  private final Client client;

  RedpandaConnectClient(String baseUrl, byte[] stretchedKey) {
    this(baseUrl, new PipelineSerializer(stretchedKey), createDefaultClient());
  }

  RedpandaConnectClient(String baseUrl, byte[] stretchedKey, Client client) {
    this(baseUrl, new PipelineSerializer(stretchedKey), client);
  }

  private RedpandaConnectClient(String baseUrl, PipelineSerializer serializer, Client client) {
    this.baseUrl = baseUrl.replaceAll("/$", "");
    this.serializer = serializer;
    this.client = client;
  }

  /**
   * Creates a new pipeline stream.
   *
   * @param pipelineId the pipeline identifier
   * @param pipelineData the pipeline definition as a Map
   * @throws FatalAdapterException on permanent errors (4xx, invalid input)
   * @throws RetryableAdapterException on transient errors (5xx, network)
   */
  void createPipeline(String pipelineId, Map<String, Object> pipelineData)
      throws FatalAdapterException, RetryableAdapterException {
    validatePipelineId(pipelineId);
    String yaml = serializer.toYaml(pipelineData, pipelineId);
    logger.info("Creating pipeline: {}", Encode.forJava(pipelineId));

    try (Response response =
        client
            .target(baseUrl)
            .path(STREAMS_PATH)
            .path(pipelineId)
            .request(MediaType.APPLICATION_JSON)
            .post(Entity.entity(yaml, "application/x-yaml"))) {
      handleResponse(response, AdapterOperation.PIPELINE_CREATE, pipelineId);
    } catch (FatalAdapterException | RetryableAdapterException e) {
      throw e;
    } catch (ProcessingException e) {
      throw networkError(pipelineId, e);
    }
  }

  /**
   * Updates an existing pipeline stream.
   *
   * @param pipelineId the pipeline identifier
   * @param pipelineData the updated pipeline definition as a Map
   * @throws FatalAdapterException on permanent errors (4xx, invalid input)
   * @throws RetryableAdapterException on transient errors (5xx, network)
   */
  void updatePipeline(String pipelineId, Map<String, Object> pipelineData)
      throws FatalAdapterException, RetryableAdapterException {
    validatePipelineId(pipelineId);
    String yaml = serializer.toYaml(pipelineData, pipelineId);
    logger.info("Updating pipeline: {}", Encode.forJava(pipelineId));

    try (Response response =
        client
            .target(baseUrl)
            .path(STREAMS_PATH)
            .path(pipelineId)
            .request(MediaType.APPLICATION_JSON)
            .put(Entity.entity(yaml, "application/x-yaml"))) {
      handleResponse(response, AdapterOperation.PIPELINE_UPDATE, pipelineId);
    } catch (FatalAdapterException | RetryableAdapterException e) {
      throw e;
    } catch (ProcessingException e) {
      throw networkError(pipelineId, e);
    }
  }

  /**
   * Deletes a pipeline stream.
   *
   * @param pipelineId the pipeline identifier
   * @throws FatalAdapterException on permanent errors (4xx, invalid input)
   * @throws RetryableAdapterException on transient errors (5xx, network)
   */
  void deletePipeline(String pipelineId) throws FatalAdapterException, RetryableAdapterException {
    validatePipelineId(pipelineId);
    logger.info("Deleting pipeline: {}", Encode.forJava(pipelineId));

    try (Response response =
        client
            .target(baseUrl)
            .path(STREAMS_PATH)
            .path(pipelineId)
            .request(MediaType.APPLICATION_JSON)
            .delete()) {
      handleResponse(response, AdapterOperation.PIPELINE_DELETE, pipelineId);
    } catch (FatalAdapterException | RetryableAdapterException e) {
      throw e;
    } catch (ProcessingException e) {
      throw networkError(pipelineId, e);
    }
  }

  @Override
  public void close() {
    serializer.close();
    if (client != null) {
      client.close();
    }
  }

  private static void validatePipelineId(String pipelineId) throws FatalAdapterException {
    if (pipelineId == null || !VALID_PIPELINE_ID.matcher(pipelineId).matches()) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD, "Invalid pipeline ID format");
    }
  }

  private void handleResponse(Response response, AdapterOperation operation, String pipelineId)
      throws FatalAdapterException, RetryableAdapterException {
    int status = response.getStatus();

    if (status >= 200 && status < 300) {
      logger.info(
          "Pipeline {} succeeded for: {}", operation.getDescription(), Encode.forJava(pipelineId));
      return;
    }

    String body = response.readEntity(String.class);

    if (status >= HTTP_SERVER_ERROR_MIN) {
      logger.warn(
          "RedPanda server error during {} for pipeline {}: {} {}",
          operation.getDescription(),
          Encode.forJava(pipelineId),
          status,
          Encode.forJava(body));
      throw new RetryableAdapterException(
          AdapterErrorCode.REDPANDA_ERROR, null, "redpanda", "HTTP " + status);
    }

    if (status >= HTTP_CLIENT_ERROR_MIN) {
      logger.error(
          "RedPanda client error during {} for pipeline {}: {} {}",
          operation.getDescription(),
          Encode.forJava(pipelineId),
          status,
          Encode.forJava(body));
      throw new FatalAdapterException(
          AdapterErrorCode.REDPANDA_PIPELINE_ERROR, null, "HTTP " + status + ": " + body);
    }
  }

  private static RetryableAdapterException networkError(String pipelineId, ProcessingException e) {
    logger.warn(
        "Network error for pipeline {}: {}",
        Encode.forJava(pipelineId),
        Encode.forJava(e.getMessage()));
    return new RetryableAdapterException(
        AdapterErrorCode.NETWORK_ERROR, e, "redpanda", e.getMessage());
  }

  private static Client createDefaultClient() {
    return ClientBuilder.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build();
  }
}
