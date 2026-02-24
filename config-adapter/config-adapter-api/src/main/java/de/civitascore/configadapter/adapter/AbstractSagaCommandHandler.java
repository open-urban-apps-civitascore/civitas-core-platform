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
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import java.util.concurrent.TimeUnit;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for {@link SagaCommandHandler} implementations. Provides the common boilerplate for
 * saga command handling:
 *
 * <ul>
 *   <li>Adapter name management and {@link #adapter()} implementation
 *   <li>Initialization lifecycle with {@link #doInitialize(AdapterConfig)}
 *   <li>Exception-dispatch boilerplate in {@link #handle(SagaCommandMessage)} that delegates to
 *       {@link #doHandle(SagaCommandMessage)}
 *   <li>Error classification and logging (including JAX-RS {@link ProcessingException})
 *   <li>JAX-RS client lifecycle ({@link #createClient()}, {@link #close()})
 *   <li>HTTP response checking ({@link #checkResponse(Response, String)})
 *   <li>Config property helpers with adapter-name prefix
 * </ul>
 *
 * <p>Subclasses implement {@link #doInitialize(AdapterConfig)} to read config properties and {@link
 * #doHandle(SagaCommandMessage)} to dispatch by operation.
 */
public abstract class AbstractSagaCommandHandler implements SagaCommandHandler {

  protected final Logger log = LoggerFactory.getLogger(getClass());
  private final String adapterName;
  protected AdapterConfig config;
  private Client client;

  protected AbstractSagaCommandHandler(String adapterName) {
    this.adapterName = adapterName;
  }

  @Override
  public final String adapter() {
    return adapterName;
  }

  @Override
  public final void initialize(AdapterConfig config) {
    this.config = config;
    doInitialize(config);
    if (this.client == null) {
      this.client = createClient();
    }
    log.info("{} initialized", getClass().getSimpleName());
  }

  /** Subclass reads config properties, validates keys, etc. */
  protected abstract void doInitialize(AdapterConfig config);

  // ─── JAX-RS client lifecycle ──────────────────────────────────────────────

  protected Client client() {
    return client;
  }

  protected Client createClient() {
    return ClientBuilder.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();
  }

  protected void setClient(Client client) {
    this.client = client;
  }

  @Override
  public void close() {
    if (client != null) {
      client.close();
      log.info("{} closed", getClass().getSimpleName());
    }
  }

  // ─── Command handling ─────────────────────────────────────────────────────

  @Override
  public final SagaCommandResult handle(SagaCommandMessage command) {
    boolean isCompensation = "COMPENSATE_STEP".equals(command.type());
    try {
      return doHandle(command);
    } catch (Exception e) {
      return handleException(command, isCompensation, e);
    }
  }

  /**
   * Subclass dispatches by operation (switch on {@code command.operation()}). For unknown
   * operations, call {@link #unknownOperation(SagaCommandMessage)}.
   */
  protected abstract SagaCommandResult doHandle(SagaCommandMessage command);

  /** Builds a failure/compensationFailure result from a caught exception. */
  protected SagaCommandResult handleException(
      SagaCommandMessage command, boolean isCompensation, Exception e) {
    String error = classifyAndLogError(command, e);
    return isCompensation
        ? SagaCommandResult.compensationFailure(command.sagaId(), command.stepId(), error)
        : SagaCommandResult.failure(command.sagaId(), command.stepId(), error);
  }

  /**
   * Classifies the error, logs appropriately, returns the error message string. Handles JAX-RS
   * {@link ProcessingException} as network errors; all other exceptions are logged at error level.
   */
  protected String classifyAndLogError(SagaCommandMessage command, Exception e) {
    if (e instanceof ProcessingException) {
      String error = "Network error: " + e.getMessage();
      log.warn(
          "{} {} failed for saga {}: {}",
          Encode.forJava(adapterName),
          Encode.forJava(command.operation()),
          Encode.forJava(command.sagaId()),
          Encode.forJava(e.getMessage()));
      return error;
    }
    String error = command.operation() + " failed: " + e.getMessage();
    log.error(
        "{} {} failed for saga {}",
        Encode.forJava(adapterName),
        Encode.forJava(command.operation()),
        Encode.forJava(command.sagaId()),
        e);
    return error;
  }

  // ─── HTTP response checking ───────────────────────────────────────────────

  /** Checks HTTP response, throws {@link SagaApiException} on non-2xx. */
  protected void checkResponse(Response response, String operationDesc) {
    int status = response.getStatus();
    if (status >= 200 && status < 300) {
      return;
    }
    String body = response.readEntity(String.class);
    throw new SagaApiException(operationDesc + " failed: HTTP " + status + " — " + body, status);
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  /** Helper for unknown-operation results (usable from {@code doHandle} default branch). */
  protected SagaCommandResult unknownOperation(SagaCommandMessage command) {
    boolean isCompensation = "COMPENSATE_STEP".equals(command.type());
    String error = "Unknown " + adapterName + " operation: " + command.operation();
    log.warn(error);
    return isCompensation
        ? SagaCommandResult.compensationFailure(command.sagaId(), command.stepId(), error)
        : SagaCommandResult.failure(command.sagaId(), command.stepId(), error);
  }

  /**
   * Extracts a required String value from the command payload. Throws {@link
   * IllegalArgumentException} if the key is missing or not a String.
   */
  protected static String requireString(SagaCommandMessage command, String key) {
    Object value = command.payload().get(key);
    if (value == null) {
      throw new IllegalArgumentException(
          command.operation() + " payload missing required field: " + key);
    }
    if (!(value instanceof String s)) {
      throw new IllegalArgumentException(
          command.operation()
              + " payload field '"
              + key
              + "' is not a String: "
              + value.getClass());
    }
    return s;
  }

  /** Config property helper: reads "{adapterName}.{key}". */
  protected String getProperty(String key) {
    return config.getProperty(adapterName + "." + key);
  }

  /** Config property helper: reads "{adapterName}.{key}" with a default value. */
  protected String getProperty(String key, String defaultValue) {
    return config.getProperty(adapterName + "." + key, defaultValue);
  }

  /** Shared exception for HTTP API errors with status code. */
  public static class SagaApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int statusCode;

    public SagaApiException(String message, int statusCode) {
      super(message);
      this.statusCode = statusCode;
    }

    public int statusCode() {
      return statusCode;
    }
  }
}
