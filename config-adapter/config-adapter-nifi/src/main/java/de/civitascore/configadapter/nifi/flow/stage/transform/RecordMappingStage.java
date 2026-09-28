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

  private static final String FORK_GUARD_DISCRIMINATOR = "FORKGUARD";

  /**
   * The guard's routing property, and with it the relationship an empty fan-out leaves on. Named
   * {@code failure} so the sink stage's existing failure routing carries it to the error sink.
   */
  private static final String FORK_GUARD_FAILURE_PROPERTY = "failure";

  /** {@code ForkRecord} writes {@code record.count} on every FlowFile it emits on {@code fork}. */
  private static final String NO_RECORDS = "${record.count:equals('0')}";

  private final CompiledMapping mapping;
  private final int chainIndex;

  /**
   * Creates the stage for one mapping node.
   *
   * @param mapping the node's compiled unit
   * @param chainIndex the node's position in the mapping chain; part of the deterministic
   *     processor-id seed for every chain position after the first, so each node's UpdateRecords
   *     keep distinct ids. The first position deliberately omits the index (discriminated by
   *     strategy alone), so a single-mapping flow's snapshot is unchanged by the chain support.
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
    List<Processor> failureSources = new ArrayList<>();
    if (mapping.fork().required()) {
      Processor fork = buildFork(ctx);
      result.add(fork);
      failureSources.add(fork);
      Processor guard = buildForkGuard(ctx);
      result.add(guard);
      failureSources.add(guard);
    }
    for (Map.Entry<ReplacementStrategy, List<UpdateRecordProperty>> group : byStrategy.entrySet()) {
      Processor processor =
          ctx.loadProcessor(
              Fragment.UPDATE_RECORD, "success", discriminator(group.getKey().name()));
      applyMapping(processor, group.getKey(), group.getValue());
      result.add(processor);
      failureSources.add(processor);
    }
    return new StageResult(result, failureSources);
  }

  /**
   * The fan-out ahead of this node's own properties: one record per source array element, so the
   * mapping's paths — compiled against the post-fork shape — resolve element-wise.
   *
   * <p>Its id seed is deliberately independent of the strategy discriminators used below, so adding
   * a fan-out to a mapping leaves the ids of the existing {@code UpdateRecord}s untouched and the
   * snapshot stays byte-stable except for the fan-out itself.
   *
   * <p>Output leaves on {@code fork}, not {@code success} — {@code original} carries the unforked
   * input and is auto-terminated by the fragment. Its third relationship, {@code failure}, carries
   * a FlowFile whose records the reader could not parse at all; the sink stage routes it to the
   * error sink like any other failure source, which is also what keeps the processor valid.
   */
  private Processor buildFork(BuildContext ctx) throws FatalAdapterException {
    Processor fork =
        ctx.loadProcessor(Fragment.FORK_RECORD, "fork", discriminator(FORK_DISCRIMINATOR));
    BuildContext.setProp(fork, FORK_PATH_PROPERTY, mapping.fork().recordPath());
    return fork;
  }

  /**
   * Fails a fan-out that produced no record at all. {@code ForkRecord} skips an element it cannot
   * extract — a scalar, a null, a non-array at the fork path — with a debug line only, and an empty
   * FlowFile then travels the whole chain and is written as a success, so an operator cannot
   * distinguish it from "no data arrived". The guard turns every such shape into a logged drop.
   *
   * <p>Records leave on {@code unmatched}; the empty ones leave on {@code failure}, which the sink
   * stage routes to the shared error sink like any other failure source.
   */
  private Processor buildForkGuard(BuildContext ctx) throws FatalAdapterException {
    Processor guard =
        ctx.loadProcessor(
            Fragment.ROUTE_ON_ATTRIBUTE, "unmatched", discriminator(FORK_GUARD_DISCRIMINATOR));
    BuildContext.setExpression(guard, FORK_GUARD_FAILURE_PROPERTY, NO_RECORDS);
    BuildContext.removeAutoTerminated(guard, "unmatched");
    return guard;
  }

  private String discriminator(String seed) {
    return chainIndex == 0 ? seed : seed + ":" + chainIndex;
  }

  private static void applyMapping(
      Processor processor,
      ReplacementStrategy strategy,
      List<UpdateRecordProperty> mappingProperties) {
    BuildContext.setProp(processor, STRATEGY_PROPERTY, strategy.nifiValue());
    for (UpdateRecordProperty property : mappingProperties) {
      BuildContext.setProp(processor, property.recordPath(), property.value());
    }
  }
}
