package de.civitascore.portal.model.connector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SqlConnectorConfiguration implements ConnectorConfiguration {

  @Schema(description = "Database driver type.", example = "postgres")
  @NotBlank(groups = OnPublish.class, message = "'driver' is required and must not be blank") private String driver;

  @Schema(
      description =
          "Connection URL. Embedded credentials are stripped — use user/password instead.",
      example = "postgres://localhost:5432/mydb")
  @NotBlank(groups = OnPublish.class, message = "'dsn' is required and must not be blank") private String dsn;

  @Schema(description = "Target table name.", example = "sensor_readings")
  @NotBlank(groups = OnPublish.class, message = "'table' is required and must not be blank") private String table;

  @Schema(description = "Column names to select.", example = "[\"id\", \"value\", \"timestamp\"]")
  @NotEmpty(
      groups = OnPublish.class,
      message = "'columns' is required and must be a non-empty list")
  private List<String> columns;

  @JsonProperty("where")
  @Schema(description = "SQL WHERE clause.", example = "id > :last_id")
  private String where;

  @Schema(description = "SQL prefix prepended to the query.")
  private String prefix;

  @Schema(description = "SQL suffix appended to the query.")
  private String suffix;

  @JsonProperty("init_statement")
  @Schema(description = "SQL statement executed on connection init.")
  private String initStatement;

  @JsonProperty("conn_max_idle_time")
  @Schema(description = "Maximum idle time per connection.", example = "10m")
  private String connMaxIdleTime;

  @JsonProperty("conn_max_life_time")
  @Schema(description = "Maximum lifetime per connection.", example = "1h")
  private String connMaxLifeTime;

  @JsonProperty("conn_max_idle")
  @Schema(description = "Maximum number of idle connections. Default: 2.", example = "2")
  private int connMaxIdle = 2;

  @JsonProperty("conn_max_open")
  @Schema(
      description = "Maximum number of open connections. 0 = unlimited. Default: 0.",
      example = "0")
  private int connMaxOpen = 0;

  @Schema(description = "Database username.", example = "dbuser")
  private String user;

  @Schema(
      description = "Database password. Write-only — returned as \"********\" in responses.",
      accessMode = Schema.AccessMode.WRITE_ONLY,
      example = "secret")
  private String password;

  // Validates DSN with java.net.URI and strips any embedded credentials (discarding them).
  // Strips "jdbc:" prefix if present since Redpanda Connect (Go) doesn't use it.
  public void setDsn(String dsn) {
    if (dsn == null || dsn.isEmpty()) {
      this.dsn = dsn;
      return;
    }
    try {
      String toParse = dsn.toLowerCase().startsWith("jdbc:") ? dsn.substring(5) : dsn;
      URI uri = new URI(toParse);
      if (uri.getHost() == null) {
        throw new IllegalArgumentException("Invalid DSN: could not parse host");
      }
      if (uri.getUserInfo() != null) {
        uri =
            new URI(
                uri.getScheme(),
                null,
                uri.getHost(),
                uri.getPort(),
                uri.getPath(),
                uri.getQuery(),
                uri.getFragment());
      }
      this.dsn = uri.toString();
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid DSN: could not parse URI", e);
    }
  }
}
