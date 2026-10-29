package de.civitascore.portal.service.removal;

import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Pipeline;
import java.util.Set;
import java.util.UUID;

/** A change to the links between Pipelines and Data sinks that a caller asks for. */
public sealed interface DataSinkChange {

  /**
   * The Pipeline writes into exactly the given Data sinks afterwards: on create, PUT and PATCH.
   *
   * @param pipeline the Pipeline, already carrying its id
   * @param dataSinkIds the full new set of linked Data sinks
   */
  static DataSinkChange linkPipeline(Pipeline pipeline, Set<UUID> dataSinkIds) {
    return new LinkPipeline(pipeline, Set.copyOf(dataSinkIds));
  }

  static DataSinkChange deletePipeline(Pipeline pipeline) {
    return new DeletePipeline(pipeline);
  }

  static DataSinkChange deleteDataSink(DataSink dataSink) {
    return new DeleteDataSink(dataSink);
  }

  /** See {@link #linkPipeline}. */
  record LinkPipeline(Pipeline pipeline, Set<UUID> dataSinkIds) implements DataSinkChange {}

  /** See {@link #deletePipeline}. */
  record DeletePipeline(Pipeline pipeline) implements DataSinkChange {}

  /** See {@link #deleteDataSink}. */
  record DeleteDataSink(DataSink dataSink) implements DataSinkChange {}
}
