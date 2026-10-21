package de.civitascore.portal.model.input;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.connector.ConnectorJson;
import de.civitascore.portal.model.connector.ConnectorSchema;
import de.civitascore.portal.model.connector.MalformedDataSourceRequestException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tools.jackson.databind.annotation.JsonDeserialize;

/** The connector values of a Data source to test, saved or not. */
@Data
@Schema(
    description =
        "Connector values to test. The test reads from the source once and changes no Data"
            + " source; it uses the same rules as the release of a Data source.")
public class ConnectionTestInputDTO {

  @Schema(implementation = ConnectorSchema.class, requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonDeserialize(using = ConnectorJson.RequestDeserializer.class)
  @NotNull(message = "'connector' is required") private ConnectorConfiguration connector;

  @Schema(
      description =
          "Name of the API call to run. Required for an HTTP connector, where it names one of its"
              + " apiCalls; absent for any other connector.",
      example = "Get all weather stations")
  private String apiCall;

  /** Rejects unknown keys, which the application mapper ignores. */
  @JsonAnySetter
  void rejectUnknownKey(String key, Object value) {
    throw new MalformedDataSourceRequestException("Unknown key: " + key);
  }
}
