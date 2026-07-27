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

  public List<UUID> getOffendingDataSourceIds() {
    return offendingDataSourceIds;
  }
}
