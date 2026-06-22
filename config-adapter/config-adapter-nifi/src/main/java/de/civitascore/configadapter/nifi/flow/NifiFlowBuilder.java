/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.ReplacementStrategy;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds a NiFi 2.x flow-snapshot JSON programmatically by composing curated per-component building
 * blocks (one processor/controller-service fragment each) according to the engine-neutral pipeline
 * graph. This is the single place that knows NiFi specifics: the adapter only ever emits known-safe
 * processors (ConsumeMQTT, ConvertRecord, UpdateRecord with RecordPath, PutDatabaseRecord, …) —
 * never scripting or Jolt — so a constrained graph can never smuggle arbitrary processors in. The
 * produced snapshot carries no secrets; sensitive controller-service properties are pushed
 * post-upload.
 */
public class NifiFlowBuilder {

  private static final String CS_TOKEN_PREFIX = "${CS:";
  private static final String STRATEGY_PROPERTY = "Replacement Value Strategy";

  private static final String READER = "JsonTreeReader";
  private static final String WRITER = "JsonRecordSetWriter";
  private static final String DBCP = "PostGISConnectionPool";

  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * The resolved inputs for one flow.
   *
   * @param processGroupName the process-group name
   * @param sourceType the datasource connector type
   * @param sourceProperties non-sensitive source processor properties (e.g. Broker URI, Topic
   *     Filter)
   * @param sinkType the datasink type
   * @param sinkProperties non-sensitive sink processor properties (e.g. Table Name)
   * @param mappingProperties compiled RecordPath properties (empty if the graph has no mapping
   *     node)
   * @param controllerServiceProperties non-sensitive controller-service properties, keyed by
   *     friendly name
   */
  public record FlowBuildSpec(
      String processGroupName,
      SourceType sourceType,
      Map<String, String> sourceProperties,
      SinkType sinkType,
      Map<String, String> sinkProperties,
      List<UpdateRecordProperty> mappingProperties,
      Map<String, Map<String, String>> controllerServiceProperties) {
    public FlowBuildSpec {
      Objects.requireNonNull(processGroupName, "processGroupName");
      Objects.requireNonNull(sourceType, "sourceType");
      Objects.requireNonNull(sinkType, "sinkType");
      sourceProperties = Map.copyOf(Objects.requireNonNull(sourceProperties, "sourceProperties"));
      sinkProperties = Map.copyOf(Objects.requireNonNull(sinkProperties, "sinkProperties"));
      mappingProperties =
          List.copyOf(Objects.requireNonNull(mappingProperties, "mappingProperties"));
      controllerServiceProperties =
          Map.copyOf(
              Objects.requireNonNull(controllerServiceProperties, "controllerServiceProperties"));
    }
  }

  private record Processor(ObjectNode node, String id, String outRelationship) {}

  /**
   * Builds the flow snapshot for the given spec.
   *
   * @param spec the resolved flow inputs
   * @return the NiFi flow-snapshot JSON
   * @throws FatalAdapterException if a source/sink combination is unsupported or a fragment is
   *     missing
   */
  public String build(FlowBuildSpec spec) throws FatalAdapterException {
    String pgId = deterministicId(spec.processGroupName());

    ObjectNode flow = mapper.createObjectNode();
    flow.put("identifier", pgId);
    flow.put("name", spec.processGroupName());
    flow.put("comments", "");
    flow.put("defaultFlowFileExpiration", "0 sec");
    flow.put("defaultBackPressureObjectThreshold", 10_000);
    flow.put("defaultBackPressureDataSizeThreshold", "1 GB");
    flow.put("flowFileConcurrency", "UNBOUNDED");
    flow.put("flowFileOutboundPolicy", "STREAM_WHEN_AVAILABLE");
    flow.put("scheduledState", "ENABLED");
    flow.put("executionEngine", "INHERITED");
    flow.put("maxConcurrentTasks", 1);
    flow.put("statelessFlowTimeout", "1 min");
    flow.put("componentType", "PROCESS_GROUP");
    ObjectNode pgPosition = flow.putObject("position");
    pgPosition.put("x", 0.0);
    pgPosition.put("y", 0.0);
    flow.putArray("processGroups");
    flow.putArray("remoteProcessGroups");
    flow.putArray("inputPorts");
    flow.putArray("outputPorts");
    flow.putArray("labels");
    flow.putArray("funnels");
    ArrayNode processors = flow.putArray("processors");
    ArrayNode controllerServices = flow.putArray("controllerServices");
    ArrayNode connections = flow.putArray("connections");

    // Controller services first — processors reference them by id.
    Map<String, String> csIdByName = new LinkedHashMap<>();
    addControllerService(controllerServices, "json_tree_reader", pgId, READER, csIdByName, spec);
    addControllerService(
        controllerServices, "json_record_set_writer", pgId, WRITER, csIdByName, spec);
    if (spec.sinkType() == SinkType.POSTGIS) {
      addControllerService(
          controllerServices, "dbcp_connection_pool", pgId, DBCP, csIdByName, spec);
    }

    // Processor chain: source -> convert -> [mapping...] -> sink. A mapping may need more than one
    // UpdateRecord because a single processor allows only one Replacement Value Strategy, so const
    // (literal-value) fields and record-path fields land on separate processors.
    Processor source =
        loadProcessor(sourceFragment(spec.sourceType()), pgId, csIdByName, "Message");
    Processor convert = loadProcessor("convert_record", pgId, csIdByName, "success");
    List<Processor> mappingProcessors = buildMappingProcessors(spec, pgId, csIdByName);
    Processor sink = loadProcessor(sinkFragment(spec.sinkType()), pgId, csIdByName, null);

    List<Processor> chain = new ArrayList<>();
    chain.add(source);
    chain.add(convert);
    chain.addAll(mappingProcessors);
    chain.add(sink);

    bindProperties(chain, spec);

    for (int i = 0; i < chain.size(); i++) {
      processors.add(chain.get(i).node());
      if (i > 0) {
        connections.add(connection(pgId, chain.get(i - 1), chain.get(i)));
      }
    }

    wireErrorSink(pgId, csIdByName, processors, connections, convert, mappingProcessors);

    ObjectNode root = mapper.createObjectNode();
    root.set("flowContents", flow);
    root.putObject("externalControllerServices");
    root.putObject("parameterContexts");
    root.putObject("parameterProviders");
    root.put("flowEncodingVersion", "1.0");
    root.put("latest", false);
    return serialize(root);
  }

  // ─── Fragment loading ───────────────────────────────────────────────────────

  private void addControllerService(
      ArrayNode target,
      String fragment,
      String pgId,
      String friendlyName,
      Map<String, String> csIdByName,
      FlowBuildSpec spec)
      throws FatalAdapterException {
    ObjectNode node = loadFragment(fragment);
    node.put("identifier", deterministicId(pgId + ":cs:" + friendlyName));
    node.put("groupIdentifier", pgId);
    // stamp the friendly name so the REST client can match sensitive properties by name post-upload
    node.put("name", friendlyName);
    ObjectNode props = (ObjectNode) node.get("properties");
    spec.controllerServiceProperties().getOrDefault(friendlyName, Map.of()).forEach(props::put);
    target.add(node);
    csIdByName.put(friendlyName, node.get("identifier").asText());
  }

  private Processor loadProcessor(
      String fragment, String pgId, Map<String, String> csIdByName, String outRelationship)
      throws FatalAdapterException {
    return loadProcessor(fragment, pgId, csIdByName, outRelationship, "");
  }

  /**
   * Loads a processor fragment with a deterministic id. The {@code discriminator} keeps ids unique
   * when the same fragment is instantiated more than once (e.g. one UpdateRecord per strategy).
   */
  private Processor loadProcessor(
      String fragment,
      String pgId,
      Map<String, String> csIdByName,
      String outRelationship,
      String discriminator)
      throws FatalAdapterException {
    ObjectNode node = loadFragment(fragment);
    String seed = pgId + ":proc:" + fragment + (discriminator.isEmpty() ? "" : ":" + discriminator);
    String id = deterministicId(seed);
    node.put("identifier", id);
    node.put("groupIdentifier", pgId);
    resolveControllerServiceReferences(node, csIdByName);
    return new Processor(node, id, outRelationship);
  }

  /** Replaces {@code ${CS:Name}} property tokens with the assigned controller-service id. */
  private void resolveControllerServiceReferences(
      ObjectNode component, Map<String, String> csIdByName) throws FatalAdapterException {
    JsonNode properties = component.get("properties");
    if (!(properties instanceof ObjectNode props)) {
      return;
    }
    var fields = props.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      JsonNode value = entry.getValue();
      if (value.isTextual() && value.asText().startsWith(CS_TOKEN_PREFIX)) {
        String name =
            value.asText().substring(CS_TOKEN_PREFIX.length(), value.asText().length() - 1);
        String id = csIdByName.get(name);
        if (id == null) {
          throw new FatalAdapterException(
              AdapterErrorCode.NIFI_FLOW_ERROR, "unresolved controller-service reference: " + name);
        }
        props.put(entry.getKey(), id);
      }
    }
  }

  private ObjectNode loadFragment(String fragment) throws FatalAdapterException {
    String resource = "fragments/" + fragment + ".json";
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_FLOW_ERROR, "missing NiFi component fragment: " + resource);
      }
      return (ObjectNode) mapper.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new FatalAdapterException(AdapterErrorCode.NIFI_FLOW_ERROR, e, "reading " + resource);
    }
  }

  // ─── Property binding ───────────────────────────────────────────────────────

  /**
   * One {@code UpdateRecord} per replacement-value strategy, in first-seen order. NiFi allows a
   * single strategy per processor, so a mapping that mixes {@code const} (literal-value) with
   * copies/concats (record-path-value) is split across processors chained in sequence.
   */
  private List<Processor> buildMappingProcessors(
      FlowBuildSpec spec, String pgId, Map<String, String> csIdByName)
      throws FatalAdapterException {
    Map<ReplacementStrategy, List<UpdateRecordProperty>> byStrategy = new LinkedHashMap<>();
    for (UpdateRecordProperty property : spec.mappingProperties()) {
      byStrategy.computeIfAbsent(property.strategy(), k -> new ArrayList<>()).add(property);
    }
    List<Processor> result = new ArrayList<>();
    for (Map.Entry<ReplacementStrategy, List<UpdateRecordProperty>> group : byStrategy.entrySet()) {
      Processor processor =
          loadProcessor("update_record", pgId, csIdByName, "success", group.getKey().name());
      applyMapping(
          (ObjectNode) processor.node().get("properties"), group.getKey(), group.getValue());
      result.add(processor);
    }
    return result;
  }

  private void bindProperties(List<Processor> chain, FlowBuildSpec spec) {
    for (Processor processor : chain) {
      String type = processor.node().path("type").asText();
      ObjectNode props = (ObjectNode) processor.node().get("properties");
      if (type.endsWith("ConsumeMQTT")) {
        spec.sourceProperties().forEach(props::put);
      } else if (type.endsWith("PutDatabaseRecord") || type.endsWith("InvokeHTTP")) {
        spec.sinkProperties().forEach(props::put);
      }
      // UpdateRecord properties are applied in buildMappingProcessors (per strategy group).
    }
  }

  private void applyMapping(
      ObjectNode props,
      ReplacementStrategy strategy,
      List<UpdateRecordProperty> mappingProperties) {
    List<String> stale = new ArrayList<>();
    props
        .fieldNames()
        .forEachRemaining(
            name -> {
              if (name.startsWith("/")) {
                stale.add(name);
              }
            });
    stale.forEach(props::remove);
    props.put(STRATEGY_PROPERTY, strategy.nifiValue());
    for (UpdateRecordProperty property : mappingProperties) {
      props.put(property.recordPath(), property.value());
    }
  }

  // ─── Error routing ──────────────────────────────────────────────────────────

  /**
   * Routes the {@code failure} relationships of the record processors to a LogMessage sink (WARN +
   * bulletin) instead of auto-terminating them. Otherwise a malformed message or an unmappable
   * record would be dropped silently — invisible, undiagnosable data loss. The graph shape is
   * intentionally stable: swapping LogMessage for a durable/recoverable dead-letter sink is a
   * later, isolated change.
   */
  private void wireErrorSink(
      String pgId,
      Map<String, String> csIdByName,
      ArrayNode processors,
      ArrayNode connections,
      Processor convert,
      List<Processor> mappingProcessors)
      throws FatalAdapterException {
    Processor errorSink = loadProcessor("log_message", pgId, csIdByName, null);
    processors.add(errorSink.node());
    connections.add(connection(pgId, convert, errorSink, "failure"));
    for (Processor mapping : mappingProcessors) {
      connections.add(connection(pgId, mapping, errorSink, "failure"));
    }
  }

  // ─── Connections ────────────────────────────────────────────────────────────

  private ObjectNode connection(String pgId, Processor source, Processor destination) {
    return connection(pgId, source, destination, source.outRelationship());
  }

  private ObjectNode connection(
      String pgId, Processor source, Processor destination, String relationship) {
    ObjectNode connection = mapper.createObjectNode();
    connection.put(
        "identifier",
        deterministicId(
            pgId + ":conn:" + source.id() + ":" + relationship + "->" + destination.id()));
    ObjectNode src = connection.putObject("source");
    src.put("id", source.id());
    src.put("type", "PROCESSOR");
    src.put("groupId", pgId);
    ObjectNode dst = connection.putObject("destination");
    dst.put("id", destination.id());
    dst.put("type", "PROCESSOR");
    dst.put("groupId", pgId);
    connection.put("groupIdentifier", pgId);
    ArrayNode rels = connection.putArray("selectedRelationships");
    rels.add(relationship);
    connection.put("backPressureObjectThreshold", 10_000);
    connection.put("backPressureDataSizeThreshold", "1 GB");
    connection.put("flowFileExpiration", "0 sec");
    connection.put("loadBalanceStrategy", "DO_NOT_LOAD_BALANCE");
    connection.put("loadBalanceCompression", "DO_NOT_COMPRESS");
    connection.put("labelIndex", 0);
    connection.put("zIndex", 0);
    connection.putArray("prioritizers");
    connection.putArray("bends");
    connection.put("componentType", "CONNECTION");
    return connection;
  }

  // ─── Helpers ────────────────────────────────────────────────────────────────

  private static String sourceFragment(SourceType sourceType) throws FatalAdapterException {
    return switch (sourceType) {
      case MQTT -> "consume_mqtt";
      case SQL ->
          throw new FatalAdapterException(
              AdapterErrorCode.NIFI_TEMPLATE_ERROR, "SQL source not yet supported");
    };
  }

  private static String sinkFragment(SinkType sinkType) {
    return switch (sinkType) {
      case POSTGIS -> "put_database_record";
      case FROST -> "invoke_http";
    };
  }

  private static String deterministicId(String seed) {
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
  }

  private String serialize(ObjectNode root) throws FatalAdapterException {
    try {
      return mapper.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_FLOW_ERROR, e, "snapshot serialization");
    }
  }
}
