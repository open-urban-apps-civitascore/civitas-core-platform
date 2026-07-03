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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.flow.stage.BuildContext;
import de.civitascore.configadapter.nifi.flow.stage.ConvertRecordStage;
import de.civitascore.configadapter.nifi.flow.stage.Fragment;
import de.civitascore.configadapter.nifi.flow.stage.Processor;
import de.civitascore.configadapter.nifi.flow.stage.RecordMappingStage;
import de.civitascore.configadapter.nifi.flow.stage.SourceCapability;
import de.civitascore.configadapter.nifi.flow.stage.SourceStage;
import de.civitascore.configadapter.nifi.flow.stage.StageRegistry;
import de.civitascore.configadapter.nifi.flow.stage.StageResult;
import de.civitascore.configadapter.nifi.flow.stage.TransformStage;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

  private static final String READER = "JsonTreeReader";
  private static final String WRITER = "JsonRecordSetWriter";

  private final ObjectMapper mapper = new ObjectMapper();
  private final StageRegistry registry;

  public NifiFlowBuilder(StageRegistry registry) {
    this.registry = registry;
  }

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
   * @param sourceCron the cron (Quartz) schedule for the source processor, or {@code null} to keep
   *     the source fragment's built-in schedule
   */
  public record FlowBuildSpec(
      String processGroupName,
      SourceType sourceType,
      Map<String, String> sourceProperties,
      SinkType sinkType,
      Map<String, String> sinkProperties,
      List<UpdateRecordProperty> mappingProperties,
      Map<String, Map<String, String>> controllerServiceProperties,
      String sourceCron) {
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
      // sourceCron is intentionally nullable — most flows keep the fragment's built-in schedule.
    }
  }

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

    BuildContext ctx =
        new BuildContext(mapper, pgId, spec, processors, controllerServices, connections);
    SourceStage sourceStage = registry.source(spec.sourceType());

    // Controller services first — processors reference them by id.
    addControllerServices(ctx, sourceStage, spec);

    // Processor chain: source -> [convert] -> [mapping...] -> sink. A mapping may need more than
    // one
    // UpdateRecord because a single processor allows only one Replacement Value Strategy, so const
    // (literal-value) fields and record-path fields land on separate processors. The convert step
    // exists only to turn a raw (non-record) source payload into records — the MQTT source emits
    // raw
    // bytes on 'Message'; the SQL source (QueryDatabaseTableRecord) already emits records on
    // 'success', so it is wired straight into the mapping/sink with no convert.
    boolean sqlSource = spec.sourceType() == SourceType.SQL;
    boolean frostSink = spec.sinkType() == SinkType.FROST;
    // The FROST find-or-create works on the SensorThings envelope ($.things/$.observations) that an
    // MQTT STA source delivers. A SQL source emits plain table records, which SplitJson would never
    // match — the flow would silently produce nothing. Reject the combination rather than deploy
    // it.
    if (frostSink && sqlSource) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "FROST sink requires an MQTT SensorThings source; a SQL source does not emit the STA"
              + " envelope");
    }
    // Common prefix: source -> [convert] -> [mapping...]. The sink stage differs: PostGIS/MQTT is a
    // single terminal processor (linear), FROST is a multi-stage find-or-create sub-flow.
    List<Processor> prefix = new ArrayList<>(sourceStage.build(ctx).chain());
    List<Processor> failureSources = new ArrayList<>();
    for (TransformStage transform : transformsFor(sourceStage, spec)) {
      StageResult result = transform.build(ctx);
      prefix.addAll(result.chain());
      failureSources.addAll(result.failureSources());
    }
    for (int i = 0; i < prefix.size(); i++) {
      ctx.addProcessor(prefix.get(i));
      if (i > 0) {
        ctx.addChainConnection(prefix.get(i - 1), prefix.get(i));
      }
    }
    Processor tail = prefix.get(prefix.size() - 1);
    registry.sink(spec.sinkType()).build(ctx, tail, failureSources);

    ObjectNode root = mapper.createObjectNode();
    root.set("flowContents", flow);
    root.putObject("externalControllerServices");
    root.putObject("parameterContexts");
    root.putObject("parameterProviders");
    root.put("flowEncodingVersion", "1.0");
    root.put("latest", false);
    return serialize(root);
  }

  // ─── Controller services ────────────────────────────────────────────────────

  /**
   * Adds the controller services the flow needs: the JSON reader/writer always (they are chain
   * infrastructure referenced by convert/mapping fragments), then the source stage's own services,
   * then the sink DB pool for a PostGIS sink. The order is part of the byte-stable snapshot
   * contract.
   */
  private void addControllerServices(BuildContext ctx, SourceStage sourceStage, FlowBuildSpec spec)
      throws FatalAdapterException {
    ctx.addControllerService(Fragment.JSON_TREE_READER, READER);
    ctx.addControllerService(Fragment.JSON_RECORD_SET_WRITER, WRITER);
    sourceStage.registerControllerServices(ctx);
    if (spec.sinkType() == SinkType.POSTGIS) {
      registry.sink(spec.sinkType()).registerControllerServices(ctx);
    }
  }

  // ─── Transforms ─────────────────────────────────────────────────────────────

  /**
   * The transforms between source and sink, derived structurally. ConvertRecord turns a raw source
   * payload into records — skipped when the source already emits records or the sink consumes the
   * raw JSON envelope (the FROST find-or-create works on SplitJson/EvaluateJsonPath, and a
   * record-based UpdateRecord mapping cannot run in that path: it would re-wrap the envelope).
   */
  private List<TransformStage> transformsFor(SourceStage source, FlowBuildSpec spec) {
    boolean rawJsonSink = spec.sinkType() == SinkType.FROST;
    List<TransformStage> transforms = new ArrayList<>();
    if (!rawJsonSink && !source.capabilities().contains(SourceCapability.EMITS_RECORDS)) {
      transforms.add(new ConvertRecordStage());
    }
    if (!rawJsonSink && !spec.mappingProperties().isEmpty()) {
      transforms.add(new RecordMappingStage());
    }
    return transforms;
  }

  // ─── Helpers ────────────────────────────────────────────────────────────────

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
