package de.civitascore.portal.service.removal;

import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Pipeline;
import java.util.List;

/**
 * A {@link DataSinkChange} that {@link DataSinkRemovalService#prepare} checked and confirmed, ready
 * for {@link DataSinkRemovalService#apply}. Valid only in the transaction that prepared it: it
 * holds the managed entities that apply changes.
 */
public final class PreparedDataSinkChange {

  private final DataSinkRemovalPlan plan;
  private final Pipeline pipeline;
  private final List<DataSink> linksToAdd;
  private final List<DataSink> linksToRemove;

  /**
   * @param pipeline the Pipeline whose links change, {@code null} when no link changes
   * @param linksToAdd the Data sinks that get a link to the Pipeline
   * @param linksToRemove the Data sinks that lose their link to the Pipeline
   */
  PreparedDataSinkChange(
      DataSinkRemovalPlan plan,
      Pipeline pipeline,
      List<DataSink> linksToAdd,
      List<DataSink> linksToRemove) {
    this.plan = plan;
    this.pipeline = pipeline;
    this.linksToAdd = List.copyOf(linksToAdd);
    this.linksToRemove = List.copyOf(linksToRemove);
  }

  static PreparedDataSinkChange removalOnly(DataSinkRemovalPlan plan) {
    return new PreparedDataSinkChange(plan, null, List.of(), List.of());
  }

  DataSinkRemovalPlan plan() {
    return plan;
  }

  Pipeline pipeline() {
    return pipeline;
  }

  List<DataSink> linksToAdd() {
    return linksToAdd;
  }

  List<DataSink> linksToRemove() {
    return linksToRemove;
  }
}
