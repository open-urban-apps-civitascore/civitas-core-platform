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
    super("One or more DataSources are not permitted for the Dataset's DataPool scope");
    this.offendingDataSourceIds = List.copyOf(offendingDataSourceIds);
  }

  public List<UUID> getOffendingDataSourceIds() {
    return offendingDataSourceIds;
  }
}
