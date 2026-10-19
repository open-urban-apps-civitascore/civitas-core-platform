package de.civitascore.portal.model.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SqlConnectorConfigurationTest {

  private static SqlConnectorConfiguration withDsn(String dsn) {
    return new SqlConnectorConfiguration(null, dsn, null, null, null, null, null);
  }

  @ParameterizedTest
  @CsvSource({
    "postgres://user:secret@localhost:5432/mydb, postgres://localhost:5432/mydb",
    "postgres://reader:secret@db:5432/in?sslmode=require, postgres://db:5432/in?sslmode=require",
    "sqlserver://sa:pass@host:1433?database=mydb&encrypt=true,"
        + " sqlserver://host:1433?database=mydb&encrypt=true",
    "jdbc:postgres://admin:secret@localhost:5432/mydb, postgres://localhost:5432/mydb",
    "JDBC:postgres://localhost:5432/mydb, postgres://localhost:5432/mydb",
    "postgres://localhost:5432/mydb, postgres://localhost:5432/mydb"
  })
  void constructor_withDsn_removesCredentialsAndJdbcPrefix(String dsn, String expected) {
    assertThat(withDsn(dsn).dsn()).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not a valid dsn with spaces", "not a valid dsn user:pass@with spaces"})
  void constructor_withUnparseableDsn_throwsNamingTheKeyOnly(String dsn) {
    assertThatThrownBy(() -> withDsn(dsn))
        .isInstanceOf(InvalidConnectorValueException.class)
        .hasMessage("dsn");
  }

  @Test
  void constructor_withBlankValues_storesNull() {
    SqlConnectorConfiguration config =
        new SqlConnectorConfiguration(" ", "", " ", null, " ", "", Secret.of(" "));

    assertThat(config)
        .isEqualTo(new SqlConnectorConfiguration(null, null, null, null, null, null, null));
  }
}
