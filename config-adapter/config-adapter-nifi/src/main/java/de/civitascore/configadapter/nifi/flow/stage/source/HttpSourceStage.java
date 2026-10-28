/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.source;

import static de.civitascore.configadapter.nifi.flow.stage.BindingSupport.isEncrypted;
import static de.civitascore.configadapter.nifi.flow.stage.BindingSupport.putIfPresent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.SourceType;
import de.civitascore.configadapter.nifi.flow.stage.BuildContext;
import de.civitascore.configadapter.nifi.flow.stage.Fragment;
import de.civitascore.configadapter.nifi.flow.stage.PayloadForm;
import de.civitascore.configadapter.nifi.flow.stage.PlanContext;
import de.civitascore.configadapter.nifi.flow.stage.Processor;
import de.civitascore.configadapter.nifi.flow.stage.SourceStage;
import de.civitascore.configadapter.nifi.flow.stage.StageResult;
import de.civitascore.configadapter.nifi.flow.stage.StopSource;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HTTP/REST pull source over GetHttpApiCall. The stage maps no field of the API call, so a new
 * setting of an API call needs no change here; the processor is the one check on its shape.
 */
public final class HttpSourceStage implements SourceStage {

  /** Friendly name the REST client matches for the post-upload sensitive-property push. */
  private static final String HTTP_PROCESSOR = "HttpApiCall";

  private static final String WEB_CLIENT = "HttpSourceWebClient";

  /**
   * Friendly name of the client certificate's SSL context, matched like {@link #HTTP_PROCESSOR}.
   */
  private static final String SSL_CONTEXT = "HttpSourceSslContext";

  private static final String AUTHENTICATION_TYPE = "Authentication Type";

  private static final String NONE = "NONE";
  private static final String BASIC = "BASIC";
  private static final String BEARER_TOKEN = "BEARER_TOKEN";
  private static final String API_TOKEN = "API_TOKEN";
  private static final String OAUTH2 = "OAUTH2";
  private static final String CLIENT_CERTIFICATE = "CLIENT_CERTIFICATE";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final CredentialResolver credentials;

  public HttpSourceStage(CredentialResolver credentials) {
    this.credentials = credentials;
  }

  @Override
  public SourceType type() {
    return SourceType.HTTP;
  }

  @Override
  public PayloadForm output() {
    return PayloadForm.RAW_JSON;
  }

  @Override
  public boolean acceptsSchedule() {
    return true;
  }

  @Override
  public boolean requiresSchedule() {
    return true;
  }

  @Override
  public boolean selectsApiCall() {
    return true;
  }

  @Override
  public void bind(
      Datasource source,
      Map<String, Map<String, Object>> artifacts,
      GraphNode node,
      String sourceNodeKey,
      PlanContext out)
      throws FatalAdapterException {
    Map<String, Object> original = source.getAdditionalProperties();
    Map<String, Object> recordSchema = dataStructure(source, artifacts);
    Map<String, Object> apiCall = selectApiCall(original.get("apiCalls"), node);
    Map<String, Object> authentication = objectOrEmpty(original.get("authentication"));
    String type = authentication.get("type") instanceof String text ? text : NONE;
    Secrets secrets = new Secrets(original, authentication);

    putIfPresent(out::putSourceProperty, "Base URL", original.get("baseUrl"));
    out.putSourceProperty("API Call", json(apiCall));
    out.putSourceProperty("Record Schema", json(recordSchema));
    out.putSourceProperty(AUTHENTICATION_TYPE, type);
    switch (type) {
      case BASIC -> {
        putIfPresent(out::putSourceProperty, "Basic Auth Username", authentication.get("username"));
        secrets
            .value("password")
            .ifPresent(value -> out.putSensitive(HTTP_PROCESSOR, "Basic Auth Password", value));
      }
      case BEARER_TOKEN ->
          secrets
              .value("token")
              .ifPresent(value -> out.putSensitive(HTTP_PROCESSOR, "Bearer Token", value));
      case API_TOKEN -> {
        putIfPresent(out::putSourceProperty, "API Token Name", authentication.get("name"));
        putIfPresent(out::putSourceProperty, "API Token Location", authentication.get("in"));
        secrets
            .value("value")
            .ifPresent(value -> out.putSensitive(HTTP_PROCESSOR, "API Token Value", value));
      }
      case OAUTH2 -> {
        putIfPresent(out::putSourceProperty, "OAuth2 Token URL", authentication.get("tokenUrl"));
        putIfPresent(out::putSourceProperty, "OAuth2 Client ID", authentication.get("clientId"));
        putIfPresent(out::putSourceProperty, "OAuth2 Scope", authentication.get("scope"));
        secrets
            .value("clientSecret")
            .ifPresent(value -> out.putSensitive(HTTP_PROCESSOR, "OAuth2 Client Secret", value));
      }
      case CLIENT_CERTIFICATE -> {
        secrets
            .value("certificate")
            .ifPresent(
                value -> out.putControllerServiceProperty(SSL_CONTEXT, "Certificate Chain", value));
        Optional<String> privateKey = secrets.value("privateKey");
        if (privateKey.isPresent()) {
          out.putSensitive(SSL_CONTEXT, "Private Key", readablePrivateKey(privateKey.get()));
        }
      }
      default -> {
        // NONE, and a type the processor refuses as invalid
      }
    }
  }

  /**
   * NiFi reads the key only when it enables the SSL context and then retries the enable without
   * end, so a key in another form would fail the deployment only as a timeout.
   */
  private static String readablePrivateKey(String privateKey) throws FatalAdapterException {
    if (privateKey.startsWith("-----BEGIN PRIVATE KEY-----")
        || privateKey.startsWith("-----BEGIN RSA PRIVATE KEY-----")) {
      return privateKey;
    }
    throw new FatalAdapterException(
        AdapterErrorCode.NIFI_TEMPLATE_ERROR,
        "HTTP/REST source privateKey must be a PEM key in PKCS#8 form, or in PKCS#1 form for RSA");
  }

  /**
   * The secrets of an authentication. Decrypts the Data source on the first encrypted value. A
   * class, not a record, so that no {@code toString} shows a secret.
   */
  private final class Secrets {

    private final Map<String, Object> original;
    private final Map<String, Object> authentication;
    private Map<String, Object> plain;

    Secrets(Map<String, Object> original, Map<String, Object> authentication) {
      this.original = original;
      this.authentication = authentication;
    }

    /** The decrypted value of {@code field}, or empty when the Data source has none. */
    Optional<String> value(String field) throws FatalAdapterException {
      Object value = authentication.get(field);
      if (value == null || String.valueOf(value).isBlank()) {
        return Optional.empty();
      }
      if (!isEncrypted(value)) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_TEMPLATE_ERROR,
            "HTTP/REST source "
                + field
                + " must be encrypted (plaintext secrets are not accepted)");
      }
      if (plain == null) {
        plain = objectOrEmpty(credentials.decrypt(original).get("authentication"));
      }
      return Optional.of(String.valueOf(plain.get(field)));
    }
  }

  @Override
  public void registerControllerServices(BuildContext ctx) throws FatalAdapterException {
    if (!CLIENT_CERTIFICATE.equals(ctx.spec().sourceProperties().get(AUTHENTICATION_TYPE))) {
      ctx.addControllerService(Fragment.HTTP_SOURCE_WEB_CLIENT, WEB_CLIENT);
      return;
    }
    ctx.addControllerService(Fragment.HTTP_SOURCE_SSL_CONTEXT, SSL_CONTEXT);
    ctx.addControllerService(Fragment.HTTP_SOURCE_WEB_CLIENT, WEB_CLIENT);
    ctx.setControllerServiceReference(WEB_CLIENT, "SSL Context Service", SSL_CONTEXT);
  }

  @Override
  public StageResult build(BuildContext ctx) throws FatalAdapterException {
    Processor source = ctx.loadProcessor(Fragment.GET_HTTP_API_CALL, "success");
    BuildContext.applySchedule(source, ctx.spec().sourceCron());
    ctx.spec().sourceProperties().forEach((key, value) -> BuildContext.setProp(source, key, value));
    return new StageResult(
        List.of(source),
        List.of(source),
        List.of(new StopSource(source, "stop", "http.error.message")));
  }

  /** The processor validates each record of a response against this inlined JSON Schema. */
  private static Map<String, Object> dataStructure(
      Datasource source, Map<String, Map<String, Object>> artifacts) throws FatalAdapterException {
    if (!(source.getAdditionalProperties().get("element") instanceof String element)
        || element.isBlank()) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD,
          "HTTP/REST Data source '" + source.getId() + "' names no Data structure");
    }
    Map<String, Object> schema = artifacts.get(element);
    if (schema == null) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD,
          "HTTP/REST Data source '"
              + source.getId()
              + "' names the Data structure '"
              + element
              + "', which the payload's artifacts do not hold");
    }
    return schema;
  }

  private static Map<String, Object> selectApiCall(Object apiCalls, GraphNode node)
      throws FatalAdapterException {
    String name = node.apiCallName();
    if (name == null || name.isBlank()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "source node '" + node.id() + "' of an HTTP/REST source names no API call");
    }
    if (apiCalls instanceof List<?> calls) {
      for (Object call : calls) {
        if (call instanceof Map<?, ?> candidate && name.equals(candidate.get("name"))) {
          return objectOrEmpty(candidate);
        }
      }
    }
    throw new FatalAdapterException(
        AdapterErrorCode.NIFI_TEMPLATE_ERROR,
        "source node '"
            + node.id()
            + "' names the API call '"
            + name
            + "', which its Data source does not have");
  }

  private static String json(Map<String, Object> document) throws FatalAdapterException {
    try {
      return MAPPER.writeValueAsString(document);
    } catch (JsonProcessingException e) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_FLOW_ERROR, e, "HTTP/REST source property serialization");
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> objectOrEmpty(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }
}
