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
import java.util.function.BiConsumer;

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

  private static final Map<String, List<String>> SECRET_FIELDS =
      Map.of(
          BASIC, List.of("password"),
          BEARER_TOKEN, List.of("token"),
          API_TOKEN, List.of("value"),
          OAUTH2, List.of("clientSecret"),
          CLIENT_CERTIFICATE, List.of("certificate", "privateKey"));

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
    Secrets secrets =
        secrets(original, authentication, SECRET_FIELDS.getOrDefault(type, List.of()));

    putIfPresent(out::putSourceProperty, "Base URL", original.get("baseUrl"));
    out.putSourceProperty("API Call", json(apiCall));
    out.putSourceProperty("Record Schema", json(recordSchema));
    out.putSourceProperty(AUTHENTICATION_TYPE, type);
    BiConsumer<String, String> processorSecret =
        (key, value) -> out.putSensitive(HTTP_PROCESSOR, key, value);
    switch (type) {
      case BASIC -> {
        putIfPresent(out::putSourceProperty, "Basic Auth Username", authentication.get("username"));
        secrets.put(processorSecret, "Basic Auth Password", "password");
      }
      case BEARER_TOKEN -> secrets.put(processorSecret, "Bearer Token", "token");
      case API_TOKEN -> {
        putIfPresent(out::putSourceProperty, "API Token Name", authentication.get("name"));
        putIfPresent(out::putSourceProperty, "API Token Location", authentication.get("in"));
        secrets.put(processorSecret, "API Token Value", "value");
      }
      case OAUTH2 -> {
        putIfPresent(out::putSourceProperty, "OAuth2 Token URL", authentication.get("tokenUrl"));
        putIfPresent(out::putSourceProperty, "OAuth2 Client ID", authentication.get("clientId"));
        putIfPresent(out::putSourceProperty, "OAuth2 Scope", authentication.get("scope"));
        secrets.put(processorSecret, "OAuth2 Client Secret", "clientSecret");
      }
      case CLIENT_CERTIFICATE -> {
        secrets.put(
            (key, value) -> out.putControllerServiceProperty(SSL_CONTEXT, key, value),
            "Certificate Chain",
            "certificate");
        secrets.put(
            (key, value) -> out.putSensitive(SSL_CONTEXT, key, value), "Private Key", "privateKey");
      }
      default -> {
        // NONE, and a type the processor refuses as invalid
      }
    }
  }

  /** Refuses a plaintext secret and decrypts the Data source once when a secret is present. */
  private Secrets secrets(
      Map<String, Object> original, Map<String, Object> authentication, List<String> fields)
      throws FatalAdapterException {
    boolean encrypted = false;
    for (String field : fields) {
      Object value = authentication.get(field);
      if (value != null && !String.valueOf(value).isBlank() && !isEncrypted(value)) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_TEMPLATE_ERROR,
            "HTTP/REST source "
                + field
                + " must be encrypted (plaintext secrets are not accepted)");
      }
      encrypted |= isEncrypted(value);
    }
    Map<String, Object> plain =
        encrypted ? objectOrEmpty(credentials.decrypt(original).get("authentication")) : Map.of();
    return new Secrets(authentication, plain);
  }

  /**
   * The encrypted fields of an authentication with their decrypted values. A class, not a record,
   * so that no {@code toString} shows a secret.
   */
  private static final class Secrets {

    private final Map<String, Object> encrypted;
    private final Map<String, Object> plain;

    Secrets(Map<String, Object> encrypted, Map<String, Object> plain) {
      this.encrypted = encrypted;
      this.plain = plain;
    }

    /** Gives {@code target} the decrypted value of {@code field} if the Data source has one. */
    void put(BiConsumer<String, String> target, String key, String field) {
      if (isEncrypted(encrypted.get(field))) {
        target.accept(key, String.valueOf(plain.get(field)));
      }
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
