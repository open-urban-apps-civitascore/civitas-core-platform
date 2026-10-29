package de.civitascore.portal.service.removal;

import java.util.Set;
import java.util.UUID;

/**
 * The removals one request makes in a Dataset: Pipelines it deletes, links between a Pipeline and a
 * Data sink it removes, and Data sinks it deletes directly.
 */
public record DataSinkRemovalRequest(
    Set<UUID> deletedPipelineIds,
    Set<PipelineDataSinkLink> removedLinks,
    Set<UUID> deletedDataSinkIds) {

  public DataSinkRemovalRequest {
    deletedPipelineIds = Set.copyOf(deletedPipelineIds);
    removedLinks = Set.copyOf(removedLinks);
    deletedDataSinkIds = Set.copyOf(deletedDataSinkIds);
  }

  static DataSinkRemovalRequest deletePipeline(UUID pipelineId) {
    return new DataSinkRemovalRequest(Set.of(pipelineId), Set.of(), Set.of());
  }

  static DataSinkRemovalRequest removeLinks(Set<PipelineDataSinkLink> links) {
    return new DataSinkRemovalRequest(Set.of(), links, Set.of());
  }

  static DataSinkRemovalRequest deleteDataSink(UUID dataSinkId) {
    return new DataSinkRemovalRequest(Set.of(), Set.of(), Set.of(dataSinkId));
  }

  /** Whether the Pipeline no longer writes into the Data sink after this request. */
  boolean detaches(UUID pipelineId, UUID dataSinkId) {
    return deletedPipelineIds.contains(pipelineId)
        || removedLinks.contains(new PipelineDataSinkLink(pipelineId, dataSinkId));
  }

  boolean isEmpty() {
    return deletedPipelineIds.isEmpty() && removedLinks.isEmpty() && deletedDataSinkIds.isEmpty();
  }
}
