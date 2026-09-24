package de.civitascore.portal.model.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("SqlConnectorConfiguration Tests")
class SqlConnectorConfigurationTest {

  private static final ObjectMapper MAPPER = new JsonMapper();

  @Nested
  @DisplayName("DSN validation and credential stripping")
  class DsnValidationTests {

    @Test
    @DisplayName("Should strip embedded credentials from DSN and discard them")
    void shouldStripCredentialsFromDsn() {
      SqlConnectorConfiguration config = new SqlConnectorConfiguration();
      config.setDsn("postgres://admin:secret@localhost:5432/mydb");

      assertThat(config.getDsn()).isEqualTo("postgres://localhost:5432/mydb");
      // Credentials are discarded, not stored
      assertThat(config.getUser()).isNull();
      assertThat(config.getPassword()).isNull();
    }

    @Test
    @DisplayName("Should pass through DSN without credentials unchanged")
    void shouldPassThroughCleanDsn() {
      SqlConnectorConfiguration config = new SqlConnectorConfiguration();
      config.setDsn("postgres://localhost:5432/mydb");

      assertThat(config.getDsn()).isEqualTo("postgres://localhost:5432/mydb");
      assertThat(config.getUser()).isNull();
      assertThat(config.getPassword()).isNull();
    }

    @Test
    @DisplayName("Should handle null DSN")
    void shouldHandleNullDsn() {
      SqlConnectorConfiguration config = new SqlConnectorConfiguration();
      config.setDsn(null);

      assertThat(config.getDsn()).isNull();
    }

    @Test
    @DisplayName("Should handle empty DSN")
    void shouldHandleEmptyDsn() {
      SqlConnectorConfiguration config = new SqlConnectorConfiguration();
      config.setDsn("");

      assertThat(config.getDsn()).isEqualTo("");
    }

    @Test
    @DisplayName("Should preserve sslmode (TLS) while stripping credentials from a postgres DSN")
    void shouldPreserveSslModeAndStripCredentials() {
      // SSL to the source DB is configured via the dsn's sslmode; the credential-stripping URI
      // round-trip must keep the query so it survives to the deployed JDBC connection
      SqlConnectorConfiguration config = new SqlConnectorConfiguration();
      config.setDsn("postgres://reader:secret@db:5432/in?sslmode=require");

      assertThat(config.getDsn()).isEqualTo("postgres://db:5432/in?sslmode=require");
      assertThat(config.getPassword()).isNull();
    }

    @Test
    @DisplayName("Should preserve query params in clean DSN")
    void shouldPreserveQueryParams() {
      SqlConnectorConfiguration config = new SqlConnectorConfiguration();
      config.setDsn("sqlserver://sa:pass@host:1433?database=mydb&encrypt=true");

      assertThat(config.getDsn()).isEqualTo("sqlserver://host:1433?database=mydb&encrypt=true");
    }

    @Test
    @DisplayName("Should strip jdbc: prefix from DSN")
    void shouldStripJdbcPrefix() {
      SqlConnectorConfiguration config = new SqlConnectorConfiguration();
      config.setDsn("jdbc:postgres://localhost:5432/mydb");

      assertThat(config.getDsn()).isEqualTo("postgres://localhost:5432/mydb");
    }

    @Test
    @DisplayName("Should strip jdbc: prefix and embedded credentials together")
    void shouldStripJdbcPrefixAndCredentials() {
      SqlConnectorConfiguration config = new SqlConnectorConfiguration();
      config.setDsn("jdbc:postgres://admin:secret@localhost:5432/mydb");

      assertThat(config.getDsn()).isEqualTo("postgres://localhost:5432/mydb");
      assertThat(config.getUser()).isNull();
      assertThat(config.getPassword()).isNull();
    }

    @Test
    @DisplayName("Should throw on unparseable DSN without credentials")
    void shouldThrowOnUnparseableDsn() {
      SqlConnectorConfiguration config = new SqlConnectorConfiguration();

      assertThatThrownBy(() -> config.setDsn("not a valid dsn with spaces"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid DSN");
    }

    @Test
    @DisplayName("Should throw on unparseable DSN that contains credentials")
    void shouldThrowOnUnparseableDsnWithCredentials() {
      SqlConnectorConfiguration config = new SqlConnectorConfiguration();

      assertThatThrownBy(() -> config.setDsn("not a valid dsn user:pass@with spaces"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid DSN");
    }
  }

  @Nested
  @DisplayName("Dedicated user/password fields")
  class CredentialFieldTests {

    @Test
    @DisplayName("Should accept dedicated user and password fields")
    void shouldAcceptDedicatedCredentials() {
      SqlConnectorConfiguration config = new SqlConnectorConfiguration();
      config.setDsn("postgres://localhost:5432/mydb");
      config.setUser("admin");
      config.setPassword("secret");

      assertThat(config.getUser()).isEqualTo("admin");
      assertThat(config.getPassword()).isEqualTo("secret");
      assertThat(config.getDsn()).isEqualTo("postgres://localhost:5432/mydb");
    }

    @Test
    @DisplayName("Should normalize blank password to null")
    void shouldNormalizeBlankPasswordToNull() {
      SqlConnectorConfiguration config = new SqlConnectorConfiguration();
      config.setPassword("");
      assertThat(config.getPassword()).isNull();

      config.setPassword("   ");
      assertThat(config.getPassword()).isNull();
    }

    @Test
    @DisplayName("Should preserve null password")
    void shouldPreserveNullPassword() {
      SqlConnectorConfiguration config = new SqlConnectorConfiguration();
      config.setPassword(null);
      assertThat(config.getPassword()).isNull();
    }
  }

  @Nested
  @DisplayName("Jackson deserialization")
  class DeserializationTests {

    @Test
    @DisplayName("Should deserialize from map with all fields")
    void shouldDeserializeFromMapWithAllFields() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("driver", "postgres");
      map.put("dsn", "postgres://localhost:5432/mydb");
      map.put("table", "users");
      map.put("columns", List.of("id", "name"));
      map.put("where", "id > ?");
      map.put("args_mapping", "- this.id");
      map.put("init_files", "[\"init.sql\"]");
      map.put("user", "admin");
      map.put("password", "secret");

      SqlConnectorConfiguration config = MAPPER.convertValue(map, SqlConnectorConfiguration.class);

      assertThat(config.getDriver()).isEqualTo("postgres");
      assertThat(config.getDsn()).isEqualTo("postgres://localhost:5432/mydb");
      assertThat(config.getUser()).isEqualTo("admin");
      assertThat(config.getPassword()).isEqualTo("secret");
      assertThat(config.getTable()).isEqualTo("users");
      assertThat(config.getColumns()).isEqualTo(List.of("id", "name"));
      assertThat(config.getWhere()).isEqualTo("id > ?");
    }

    @Test
    @DisplayName("Should ignore unknown fields")
    void shouldIgnoreUnknownFields() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("driver", "postgres");
      map.put("dsn", "postgres://localhost/db");
      map.put("table", "users");
      map.put("unknown_field", "value");
      map.put("extra", 42);

      SqlConnectorConfiguration config = MAPPER.convertValue(map, SqlConnectorConfiguration.class);

      assertThat(config.getDriver()).isEqualTo("postgres");
      assertThat(config.getTable()).isEqualTo("users");
    }

    @Test
    @DisplayName("Should strip credentials from DSN during deserialization")
    void shouldStripCredentialsDuringDeserialization() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("driver", "postgres");
      map.put("dsn", "postgres://user:pass@localhost:5432/mydb");
      map.put("table", "users");

      SqlConnectorConfiguration config = MAPPER.convertValue(map, SqlConnectorConfiguration.class);

      assertThat(config.getDsn()).isEqualTo("postgres://localhost:5432/mydb");
    }
  }

  @Nested
  @DisplayName("Jackson serialization")
  class SerializationTests {

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should serialize back to map with correct JSON property names")
    void shouldSerializeToMapWithCorrectPropertyNames() {
      SqlConnectorConfiguration config = new SqlConnectorConfiguration();
      config.setDriver("postgres");
      config.setDsn("postgres://localhost/db");
      config.setTable("users");
      config.setColumns(List.of("*"));
      config.setWhere("active = true");
      config.setUser("user");
      config.setPassword("secret");

      Map<String, Object> map = MAPPER.convertValue(config, Map.class);

      assertThat(map.get("driver")).isEqualTo("postgres");
      assertThat(map.get("dsn")).isEqualTo("postgres://localhost/db");
      assertThat(map.get("table")).isEqualTo("users");
      assertThat(map.get("columns")).isEqualTo(List.of("*"));
      assertThat(map.get("where")).isEqualTo("active = true");
      assertThat(map.get("user")).isEqualTo("user");
      assertThat(map.get("password")).isEqualTo("secret");
    }
  }
}
