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

import com.fasterxml.jackson.databind.node.ObjectNode;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.nifi.flow.stage.BuildContext;
import de.civitascore.configadapter.nifi.flow.stage.Fragment;
import de.civitascore.configadapter.nifi.flow.stage.Processor;
import de.civitascore.configadapter.nifi.flow.stage.StageResult;
import de.civitascore.configadapter.nifi.flow.stage.TransformStage;
import de.civitascore.configadapter.nifi.mapping.CompiledMapping;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.ReplacementStrategy;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One mapping node's compiled unit as a chain of {@code UpdateRecord} processors — one per
 * replacement-value strategy, in first-seen order. NiFi allows a single strategy per processor, so
 * a mapping that mixes {@code const} (literal-value) with copies/concats (record-path-value) is
 * split across processors chained in sequence.
 */
public final class RecordMappingStage implements TransformStage {

  private static final String STRATEGY_PROPERTY = "Replacement Value Strategy";

  /**
   * ForkRecord takes the fork target as a dynamic property whose VALUE is the RecordPath — the
   * opposite of UpdateRecord, where the property name is the path. The name is therefore free and
   * only needs to be stable, since it is part of the flow snapshot.
   */
  private static final String FORK_PATH_PROPERTY = "fan-out";

  private static final String FORK_DISCRIMINATOR = "FORK";

  private final CompiledMapping mapping;
  private final int chainIndex;

  /**
   * Creates the stage for one mapping node.
   *
   * @param mapping the node's compiled unit
   * @param chainIndex the node's position in the mapping chain; part of the deterministic
   *     processor-id seed for every chain position after the first, so a redeploy maps each
   *     UpdateRecord back to the same NiFi component. The first position deliberately omits the
   *     index (discriminated by strategy alone), so the ids of single-mapping flows already
   *     deployed to a live NiFi stay stable across redeploys.
   */
  public RecordMappingStage(CompiledMapping mapping, int chainIndex) {
    this.mapping = mapping;
    this.chainIndex = chainIndex;
  }

  @Override
  public StageResult build(BuildContext ctx) throws FatalAdapterException {
    Map<ReplacementStrategy, List<UpdateRecordProperty>> byStrategy = new LinkedHashMap<>();
    for (UpdateRecordProperty property : mapping.properties()) {
      byStrategy.computeIfAbsent(property.strategy(), k -> new ArrayList<>()).add(property);
    }
    List<Processor> result = new ArrayList<>();
    if (mapping.fork().required()) {
      result.add(buildFork(ctx));
    }
    for (Map.Entry<ReplacementStrategy, List<UpdateRecordProperty>> group : byStrategy.entrySet()) {
      String discriminator =
          chainIndex == 0 ? group.getKey().name() : group.getKey().name() + ":" + chainIndex;
      Processor processor = ctx.loadProcessor(Fragment.UPDATE_RECORD, "success", discriminator);
      applyMapping(
          (ObjectNode) processor.node().get("properties"), group.getKey(), group.getValue());
      result.add(processor);
    }
    return new StageResult(result, result);
  }

  /**
   * The fan-out ahead of this node's own properties: one record per source array element, so the
   * mapping's paths — compiled against the post-fork shape — resolve element-wise.
   *
   * <p>Its id seed is deliberately independent of the strategy discriminators used below, so adding
   * a fan-out to a mapping leaves the ids of the existing {@code UpdateRecord}s untouched and a
   * redeploy still matches them to the same live NiFi components.
   *
   * <p>Output leaves on {@code fork}, not {@code success} — {@code original} carries the unforked
   * input and is auto-terminated by the fragment.
   */
  private Processor buildFork(BuildContext ctx) throws FatalAdapterException {
    String discriminator =
        chainIndex == 0 ? FORK_DISCRIMINATOR : FORK_DISCRIMINATOR + ":" + chainIndex;
    Processor fork = ctx.loadProcessor(Fragment.FORK_RECORD, "fork", discriminator);
    BuildContext.setProp(fork, FORK_PATH_PROPERTY, mapping.fork().recordPath());
    return fork;
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
}
