/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.transform;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.CoreUrn;
import de.civitascore.configadapter.nifi.flow.stage.MappingSupport;
import de.civitascore.configadapter.nifi.flow.stage.SinkStage;
import de.civitascore.configadapter.nifi.flow.stage.TransformNodeType;
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkSpec;
import de.civitascore.configadapter.nifi.flow.stage.sink.PostgisSinkSpec;
import de.civitascore.configadapter.nifi.flow.stage.sink.SinkSpec;
import de.civitascore.configadapter.nifi.graph.NodeKind;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import de.civitascore.configadapter.nifi.mapping.CompiledMapping;
import de.civitascore.configadapter.nifi.mapping.CompiledTransform;
import de.civitascore.configadapter.nifi.mapping.ForkPlan;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler;
import de.civitascore.configadapter.nifi.mapping.MappingConfig;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import de.civitascore.configadapter.nifi.mapping.ValueNode;
import java.util.ArrayList;
import java.util.List;

/**
 * The mapping node kind: parses each node's {@code mappingConfig} and compiles the chain to
 * RecordPath (directly for a records sink, or via the FROST mapping compiler for a mapped FROST
 * sink). For a FROST sink the intermediate mappings stay plain record transforms while the LAST
 * mapping targets the sink's Thing-shaped structure and compiles into flat intermediate fields plus
 * the entity plan the sink's build half turns into its find-or-create chain.
 */
public final class MappingNodeType implements TransformNodeType {

  private static final String KEY_MAPPING_CONFIG = "mappingConfig";

  private final ObjectMapper mapper = new ObjectMapper();
  private final MappingConfigParser mappingConfigParser;
  private final RecordPathCompiler recordPathCompiler;
  private final FrostMappingCompiler frostMappingCompiler;

  /**
   * Creates the mapping kind.
   *
   * @param mappingConfigParser the mapping parser
   * @param recordPathCompiler the RecordPath compiler
   */
  public MappingNodeType(
      MappingConfigParser mappingConfigParser, RecordPathCompiler recordPathCompiler) {
    this.mappingConfigParser = mappingConfigParser;
    this.recordPathCompiler = recordPathCompiler;
    this.frostMappingCompiler = new FrostMappingCompiler(recordPathCompiler);
  }

  @Override
  public NodeKind kind() {
    return NodeKind.MAPPING;
  }

  @Override
  public Compilation compile(List<GraphNode> ownNodes, SinkStage<?> sink, SinkSpec sinkSpec)
      throws FatalAdapterException {
    List<MappingConfig> mappingConfigs = parse(ownNodes);
    if (sink.mappingSupport() == MappingSupport.NONE) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, sink.mappingRejectionMessage());
    }
    requireChainedStructures(ownNodes, mappingConfigs);
    List<CompiledTransform> units = new ArrayList<>();
    if (sink.mappingSupport() == MappingSupport.ENVELOPE) {
      // The last mapping targets the sink's Thing-shaped structure; the ones before it are
      // ordinary record transformations between structures.
      if (!(sinkSpec instanceof FrostSinkSpec frost) || frost.staProperties() == null) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_TEMPLATE_ERROR,
            "a mapped FROST pipeline requires the mapping's target data structure on the FROST"
                + " datasink; re-publish the dataset");
      }
      List<MappingConfig> intermediate = mappingConfigs.subList(0, mappingConfigs.size() - 1);
      for (MappingConfig config : intermediate) {
        units.add(recordPathCompiler.compile(config, sink.geometryEncoding()));
      }
      FrostMappingCompiler.FrostCompilation compilation =
          frostMappingCompiler.compile(
              mappingConfigs.get(mappingConfigs.size() - 1), frost.staProperties());
      units.add(compilation.mapping());
      return new Compilation(units, compilation.plan());
    }
    for (MappingConfig config : mappingConfigs) {
      CompiledMapping compiled = recordPathCompiler.compile(config, sink.geometryEncoding());
      requireElementLevelKey(compiled, config, sinkSpec);
      units.add(compiled);
    }
    return new Compilation(units, null);
  }

  /**
   * Rejects a fan-out whose rows all carry the same primary key. With a key the sink writes UPSERT
   * keyed on it, and {@code PutDatabaseRecord} batches each record as its own {@code ON CONFLICT DO
   * UPDATE}: N elements sharing one key overwrite each other down to a single row, last element
   * winning, with no failure route and no error — the batching is what keeps Postgres from raising
   * its usual "cannot affect row a second time".
   *
   * <p>A key column is element-level when its rule reads a source below the fork's innermost array,
   * which is exactly what {@link ForkPlan#required()} rewrites to a root-level path.
   */
  private static void requireElementLevelKey(
      CompiledMapping compiled, MappingConfig config, SinkSpec sinkSpec)
      throws FatalAdapterException {
    if (!compiled.fork().required() || !(sinkSpec instanceof PostgisSinkSpec postgis)) {
      return;
    }
    List<String> keyColumns = postgis.primaryKeyColumns();
    if (keyColumns.isEmpty() || keyColumns.stream().anyMatch(key -> readsAnElement(config, key))) {
      return;
    }
    throw new FatalAdapterException(
        AdapterErrorCode.NIFI_MAPPING_ERROR,
        "the fan-out over '"
            + compiled.fork().recordPath()
            + "' writes one row per element, but every primary-key column ("
            + String.join(", ", keyColumns)
            + ") is mapped from outside that array, so all rows would share one key and overwrite"
            + " each other down to one; map a field from below the array onto a key column");
  }

  /** Whether the rule writing {@code column} reads a source below the fan-out's innermost array. */
  private static boolean readsAnElement(MappingConfig config, String column) {
    ValueNode rule = config.fields().get("$." + column);
    return rule != null && ForkPlan.readsBelowTheArray(rule);
  }

  /**
   * Rejects a chain whose neighbours disagree on the structure between them. Each node compiles
   * against the paths it was authored with, never against the shape its predecessor actually emits,
   * so a stale declaration is not recoverable at runtime: paths resolve against nothing, and an
   * array selector makes the fan-out target an array the record no longer has.
   *
   * <p>Compared on structure identity and version, not on the URN string: the name segment is a
   * display name, so a rename leaves the shape untouched and must not fail a deploy. A version bump
   * is a different shape and does fail — the downstream node's paths were authored against the old
   * one.
   *
   * <p>A pair that declares neither URN is left alone: they are optional on a mapping. One side
   * declaring is rejected because the handover cannot be verified at all — the editor writes both
   * URNs or neither, but a chain half-migrated by editing only one node reaches this too.
   */
  private static void requireChainedStructures(
      List<GraphNode> ownNodes, List<MappingConfig> configs) throws FatalAdapterException {
    for (int i = 1; i < configs.size(); i++) {
      String upstreamTarget = configs.get(i - 1).target();
      String ownSource = configs.get(i).source();
      if (upstreamTarget == null && ownSource == null) {
        continue;
      }
      if (upstreamTarget == null || ownSource == null) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_MAPPING_ERROR,
            "mapping node '"
                + ownNodes.get(i).id()
                + "' declares "
                + describe("source", ownSource)
                + " while the preceding node '"
                + ownNodes.get(i - 1).id()
                + "' declares "
                + describe("target", upstreamTarget)
                + ": the handover between them cannot be verified; re-open both mappings and save"
                + " them again");
      }
      if (!CoreUrn.sameStructureVersion(upstreamTarget, ownSource)) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_MAPPING_ERROR,
            "mapping node '"
                + ownNodes.get(i).id()
                + "' reads structure '"
                + ownSource
                + "' but the preceding node '"
                + ownNodes.get(i - 1).id()
                + "' writes '"
                + upstreamTarget
                + "': its paths would resolve against a shape the chain never produces; re-open the"
                + " mapping and rebuild it against the current structure");
      }
    }
  }

  private static String describe(String side, String structure) {
    if (structure == null) {
      return "no " + side + " structure";
    }
    return side + " structure '" + structure + "'";
  }

  /** Parses each node's config, in flow order. */
  private List<MappingConfig> parse(List<GraphNode> ownNodes) throws FatalAdapterException {
    List<MappingConfig> configs = new ArrayList<>();
    for (GraphNode node : ownNodes) {
      Object rawConfig = node.data().get(KEY_MAPPING_CONFIG);
      if (rawConfig == null) {
        // A wired mapping node must carry a config; a missing one is a corrupted payload that
        // would otherwise deploy untransformed. (A pipeline with no mapping node at all is fine.)
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_TEMPLATE_ERROR, "mapping node has no mappingConfig");
      }
      configs.add(mappingConfigParser.parse(mapper.valueToTree(rawConfig)));
    }
    return configs;
  }
}
