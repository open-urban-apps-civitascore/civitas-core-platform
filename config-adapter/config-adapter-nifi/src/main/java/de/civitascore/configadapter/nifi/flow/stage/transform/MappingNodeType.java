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
import de.civitascore.configadapter.nifi.flow.stage.MappingSupport;
import de.civitascore.configadapter.nifi.flow.stage.SinkStage;
import de.civitascore.configadapter.nifi.flow.stage.TransformNodeType;
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkSpec;
import de.civitascore.configadapter.nifi.flow.stage.sink.SinkSpec;
import de.civitascore.configadapter.nifi.graph.NodeKind;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import de.civitascore.configadapter.nifi.mapping.CompiledMapping;
import de.civitascore.configadapter.nifi.mapping.CompiledTransform;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler;
import de.civitascore.configadapter.nifi.mapping.MappingConfig;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
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
    requireChainedStructures(mappingConfigs);
    if (sink.mappingSupport() == MappingSupport.NONE) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, sink.mappingRejectionMessage());
    }
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
      units.add(new CompiledMapping(compilation.flatProperties(), compilation.fork()));
      return new Compilation(units, compilation.plan());
    }
    for (MappingConfig config : mappingConfigs) {
      units.add(recordPathCompiler.compile(config, sink.geometryEncoding()));
    }
    return new Compilation(units, null);
  }

  /**
   * Rejects a chain whose neighbours disagree on the structure between them: node N+1 reads the
   * records node N writes, so N+1's source URN must be N's target URN — version included, since a
   * new version is a different shape.
   *
   * <p>Each node compiles against the paths it was authored with, never against the shape its
   * predecessor actually emits. A stale source therefore yields paths that resolve against nothing:
   * a field silently becomes NULL, and an array selector makes the fan-out target an array the
   * incoming record no longer has, dropping every record without a bulletin.
   *
   * <p>A node that declares neither URN is left alone — they are optional on the mapping, so
   * requiring them here would reject flows that deploy correctly today.
   */
  private static void requireChainedStructures(List<MappingConfig> configs)
      throws FatalAdapterException {
    for (int i = 1; i < configs.size(); i++) {
      String upstreamTarget = configs.get(i - 1).target();
      String ownSource = configs.get(i).source();
      if (upstreamTarget == null || ownSource == null || upstreamTarget.equals(ownSource)) {
        continue;
      }
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_MAPPING_ERROR,
          "mapping node "
              + (i + 1)
              + " reads structure '"
              + ownSource
              + "' but the preceding node writes '"
              + upstreamTarget
              + "': its paths would resolve against a shape the chain never produces; re-open the"
              + " mapping and rebuild it against the current structure");
    }
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
