/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.graph;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The closed vocabulary of CORE Pipeline node kinds this adapter deploys, each with exactly one
 * flow role. Path derivation and validation dispatch on roles, never on raw kind strings — this
 * enum is the only place a kind's string appears. Kept as a closed, hand-maintained set (like the
 * hand-wired stage registry) so the reviewable universe of deployable node kinds is visible in one
 * file; a graph node whose {@code kind} is absent here is unknown to the adapter and must not be
 * wired into the data flow.
 *
 * <p>Vocabulary from the CORE Pipeline schema ({@code source}/{@code sink}/{@code mapping}/{@code
 * start}/{@code end}/{@code cron}). A {@code sink} node covers every sink engine (FROST, PostGIS,
 * …); which one a node targets is decided later from the {@code DataSink} its {@code sinkRef}
 * resolves to, not from the node kind. Unsupported CORE kinds ({@code filter}/{@code enrich}/{@code
 * split}) are deliberately absent — the derivation rejects them if wired into the flow.
 */
public enum NodeKind {
  SOURCE("source", Role.SOURCE),
  MAPPING("mapping", Role.TRANSFORM),
  SINK("sink", Role.SINK),
  CRON("cron", Role.TRIGGER),
  START("start", Role.CONTROL),
  END("end", Role.CONTROL);

  /** What a node kind contributes to the flow. */
  public enum Role {
    /** Emits the data; exactly one per pipeline (deliberate product restriction). */
    SOURCE,
    /** Sits on the data path between source and sink; kinds and instances are unbounded. */
    TRANSFORM,
    /** Consumes the data; exactly one per pipeline (deliberate product restriction). */
    SINK,
    /** Schedules the source's entry processor; not data flow. */
    TRIGGER,
    /** Structural anchor ({@code start}/{@code end}); carries no data. */
    CONTROL
  }

  private static final Map<String, NodeKind> BY_KIND_STRING =
      Stream.of(values()).collect(Collectors.toMap(k -> k.kindString, Function.identity()));

  private final String kindString;
  private final Role role;

  NodeKind(String kindString, Role role) {
    this.kindString = kindString;
    this.role = role;
  }

  /** The CORE Pipeline node {@code kind} string. */
  public String kindString() {
    return kindString;
  }

  public Role role() {
    return role;
  }

  /**
   * The kind for a CORE {@code kind} string, or empty for a kind unknown to this adapter.
   *
   * @param kind the node kind string (nullable)
   * @return the kind, or empty
   */
  public static Optional<NodeKind> fromKind(String kind) {
    return Optional.ofNullable(kind == null ? null : BY_KIND_STRING.get(kind));
  }

  /** The role of {@code node}'s kind, or empty for an unknown kind. */
  public static Optional<Role> roleOf(PipelineGraph.GraphNode node) {
    return fromKind(node.kind()).map(NodeKind::role);
  }
}
