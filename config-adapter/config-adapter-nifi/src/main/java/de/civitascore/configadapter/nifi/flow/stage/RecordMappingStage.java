/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage;

import com.fasterxml.jackson.databind.node.ObjectNode;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.ReplacementStrategy;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The compiled record mapping as a chain of {@code UpdateRecord} processors — one per
 * replacement-value strategy, in first-seen order. NiFi allows a single strategy per processor, so
 * a mapping that mixes {@code const} (literal-value) with copies/concats (record-path-value) is
 * split across processors chained in sequence.
 */
public final class RecordMappingStage implements TransformStage {

  private static final String STRATEGY_PROPERTY = "Replacement Value Strategy";

  @Override
  public StageResult build(BuildContext ctx) throws FatalAdapterException {
    Map<ReplacementStrategy, List<UpdateRecordProperty>> byStrategy = new LinkedHashMap<>();
    for (UpdateRecordProperty property : ctx.spec().mappingProperties()) {
      byStrategy.computeIfAbsent(property.strategy(), k -> new ArrayList<>()).add(property);
    }
    List<Processor> result = new ArrayList<>();
    for (Map.Entry<ReplacementStrategy, List<UpdateRecordProperty>> group : byStrategy.entrySet()) {
      Processor processor =
          ctx.loadProcessor(Fragment.UPDATE_RECORD, "success", group.getKey().name());
      applyMapping(
          (ObjectNode) processor.node().get("properties"), group.getKey(), group.getValue());
      result.add(processor);
    }
    return new StageResult(result, result);
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
