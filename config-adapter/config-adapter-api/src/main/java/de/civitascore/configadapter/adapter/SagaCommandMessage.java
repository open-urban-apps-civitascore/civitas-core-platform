/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.adapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Incoming saga command message dispatched by the saga orchestrator (the Flowable engine in the
 * config-adapter). Deserialized from the flat JSON structure the orchestrator emits per step.
 *
 * <p>The orchestrator flattens all payload fields into the top-level JSON object alongside the
 * envelope fields ({@code type}, {@code messageId}, {@code stepId}, {@code adapter}, {@code
 * operation}). The factory method {@link #fromMap(Map)} separates envelope from payload.
 *
 * @param type message type: {@code "EXECUTE_STEP"} or {@code "COMPENSATE_STEP"}
 * @param messageId unique message identifier
 * @param sagaId saga instance identifier (from payload)
 * @param stepId saga step identifier
 * @param adapter target adapter name (e.g. {@code "frost"}, {@code "apisix"}, {@code "nifi"})
 * @param operation adapter-specific operation (e.g. {@code "CREATE_PROJECT"}, {@code
 *     "DELETE_ROUTE"})
 * @param payload remaining fields (datasetId, datasetName, description, etc.)
 */
public record SagaCommandMessage(
    String type,
    String messageId,
    String sagaId,
    String stepId,
    String adapter,
    String operation,
    Map<String, Object> payload) {

  private static final Set<String> ENVELOPE_KEYS =
      Set.of("type", "messageId", "sagaId", "stepId", "adapter", "operation");

  /**
   * Creates a {@code SagaCommandMessage} from a flat map (as deserialized from JSON). Envelope
   * fields are extracted; all remaining entries become the {@code payload}.
   */
  public static SagaCommandMessage fromMap(Map<String, Object> map) {
    var payload = new HashMap<String, Object>();
    for (var entry : map.entrySet()) {
      if (!ENVELOPE_KEYS.contains(entry.getKey())) {
        payload.put(entry.getKey(), entry.getValue());
      }
    }
    return new SagaCommandMessage(
        (String) map.get("type"),
        (String) map.get("messageId"),
        (String) map.get("sagaId"),
        (String) map.get("stepId"),
        (String) map.get("adapter"),
        (String) map.get("operation"),
        Map.copyOf(payload));
  }
}
