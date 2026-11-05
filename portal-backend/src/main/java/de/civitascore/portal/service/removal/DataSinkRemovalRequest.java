package de.civitascore.portal.service.removal;

import java.util.Set;
import java.util.UUID;

/**
 * The removals one request makes in a Dataset: links between a Pipeline and a Data sink it removes,
 * also by deleting the Pipeline, and Data sinks it deletes directly.
 */
public record DataSinkRemovalRequest(
    Set<PipelineDataSinkLink> removedLinks, Set<UUID> deletedDataSinkIds) {

  public DataSinkRemovalRequest {
    removedLinks = Set.copyOf(removedLinks);
    deletedDataSinkIds = Set.copyOf(deletedDataSinkIds);
  }

  static DataSinkRemovalRequest removeLinks(Set<PipelineDataSinkLink> links) {
    return new DataSinkRemovalRequest(links, Set.of());
  }

  static DataSinkRemovalRequest deleteDataSink(UUID dataSinkId) {
    return new DataSinkRemovalRequest(Set.of(), Set.of(dataSinkId));
  }

  /** Whether the Pipeline no longer writes into the Data sink after this request. */
  boolean detaches(UUID pipelineId, UUID dataSinkId) {
    return removedLinks.contains(new PipelineDataSinkLink(pipelineId, dataSinkId));
  }

  boolean isEmpty() {
    return removedLinks.isEmpty() && deletedDataSinkIds.isEmpty();
  }
}
