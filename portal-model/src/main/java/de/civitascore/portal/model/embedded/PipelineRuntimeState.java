package de.civitascore.portal.model.embedded;

/**
 * Runtime health of a pipeline (distinct from its release lifecycle). Contract-facing: the values
 * are carried verbatim as the {@code status} string of the {@code PIPELINE_STATUS_CHANGED} Kafka
 * event.
 */
public enum PipelineRuntimeState {
  /** The pipeline is running and processing data. */
  OK,
  /** The pipeline failed to deploy or failed while running (see the accompanying message). */
  ERROR,
  /** The platform stopped the pipeline after a fatal error; the message holds the cause. */
  STOPPED
}
