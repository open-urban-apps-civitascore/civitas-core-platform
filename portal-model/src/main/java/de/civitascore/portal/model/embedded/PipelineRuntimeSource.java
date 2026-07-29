package de.civitascore.portal.model.embedded;

/**
 * Where a pipeline runtime status originated. Contract-facing: the values are carried verbatim as
 * the {@code source} string of the {@code PIPELINE_STATUS_CHANGED} Kafka event.
 */
public enum PipelineRuntimeSource {
  /** Reported by the deploy/update/delete saga while provisioning the pipeline. */
  DEPLOYMENT,
  /** Reported by the runtime monitor after the pipeline has started. */
  RUNTIME
}
