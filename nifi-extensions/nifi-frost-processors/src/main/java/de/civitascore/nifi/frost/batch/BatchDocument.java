/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;

/** The {@code $batch} document of one invocation: the sub-requests of every record in it. */
public final class BatchDocument {

  private final List<RecordPlan> plans;

  public BatchDocument(List<RecordPlan> plans) {
    this.plans = List.copyOf(plans);
  }

  public List<RecordPlan> plans() {
    return plans;
  }

  /** The document as FROST reads it. */
  public ObjectNode toJson(ObjectMapper mapper) {
    ObjectNode document = mapper.createObjectNode();
    ArrayNode requests = document.putArray("requests");
    for (RecordPlan plan : plans) {
      for (SubRequest request : plan.requests()) {
        requests.add(render(mapper, request));
      }
    }
    return document;
  }

  private ObjectNode render(ObjectMapper mapper, SubRequest request) {
    ObjectNode node = mapper.createObjectNode();
    node.put("id", request.id());
    node.put("atomicityGroup", request.group());
    if (request.condition() != null) {
      node.put("if", request.condition());
    }
    node.put("method", request.method().wire());
    node.put("url", request.url());
    if (request.body() != null) {
      node.set("body", request.body());
    }
    return node;
  }

  /** Every sub-request of the document, in document order. */
  public List<SubRequest> requests() {
    List<SubRequest> all = new ArrayList<>();
    for (RecordPlan plan : plans) {
      all.addAll(plan.requests());
    }
    return all;
  }
}
