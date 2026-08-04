package de.civitascore.portal.util;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when one or more DataSources violate the DataPool scope of the Pipeline's Dataset. */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class DataSourceScopeViolationException extends RuntimeException {

  private final List<UUID> offendingDataSourceIds;

  public DataSourceScopeViolationException(List<UUID> offendingDataSourceIds) {
    // Name the way out: the offending ids alone leave the caller guessing, and widening the
    // datasource's own datapool scope is the only resolution — the dataset cannot be edited out
    // of the violation.
    super(
        "One or more DataSources are not permitted for the Dataset's DataPool scope."
            + " Widen the datapool scope of the offending DataSources "
            + offendingDataSourceIds
            + " or move the Dataset to a datapool they are scoped for.");
    this.offendingDataSourceIds = List.copyOf(offendingDataSourceIds);
  }

  private DataSourceScopeViolationException(List<UUID> offendingDataSourceIds, String message) {
    super(message);
    this.offendingDataSourceIds = List.copyOf(offendingDataSourceIds);
  }

  /**
   * For a DataSource a pipeline may not reference, where "not usable" deliberately covers a
   * nonexistent id, a non-AVAILABLE one and a pool-confined one alike.
   *
   * <p>Naming which of the three applies would let a caller authorized only on the dataset probe
   * the DataSource table for existence and lifecycle status, since referencing no longer requires a
   * permission on the DataSource itself. The message states all three conditions instead.
   *
   * @param offendingDataSourceIds the referenced ids that are not usable
   * @return the exception to throw
   */
  public static DataSourceScopeViolationException notUsableInPipeline(
      List<UUID> offendingDataSourceIds) {
    return new DataSourceScopeViolationException(
        offendingDataSourceIds,
        "One or more DataSources cannot be used by this Dataset's pipelines. Each referenced"
            + " DataSource must exist, be AVAILABLE, and be released for the Dataset's datapool: "
            + offendingDataSourceIds);
  }

  public List<UUID> getOffendingDataSourceIds() {
    return offendingDataSourceIds;
  }
}
