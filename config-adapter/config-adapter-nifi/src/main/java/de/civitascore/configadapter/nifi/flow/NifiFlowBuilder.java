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
import de.civitascore.configadapter.nifi.flow.stage.MappingSupport;
import de.civitascore.configadapter.nifi.flow.stage.PayloadForm;
import de.civitascore.configadapter.nifi.flow.stage.Processor;
import de.civitascore.configadapter.nifi.flow.stage.RecordMappingStage;
import de.civitascore.configadapter.nifi.flow.stage.SinkStage;
import de.civitascore.configadapter.nifi.flow.stage.SourceStage;
import de.civitascore.configadapter.nifi.flow.stage.StageRegistry;
import de.civitascore.configadapter.nifi.flow.stage.StageResult;
import de.civitascore.configadapter.nifi.flow.stage.TransformStage;
import de.civitascore.configadapter.nifi.mapping.FrostEnvelopePlan;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a NiFi 2.x flow-snapshot JSON by orchestrating the registered stages along the fixed chain
 * source → [convert] → [mapping...] → sink. All type knowledge lives in the stages; the builder
 * owns the chain order, the controller-service phase, and the snapshot envelope. Components are
 * minted exclusively from the curated fragment whitelist (never scripting), so a constrained graph
 * can never smuggle arbitrary processors in. The produced snapshot carries no secrets; sensitive
 * controller-service properties are pushed post-upload.
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
   * @param staEnvelope the compiled STA envelope rebuild plan for a mapped FROST sink, or {@code
   *     null} for every other flow
   */
  public record FlowBuildSpec(
      String processGroupName,
      SourceType sourceType,
      Map<String, String> sourceProperties,
      SinkType sinkType,
      Map<String, String> sinkProperties,
      List<UpdateRecordProperty> mappingProperties,
      Map<String, Map<String, String>> controllerServiceProperties,
      String sourceCron,
      FrostEnvelopePlan staEnvelope) {
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
      // The envelope plan exists exactly for a mapped FROST sink; any other carrier is a mis-wired
      // spec (same discipline as frostProjectId on PipelineDeploymentRequest).
      if (staEnvelope != null && (sinkType != SinkType.FROST || mappingProperties.isEmpty())) {
        throw new IllegalArgumentException(
            "staEnvelope is only valid for a FROST sink with a record mapping");
      }
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
    SinkStage sinkStage = registry.sink(spec.sinkType());
    requireCompatibleSource(sourceStage, sinkStage, !spec.mappingProperties().isEmpty());

    // Controller services first — processors reference them by id.
    addControllerServices(ctx, sourceStage, sinkStage);

    // Processor chain: source -> [convert] -> [mapping...] -> sink. A mapping may need more than
    // one
    // UpdateRecord because a single processor allows only one Replacement Value Strategy, so const
    // (literal-value) fields and record-path fields land on separate processors. The convert step
    // exists only to turn a raw (non-record) source payload into records — the MQTT source emits
    // raw
    // bytes on 'Message'; the SQL source (QueryDatabaseTableRecord) already emits records on
    // 'success', so it is wired straight into the mapping/sink with no convert.
    // Common prefix: source -> [convert] -> [mapping...]. The sink stage differs: PostGIS/MQTT is a
    // single terminal processor (linear), FROST is a multi-stage find-or-create sub-flow.
    List<Processor> prefix = new ArrayList<>(sourceStage.build(ctx).chain());
    List<Processor> failureSources = new ArrayList<>();
    for (TransformStage transform : transformsFor(sourceStage, sinkStage, spec)) {
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
    sinkStage.build(ctx, tail, failureSources);

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
  private void addControllerServices(BuildContext ctx, SourceStage sourceStage, SinkStage sinkStage)
      throws FatalAdapterException {
    ctx.addControllerService(Fragment.JSON_TREE_READER, READER);
    ctx.addControllerService(Fragment.JSON_RECORD_SET_WRITER, WRITER);
    sourceStage.registerControllerServices(ctx);
    sinkStage.registerControllerServices(ctx);
  }

  /** Rejects a source whose emitted payload form the sink can neither consume nor convert. */
  private static void requireCompatibleSource(
      SourceStage source, SinkStage sink, boolean mappingPresent) throws FatalAdapterException {
    PayloadForm offered = source.output();
    Set<PayloadForm> accepted = sink.acceptedInputs(mappingPresent);
    boolean convertible =
        accepted.contains(PayloadForm.RECORDS)
            && PayloadForm.CONVERTIBLE_TO_RECORDS.contains(offered);
    if (!accepted.contains(offered) && !convertible) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, sink.inputRejectionMessage(mappingPresent));
    }
  }

  // ─── Transforms ─────────────────────────────────────────────────────────────

  /**
   * The transforms between source and sink, derived structurally. The record chain (ConvertRecord
   * when the source does not already emit records, then the mapping) runs whenever the sink
   * consumes {@link PayloadForm#RECORDS} for this flow — which for an {@link
   * MappingSupport#ENVELOPE} sink is exactly the mapped case, whose sink-owned pre-region rebuilds
   * the mapped records into its envelope.
   */
  private List<TransformStage> transformsFor(SourceStage source, SinkStage sink, FlowBuildSpec spec)
      throws FatalAdapterException {
    boolean mapped = !spec.mappingProperties().isEmpty();
    // The builder is reachable directly (golden tests), not only through the planner: a compiled
    // mapping heading into an envelope-mode sink without an envelope rebuild plan would silently
    // vanish inside the envelope — fail the build instead.
    if (mapped && sink.mappingSupport() == MappingSupport.ENVELOPE && spec.staEnvelope() == null) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "a record mapping was compiled for a raw-JSON sink but no envelope plan was built");
    }
    List<TransformStage> transforms = new ArrayList<>();
    boolean recordChain = sink.acceptedInputs(mapped).contains(PayloadForm.RECORDS);
    if (recordChain && source.output() != PayloadForm.RECORDS) {
      transforms.add(new ConvertRecordStage());
    }
    if (mapped) {
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
