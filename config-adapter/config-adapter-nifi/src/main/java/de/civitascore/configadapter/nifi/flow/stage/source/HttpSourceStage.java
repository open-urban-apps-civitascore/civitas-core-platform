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

/**
 * HTTP/REST pull source over GetHttpApiCall. The stage maps no field of the API call, so a new
 * setting of an API call needs no change here; the processor is the one check on its shape.
 */
public final class HttpSourceStage implements SourceStage {

  /** Friendly name the REST client matches for the post-upload sensitive-property push. */
  private static final String HTTP_PROCESSOR = "HttpApiCall";

  private static final String WEB_CLIENT = "HttpSourceWebClient";

  private static final String NONE = "NONE";
  private static final String BASIC = "BASIC";

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
    Object password = authentication.get("password");
    if (password != null && !String.valueOf(password).isBlank() && !isEncrypted(password)) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "HTTP/REST source password must be encrypted (plaintext secrets are not accepted)");
    }

    putIfPresent(out::putSourceProperty, "Base URL", original.get("baseUrl"));
    out.putSourceProperty("API Call", json(apiCall));
    out.putSourceProperty("Record Schema", json(recordSchema));
    String type = authentication.get("type") instanceof String text ? text : NONE;
    out.putSourceProperty("Authentication Type", type);
    if (BASIC.equals(type)) {
      putIfPresent(out::putSourceProperty, "Basic Auth Username", authentication.get("username"));
      if (isEncrypted(password)) {
        Map<String, Object> plain =
            objectOrEmpty(credentials.decrypt(original).get("authentication"));
        out.putSensitive(
            HTTP_PROCESSOR, "Basic Auth Password", String.valueOf(plain.get("password")));
      }
    }
  }

  @Override
  public void registerControllerServices(BuildContext ctx) throws FatalAdapterException {
    ctx.addControllerService(Fragment.HTTP_SOURCE_WEB_CLIENT, WEB_CLIENT);
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
