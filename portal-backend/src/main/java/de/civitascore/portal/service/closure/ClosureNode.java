package de.civitascore.portal.service.closure;

import java.util.UUID;

/**
 * One artifact in a pipeline's participating closure, already confirmed to resolve in the registry.
 *
 * <p>Carries the pipeline it was reached from so a finding stays attributable after nodes from
 * several pipelines are grouped by artifact type for checking.
 *
 * @param pipelineId the pipeline whose flow reaches the artifact
 * @param artifactUrn the versioned CORE URN
 * @param artifactType the CORE artifact-type segment, e.g. {@code datastructure}
 */
public record ClosureNode(UUID pipelineId, String artifactUrn, String artifactType) {}
