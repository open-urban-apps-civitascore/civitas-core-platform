package de.civitascore.portal.model.connector;

import de.civitascore.portal.model.embedded.ConnectorType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Connector configuration of an SQL Data source. */
@Schema(name = "SqlConnector", description = "Connector of an SQL Data source.")
public record SqlConnectorConfiguration(
    @Schema(description = "Database driver type.", example = "postgres")
        @NotBlank(groups = OnRelease.class, message = "'driver' is required and must not be blank") String driver,
    @Schema(
            description =
                "Connection URL. Embedded credentials and a jdbc: prefix are removed; use user and"
                    + " password instead.",
            example = "postgres://localhost:5432/mydb")
        @NotBlank(groups = OnRelease.class, message = "'dsn' is required and must not be blank") String dsn,
    @Schema(description = "Target table name.", example = "sensor_readings")
        @NotBlank(groups = OnRelease.class, message = "'table' is required and must not be blank") String table,
    @Schema(description = "Column names to select.", example = "[\"id\", \"value\", \"timestamp\"]")
        @NotEmpty(
            groups = OnRelease.class,
            message = "'columns' is required and must be a non-empty list")
        List<String> columns,
    @Schema(description = "SQL WHERE clause.", example = "id > :last_id") String where,
    @Schema(description = "Database username.", example = "dbuser") String user,
    @Schema(
            description = "Database password. Responses show \"********\" for a stored password.",
            implementation = String.class,
            accessMode = Schema.AccessMode.WRITE_ONLY,
            format = "password",
            example = "secret")
        Secret password)
    implements ConnectorConfiguration {

  public SqlConnectorConfiguration {
    driver = ConnectorValues.blankToNull(driver);
    dsn = ConnectorUris.dsnWithoutCredentials(dsn, "dsn");
    table = ConnectorValues.blankToNull(table);
    columns = ConnectorValues.listOf(columns, "columns");
    where = ConnectorValues.blankToNull(where);
    user = ConnectorValues.blankToNull(user);
  }

  @Override
  public ConnectorType connectorType() {
    return ConnectorType.SQL;
  }
}
