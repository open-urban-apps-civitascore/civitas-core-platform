package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.connector.ConnectorJson;
import de.civitascore.portal.model.connector.MalformedDataSourceRequestException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Input DTO for creating and updating data source resources. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSourceInputDTO extends DataSourceMetaInputDTO {

  @JsonDeserialize(using = ConnectorJson.RequestDeserializer.class)
  private ConnectorConfiguration connector;

  @Schema(description = "ID of the data structure version to associate")
  private UUID dataStructureVersionId;

  /** The application mapper ignores unknown keys; a Data source write rejects them. */
  @JsonAnySetter
  void rejectUnknownKey(String key, Object value) {
    throw new MalformedDataSourceRequestException("Unknown key: " + key);
  }
}
