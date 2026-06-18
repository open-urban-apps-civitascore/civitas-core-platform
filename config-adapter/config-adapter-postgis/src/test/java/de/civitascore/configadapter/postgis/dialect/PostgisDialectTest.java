/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.postgis.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.model.postgis.ColumnConfig;
import de.civitascore.configadapter.model.postgis.ColumnType;
import de.civitascore.configadapter.model.postgis.GeometryColumnConfig;
import de.civitascore.configadapter.model.postgis.GeometryType;
import de.civitascore.configadapter.model.postgis.IndexConfig;
import de.civitascore.configadapter.model.postgis.IndexConfig.IndexMethod;
import de.civitascore.configadapter.model.postgis.TableConfig;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PostgisDialectTest {

  private PostgisDialect dialect;

  @BeforeEach
  void setUp() {
    dialect = new PostgisDialect();
  }

  @Nested
  class Identifiers {

    @Test
    void quoteIdentWrapsIdentifierInDoubleQuotes() {
      assertEquals("\"users\"", dialect.quoteIdent("users"));
    }

    @Test
    void quoteIdentEscapesEmbeddedDoubleQuotes() {
      assertEquals("\"weird\"\"name\"", dialect.quoteIdent("weird\"name"));
    }

    @Test
    void quoteIdentRejectsNullIdentifier() {
      assertThrows(IllegalArgumentException.class, () -> dialect.quoteIdent(null));
    }
  }

  @Nested
  class CreateTable {

    @Test
    void simpleTableHasSingleCreateTableStatement() {
      TableConfig table =
          tableWithSingleColumn(
              "widgets", new ColumnConfig("id", ColumnType.BIGINT, null, null, null, false));

      List<String> statements = dialect.createTable(table);

      assertEquals(1, statements.size());
      assertEquals("CREATE TABLE \"widgets\" (\"id\" BIGINT NOT NULL)", statements.get(0));
    }

    @Test
    void schemaIsCreatedBeforeTable() {
      TableConfig table = new TableConfig();
      table.setSchema("iot");
      table.setName("readings");
      table.setColumns(
          List.of(new ColumnConfig("id", ColumnType.INTEGER, null, null, null, false)));

      List<String> statements = dialect.createTable(table);

      assertEquals(2, statements.size());
      assertEquals("CREATE SCHEMA \"iot\"", statements.get(0));
      assertTrue(statements.get(1).startsWith("CREATE TABLE \"iot\".\"readings\""));
    }

    @Test
    void primaryKeyClauseIsAppendedToColumnList() {
      TableConfig table = new TableConfig();
      table.setName("orders");
      table.setColumns(
          List.of(
              new ColumnConfig("id", ColumnType.BIGINT, null, null, null, false),
              new ColumnConfig("region", ColumnType.TEXT, null, null, null, false)));
      table.setPrimaryKey(List.of("id", "region"));

      String createStatement = dialect.createTable(table).get(0);

      assertTrue(createStatement.contains("PRIMARY KEY (\"id\", \"region\")"));
    }

    @Test
    void varcharRendersExplicitLengthWhenProvided() {
      TableConfig table =
          tableWithSingleColumn(
              "people", new ColumnConfig("name", ColumnType.VARCHAR, 120, null, null, true));

      String createStatement = dialect.createTable(table).get(0);

      assertTrue(createStatement.contains("\"name\" VARCHAR(120)"));
    }

    @Test
    void varcharRendersWithoutLengthWhenLengthOmitted() {
      TableConfig table =
          tableWithSingleColumn(
              "people", new ColumnConfig("name", ColumnType.VARCHAR, null, null, null, true));

      assertTrue(dialect.createTable(table).get(0).contains("\"name\" VARCHAR"));
      assertFalse(dialect.createTable(table).get(0).contains("VARCHAR("));
    }

    @Test
    void numericRendersPrecisionAndScale() {
      TableConfig table =
          tableWithSingleColumn(
              "invoices", new ColumnConfig("amount", ColumnType.NUMERIC, null, 12, 2, false));

      assertTrue(dialect.createTable(table).get(0).contains("\"amount\" NUMERIC(12, 2)"));
    }

    @Test
    void numericRendersPrecisionOnlyWhenScaleOmitted() {
      TableConfig table =
          tableWithSingleColumn(
              "invoices", new ColumnConfig("amount", ColumnType.NUMERIC, null, 12, null, false));

      assertTrue(dialect.createTable(table).get(0).contains("\"amount\" NUMERIC(12)"));
    }

    @Test
    void nullableColumnOmitsNotNullKeyword() {
      TableConfig table =
          tableWithSingleColumn(
              "widgets", new ColumnConfig("label", ColumnType.TEXT, null, null, null, true));

      assertFalse(dialect.createTable(table).get(0).contains("NOT NULL"));
    }

    @Test
    void geometryColumnRendersTypeAndSrid() {
      TableConfig table = new TableConfig();
      table.setName("places");
      table.setColumns(List.of(new ColumnConfig("id", ColumnType.BIGINT, null, null, null, false)));
      table.setGeometryColumns(
          List.of(new GeometryColumnConfig("geom", GeometryType.POINT, 4326, null, false)));

      String createStatement = dialect.createTable(table).get(0);

      assertTrue(createStatement.contains("\"geom\" GEOMETRY(POINT, 4326) NOT NULL"));
    }

    @Test
    void geometryColumnWithDimensionThreeAppendsZSuffix() {
      TableConfig table = new TableConfig();
      table.setName("places3d");
      table.setColumns(List.of(new ColumnConfig("id", ColumnType.BIGINT, null, null, null, false)));
      table.setGeometryColumns(
          List.of(new GeometryColumnConfig("geom", GeometryType.POINT, 4326, 3, false)));

      assertTrue(dialect.createTable(table).get(0).contains("GEOMETRY(POINTZ, 4326)"));
    }

    @Test
    void geometryColumnWithoutSridOmitsSridArgument() {
      TableConfig table = new TableConfig();
      table.setName("places");
      table.setColumns(List.of(new ColumnConfig("id", ColumnType.BIGINT, null, null, null, false)));
      table.setGeometryColumns(
          List.of(new GeometryColumnConfig("geom", GeometryType.POLYGON, null, null, true)));

      assertTrue(dialect.createTable(table).get(0).contains("\"geom\" GEOMETRY(POLYGON)"));
    }

    @Test
    void gistIndexOnGeometryColumnUsesGistMethod() {
      TableConfig table = new TableConfig();
      table.setName("places");
      table.setColumns(List.of(new ColumnConfig("id", ColumnType.BIGINT, null, null, null, false)));
      table.setGeometryColumns(
          List.of(new GeometryColumnConfig("geom", GeometryType.POINT, 4326, null, false)));
      table.setIndexes(
          List.of(new IndexConfig("idx_places_geom", List.of("geom"), false, IndexMethod.GIST)));

      List<String> statements = dialect.createTable(table);

      assertEquals(2, statements.size());
      assertEquals(
          "CREATE INDEX \"idx_places_geom\" ON \"places\" USING GIST (\"geom\")",
          statements.get(1));
    }

    @Test
    void uniqueBtreeIndexRendersUniqueKeyword() {
      TableConfig table = new TableConfig();
      table.setName("users");
      table.setColumns(
          List.of(new ColumnConfig("email", ColumnType.TEXT, null, null, null, false)));
      table.setIndexes(
          List.of(new IndexConfig("idx_users_email", List.of("email"), true, IndexMethod.BTREE)));

      String indexStatement = dialect.createTable(table).get(1);

      assertEquals(
          "CREATE UNIQUE INDEX \"idx_users_email\" ON \"users\" USING BTREE (\"email\")",
          indexStatement);
    }

    @Test
    void indexWithoutNameUsesGeneratedName() {
      TableConfig table = new TableConfig();
      table.setName("users");
      table.setColumns(
          List.of(new ColumnConfig("email", ColumnType.TEXT, null, null, null, false)));
      table.setIndexes(List.of(new IndexConfig(null, List.of("email"), false, IndexMethod.BTREE)));

      String indexStatement = dialect.createTable(table).get(1);

      assertTrue(indexStatement.contains("\"idx_users_email\""));
    }

    @Test
    void blankTableNameIsRejected() {
      TableConfig table = new TableConfig();
      table.setName("");

      assertThrows(IllegalArgumentException.class, () -> dialect.createTable(table));
    }

    private TableConfig tableWithSingleColumn(String tableName, ColumnConfig column) {
      TableConfig table = new TableConfig();
      table.setName(tableName);
      table.setColumns(List.of(column));
      return table;
    }
  }

  @Nested
  class DropTable {

    @Test
    void simpleDropProducesSingleStatement() {
      List<String> statements = dialect.dropTable(null, "widgets");

      assertEquals(List.of("DROP TABLE \"widgets\""), statements);
    }

    @Test
    void qualifiedDropIncludesSchema() {
      List<String> statements = dialect.dropTable("iot", "readings");

      assertEquals(List.of("DROP TABLE \"iot\".\"readings\""), statements);
    }

    @Test
    void blankTableNameIsRejected() {
      assertThrows(IllegalArgumentException.class, () -> dialect.dropTable("iot", "  "));
    }
  }

  @Nested
  class SqlStateClassification {

    @Test
    void duplicateTableSqlStateIsIdentifiedAsDuplicate() {
      assertTrue(dialect.isDuplicate(new SQLException("dup", "42P07")));
    }

    @Test
    void duplicateSchemaSqlStateIsIdentifiedAsDuplicate() {
      assertTrue(dialect.isDuplicate(new SQLException("dup", "42P06")));
    }

    @Test
    void undefinedTableSqlStateIsIdentifiedAsMissing() {
      assertTrue(dialect.isMissing(new SQLException("missing", "42P01")));
    }

    @Test
    void invalidSchemaSqlStateIsIdentifiedAsMissing() {
      assertTrue(dialect.isMissing(new SQLException("missing", "3F000")));
    }

    @Test
    void connectionClassSqlStatesAreIdentifiedAsConnectivity() {
      assertTrue(dialect.isConnectivity(new SQLException("conn", "08000")));
      assertTrue(dialect.isConnectivity(new SQLException("conn", "08006")));
    }

    @Test
    void unrelatedSqlStateIsNotMisclassified() {
      SQLException syntax = new SQLException("syntax", "42601");

      assertFalse(dialect.isDuplicate(syntax));
      assertFalse(dialect.isMissing(syntax));
      assertFalse(dialect.isConnectivity(syntax));
    }

    @Test
    void nullExceptionIsHandledSafely() {
      assertFalse(dialect.isDuplicate(null));
      assertFalse(dialect.isMissing(null));
      assertFalse(dialect.isConnectivity(null));
    }
  }
}
