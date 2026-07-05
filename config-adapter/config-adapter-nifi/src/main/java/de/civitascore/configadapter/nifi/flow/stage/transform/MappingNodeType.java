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
import de.civitascore.configadapter.nifi.graph.NodeKind;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import de.civitascore.configadapter.nifi.mapping.CompiledMapping;
import de.civitascore.configadapter.nifi.mapping.CompiledTransform;
import de.civitascore.configadapter.nifi.mapping.MappingConfig;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import de.civitascore.configadapter.nifi.mapping.StaEnvelopeCompiler;
import java.util.ArrayList;
import java.util.List;

/**
 * The mapping node kind: parses each node's {@code mappingConfig} and compiles the chain to
 * RecordPath (directly for a records sink, or via the STA envelope compiler for a mapped FROST
 * sink). For an envelope sink the intermediate mappings stay plain record transforms while the LAST
 * mapping before the sink carries the STA target paths and compiles into flat intermediate fields
 * plus the envelope rebuild plan the sink's build half turns into the split/capture/ReplaceText
 * pre-region.
 */
public final class MappingNodeType implements TransformNodeType {

  private static final String KEY_MAPPING_CONFIG = "mappingConfig";

  private final ObjectMapper mapper = new ObjectMapper();
  private final MappingConfigParser mappingConfigParser;
  private final RecordPathCompiler recordPathCompiler;
  private final StaEnvelopeCompiler staEnvelopeCompiler;

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
    this.staEnvelopeCompiler = new StaEnvelopeCompiler(recordPathCompiler);
  }

  @Override
  public NodeKind kind() {
    return NodeKind.MAPPING;
  }

  @Override
  public Compilation compile(List<GraphNode> ownNodes, SinkStage<?> sink)
      throws FatalAdapterException {
    List<MappingConfig> mappingConfigs = parse(ownNodes);
    if (sink.mappingSupport() == MappingSupport.NONE) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, sink.mappingRejectionMessage());
    }
    List<CompiledTransform> units = new ArrayList<>();
    if (sink.mappingSupport() == MappingSupport.ENVELOPE) {
      for (MappingConfig config : mappingConfigs.subList(0, mappingConfigs.size() - 1)) {
        units.add(new CompiledMapping(recordPathCompiler.compile(config, sink.geometryEncoding())));
      }
      StaEnvelopeCompiler.EnvelopeCompilation compilation =
          staEnvelopeCompiler.compile(mappingConfigs.get(mappingConfigs.size() - 1));
      units.add(new CompiledMapping(compilation.flatProperties()));
      return new Compilation(units, compilation.plan());
    }
    for (MappingConfig config : mappingConfigs) {
      units.add(new CompiledMapping(recordPathCompiler.compile(config, sink.geometryEncoding())));
    }
    return new Compilation(units, null);
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
