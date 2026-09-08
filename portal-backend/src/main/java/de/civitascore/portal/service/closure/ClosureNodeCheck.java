package de.civitascore.portal.service.closure;

import java.util.List;

/**
 * The checks one artifact type must pass to participate in a released flow, beyond resolving in the
 * registry — which {@link PipelineClosureValidator} has already established for every node handed
 * here. An artifact type with no implementation is held to resolvability alone.
 *
 * <p>Every node of a type is passed in one call rather than one at a time, so an implementation can
 * answer for the whole set with a single authorization decision or query instead of repeating one
 * per artifact.
 */
public interface ClosureNodeCheck {

  /** The CORE artifact-type segment this check answers for, e.g. {@code datastructure}. */
  String artifactType();

  /**
   * @param nodes every closure node of {@link #artifactType()}, across all of the dataset's
   *     pipelines
   * @return one finding per offending artifact; empty when all pass
   */
  List<ClosureFinding> check(List<ClosureNode> nodes);
}
