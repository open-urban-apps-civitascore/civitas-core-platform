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
import java.util.Map;

/**
 * The mapping node kind: resolves each node's {@code mappingRef} against the pipeline's shipped
 * mappings catalog and compiles the chain to RecordPath (directly for a records sink, or via the
 * FROST mapping compiler for a mapped FROST sink). For a FROST sink the intermediate mappings stay
 * plain record transforms while the LAST mapping targets the sink's Thing-shaped structure and
 * compiles into flat intermediate fields plus the entity plan the sink's build half turns into its
 * find-or-create chain.
 */
public final class MappingNodeType implements TransformNodeType {

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
  public Compilation compile(
      List<GraphNode> ownNodes, SinkStage<?> sink, SinkSpec sinkSpec, Map<String, Object> mappings)
      throws FatalAdapterException {
    List<MappingConfig> mappingConfigs = parse(ownNodes, mappings);
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
        units.add(new CompiledMapping(recordPathCompiler.compile(config, sink.geometryEncoding())));
      }
      FrostMappingCompiler.FrostCompilation compilation =
          frostMappingCompiler.compile(
              mappingConfigs.get(mappingConfigs.size() - 1), frost.staProperties());
      units.add(new CompiledMapping(compilation.flatProperties()));
      return new Compilation(units, compilation.plan());
    }
    for (MappingConfig config : mappingConfigs) {
      units.add(new CompiledMapping(recordPathCompiler.compile(config, sink.geometryEncoding())));
    }
    return new Compilation(units, null);
  }

  /**
   * Resolves each node's {@code mappingRef} against the shipped catalog and parses the mapping
   * document, in flow order. The config-adapter is callback-free, so the referenced Mapping's
   * content must have travelled in the pipeline's {@code mappings} catalog; a wired mapping node
   * with no ref, or one whose ref is not shipped, is a corrupted payload that would otherwise
   * deploy untransformed.
   */
  private List<MappingConfig> parse(List<GraphNode> ownNodes, Map<String, Object> mappings)
      throws FatalAdapterException {
    List<MappingConfig> configs = new ArrayList<>();
    for (GraphNode node : ownNodes) {
      String ref = node.mappingRef();
      if (ref == null || ref.isBlank()) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_TEMPLATE_ERROR, "mapping node has no mappingRef");
      }
      Object document = lookup(mappings, ref);
      if (!(document instanceof Map<?, ?> map)) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_TEMPLATE_ERROR,
            "mapping '" + ref + "' was not shipped in the pipeline's mappings catalog");
      }
      configs.add(mappingConfigParser.parse(mapper.valueToTree(map)));
    }
    return configs;
  }

  /**
   * The mapping document for {@code ref} from the shipped catalog, tolerating a version drift
   * between the pipeline's pinned {@code mappingRef} and the catalog key (exact match first, then a
   * logical-URN match), or {@code null} when absent.
   */
  private static Object lookup(Map<String, Object> mappings, String ref) {
    Object exact = mappings.get(ref);
    if (exact != null) {
      return exact;
    }
    for (Map.Entry<String, Object> entry : mappings.entrySet()) {
      if (CoreUrn.sameArtifact(entry.getKey(), ref)) {
        return entry.getValue();
      }
    }
    return null;
  }
}
