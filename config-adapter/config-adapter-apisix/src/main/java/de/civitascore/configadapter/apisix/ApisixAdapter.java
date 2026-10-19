/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.apisix;

import de.civitascore.configadapter.adapter.AbstractConfigAdapter;
import de.civitascore.configadapter.apisix.ApisixAdminOperations.ResourceKind;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AbstractApiModel;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigValue;
import de.civitascore.configadapter.model.Operation;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * APISIX adapter that processes configuration messages and manages APISIX resources. Supports
 * CREATE, UPDATE, and DELETE operations for APISIX upstreams and routes; the Admin API mechanics
 * and error classification (retryable vs fatal, idempotent 409/404 handling) live in {@link
 * ApisixAdminOperations}.
 *
 * <p>Supported route events:
 *
 * <ul>
 *   <li>de.civitascore.api.route.created - Create a new route
 *   <li>de.civitascore.api.route.updated - Update an existing route
 *   <li>de.civitascore.api.route.deleted - Delete a route
 * </ul>
 *
 * <p>Routes support the following plugins (as per CIVITAS/CORE V1): openid-connect,
 * serverless-post-function, serverless-pre-function, response-rewrite, proxy-rewrite, prometheus,
 * loki.
 */
public class ApisixAdapter extends AbstractConfigAdapter {

  public static final String ADAPTER_NAME = "apisix";

  private static final Logger LOG = LoggerFactory.getLogger(ApisixAdapter.class);

  private static final String ADMIN_URL_DEFAULT = "http://localhost:9180";
  private static final String APISIX_RESULT_TYPE = "de.civitascore.api.processing.result";
  private static final String UPSTREAMS_SEGMENT = "upstreams";
  private static final String ROUTES_SEGMENT = "routes";

  private OkHttpClient client;
  private ApisixAdminOperations operations;

  @Override
  public void initialize(AdapterConfig config) {
    super.initialize(config);

    String adminApiUrl = getAdapterProperty("admin.url", ADMIN_URL_DEFAULT);
    String adminApiKey = getAdapterProperty("admin.key");
    if (adminApiKey == null || adminApiKey.isBlank()) {
      throw new IllegalArgumentException("The APISIX admin key cannot be null or blank.");
    }

    if (this.client == null) {
      this.client = createClient();
    }
    this.operations = new ApisixAdminOperations(() -> client, adminApiUrl, adminApiKey);

    LOG.info(
        "APISIX adapter '{}' initialized for: {}",
        Encode.forJava(getName()),
        Encode.forJava(adminApiUrl));
    LOG.info(
        "Subscribed to {} Kafka topics: {}",
        getSubscribedTopics().size(),
        Encode.forJava(String.valueOf(getSubscribedTopics())));
  }

  /**
   * Creates the default OkHttp client. Can be overridden for testing.
   *
   * @return configured OkHttpClient instance
   */
  protected OkHttpClient createClient() {
    return new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();
  }

  /**
   * Sets the client for testing purposes.
   *
   * @param client the OkHttpClient to use
   */
  void setClient(OkHttpClient client) {
    this.client = client;
  }

  @Override
  public String getName() {
    return ADAPTER_NAME;
  }

  @Override
  protected void doProcessConfigEvent(String topic, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    Operation operation = event.payload().operation();
    String targetResource = event.payload().targetResource();

    LOG.info(
        "Processing config event - Topic: {}, Operation: {}, TargetComponent: {},"
            + " TargetResource: {}",
        Encode.forJava(topic),
        operation,
        Encode.forJava(String.valueOf(event.payload().targetComponent())),
        Encode.forJava(String.valueOf(targetResource)));

    ResourceTarget target = parseTargetResource(targetResource);
    switch (operation) {
      case CREATE -> operations.create(target.kind(), extractConfig(event));
      case UPDATE -> operations.update(target.kind(), target.id(), extractConfig(event));
      case DELETE -> operations.delete(target.kind(), target.id());
    }

    String successMessage =
        "APISIX "
            + target.kind().label()
            + " "
            + operation.name().toLowerCase(Locale.ROOT)
            + "d successfully";
    LOG.info(successMessage);
    publishSuccessResult(event, successMessage, target.id());
  }

  @Override
  protected String getResultType() {
    return APISIX_RESULT_TYPE;
  }

  /**
   * Parses the targetResource string into resource kind and optional id. Expected formats: {@code
   * upstreams[/{id}]} or {@code routes[/{id}]} (possibly nested in a longer path). An unknown
   * resource type is fatal — there is nothing the adapter could apply the event to.
   */
  private ResourceTarget parseTargetResource(String targetResource) throws FatalAdapterException {
    String[] parts = targetResource.split("/");
    for (int i = 0; i < parts.length; i++) {
      ResourceKind kind = resourceKindOf(parts[i]);
      if (kind != null) {
        String id = i + 1 < parts.length ? parts[i + 1] : null;
        LOG.debug(
            "Parsed resource - Type: {}, ID: {}",
            Encode.forJava(kind.label()),
            Encode.forJava(String.valueOf(id)));
        return new ResourceTarget(kind, id);
      }
    }
    LOG.warn("Unknown resource type in target: {}", Encode.forJava(targetResource));
    throw new FatalAdapterException(AdapterErrorCode.INVALID_RESOURCE_TYPE, targetResource);
  }

  private static ResourceKind resourceKindOf(String pathSegment) {
    if (UPSTREAMS_SEGMENT.equals(pathSegment)) {
      return ResourceKind.UPSTREAM;
    }
    if (ROUTES_SEGMENT.equals(pathSegment)) {
      return ResourceKind.ROUTE;
    }
    return null;
  }

  /**
   * Extracts the API payload from the event's ConfigValue. All APISIX-bound models ({@code
   * RouteConfigValue}, {@code ApisixConfigValue}) extend {@link AbstractApiModel} and serialize via
   * {@code toApiMap()}; anything else is passed through as-is.
   */
  private static Object extractConfig(ConfigEvent event) {
    ConfigValue configValue = event.payload().config().value();
    return configValue instanceof AbstractApiModel model ? model.toApiMap() : configValue;
  }

  @Override
  public void close() {
    if (client != null) {
      client.dispatcher().executorService().shutdown();
      client.connectionPool().evictAll();
    }
    LOG.info("APISIX adapter closed");
  }

  /** Parsed {@code targetResource}: which APISIX resource kind plus the optional resource id. */
  private record ResourceTarget(ResourceKind kind, String id) {}
}
