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

import de.civitascore.configadapter.model.postgis.DbRoleConfig;
import de.civitascore.configadapter.model.postgis.SchemaConfig;
import de.civitascore.configadapter.model.postgis.SchemaPrivilege;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PostgisDialectSchemaRoleTest {

  private PostgisDialect dialect;

  @BeforeEach
  void setUp() {
    dialect = new PostgisDialect();
  }

  @Nested
  class SchemaDdl {

    @Test
    void createWithoutOwnerOmitsAuthorization() {
      SchemaConfig schema = new SchemaConfig();
      schema.setName("iot");

      assertEquals(List.of("CREATE SCHEMA \"iot\""), dialect.createSchema(schema));
    }

    @Test
    void createWithOwnerAddsAuthorization() {
      SchemaConfig schema = new SchemaConfig();
      schema.setName("iot");
      schema.setOwner("iot_admin");

      assertEquals(
          List.of("CREATE SCHEMA \"iot\" AUTHORIZATION \"iot_admin\""),
          dialect.createSchema(schema));
    }

    @Test
    void alterOwnerProducesOwnerToStatement() {
      SchemaConfig schema = new SchemaConfig();
      schema.setName("iot");
      schema.setOwner("new_owner");

      assertEquals(
          List.of("ALTER SCHEMA \"iot\" OWNER TO \"new_owner\""), dialect.alterSchemaOwner(schema));
    }

    @Test
    void alterOwnerWithoutOwnerIsNoOp() {
      SchemaConfig schema = new SchemaConfig();
      schema.setName("iot");

      assertTrue(dialect.alterSchemaOwner(schema).isEmpty());
    }

    @Test
    void dropDefaultsToRestrict() {
      SchemaConfig schema = new SchemaConfig();
      schema.setName("iot");

      assertEquals(List.of("DROP SCHEMA \"iot\" RESTRICT"), dialect.dropSchema(schema));
    }

    @Test
    void dropWithCascadeUsesCascade() {
      SchemaConfig schema = new SchemaConfig();
      schema.setName("iot");
      schema.setCascade(true);

      assertEquals(List.of("DROP SCHEMA \"iot\" CASCADE"), dialect.dropSchema(schema));
    }

    @Test
    void blankNameIsRejected() {
      SchemaConfig schema = new SchemaConfig();
      schema.setName("  ");

      assertThrows(IllegalArgumentException.class, () -> dialect.createSchema(schema));
    }
  }

  @Nested
  class RoleDdl {

    @Test
    void loginRoleWithPasswordRendersLoginAndPasswordLiteral() {
      DbRoleConfig role = new DbRoleConfig();
      role.setName("analyst");
      role.setCanLogin(true);

      String statement = dialect.createRole(role, "s3cret").get(0);

      assertEquals("CREATE ROLE \"analyst\" WITH LOGIN PASSWORD 's3cret'", statement);
    }

    @Test
    void roleWithoutLoginRendersNologin() {
      DbRoleConfig role = new DbRoleConfig();
      role.setName("readers");

      assertEquals("CREATE ROLE \"readers\" WITH NOLOGIN", dialect.createRole(role, null).get(0));
    }

    @Test
    void passwordLiteralEscapesSingleQuotes() {
      DbRoleConfig role = new DbRoleConfig();
      role.setName("tricky");
      role.setCanLogin(true);

      String statement = dialect.createRole(role, "a'b").get(0);

      assertTrue(statement.contains("PASSWORD 'a''b'"));
    }

    @Test
    void optionalAttributesAreRenderedWhenSet() {
      DbRoleConfig role = new DbRoleConfig();
      role.setName("ops");
      role.setSuperuser(false);
      role.setCreateDb(true);
      role.setCreateRole(false);
      role.setInherit(true);

      String statement = dialect.createRole(role, null).get(0);

      assertTrue(statement.contains("NOSUPERUSER"));
      assertTrue(statement.contains("CREATEDB"));
      assertTrue(statement.contains("NOCREATEROLE"));
      assertTrue(statement.contains("INHERIT"));
    }

    @Test
    void unsetOptionalAttributesAreOmitted() {
      DbRoleConfig role = new DbRoleConfig();
      role.setName("plain");

      String statement = dialect.createRole(role, null).get(0);

      assertFalse(statement.contains("SUPERUSER"));
      assertFalse(statement.contains("CREATEDB"));
      assertFalse(statement.contains("CREATEROLE"));
    }

    @Test
    void alterRoleUsesAlterKeyword() {
      DbRoleConfig role = new DbRoleConfig();
      role.setName("analyst");
      role.setCanLogin(true);

      assertEquals(
          "ALTER ROLE \"analyst\" WITH LOGIN PASSWORD 'pw'", dialect.alterRole(role, "pw").get(0));
    }

    @Test
    void alterRoleWithoutPasswordOmitsPassword() {
      DbRoleConfig role = new DbRoleConfig();
      role.setName("analyst");
      role.setCanLogin(true);

      assertEquals("ALTER ROLE \"analyst\" WITH LOGIN", dialect.alterRole(role, null).get(0));
    }

    @Test
    void dropRoleRevokesOwnedPrivilegesBeforeDropping() {
      DbRoleConfig role = new DbRoleConfig();
      role.setName("analyst");

      assertEquals(
          List.of("DROP OWNED BY \"analyst\"", "DROP ROLE \"analyst\""), dialect.dropRole(role));
    }
  }

  @Nested
  class SchemaGrants {

    @Test
    void grantRendersPrivilegesSchemaAndRole() {
      String statement =
          dialect.grantOnSchema(
              "analyst", "iot", List.of(SchemaPrivilege.USAGE, SchemaPrivilege.CREATE), false);

      assertEquals("GRANT USAGE, CREATE ON SCHEMA \"iot\" TO \"analyst\"", statement);
    }

    @Test
    void grantWithGrantOptionAppendsClause() {
      String statement =
          dialect.grantOnSchema("analyst", "iot", List.of(SchemaPrivilege.USAGE), true);

      assertTrue(statement.endsWith("WITH GRANT OPTION"));
    }

    @Test
    void revokeRendersFromRole() {
      String statement = dialect.revokeOnSchema("analyst", "iot", List.of(SchemaPrivilege.CREATE));

      assertEquals("REVOKE CREATE ON SCHEMA \"iot\" FROM \"analyst\"", statement);
    }

    @Test
    void revokeGrantOptionKeepsPrivilegeAndStripsOption() {
      String statement =
          dialect.revokeGrantOptionOnSchema("analyst", "iot", List.of(SchemaPrivilege.USAGE));

      assertEquals("REVOKE GRANT OPTION FOR USAGE ON SCHEMA \"iot\" FROM \"analyst\"", statement);
    }

    @Test
    void grantSelectOnTableQualifiesSchemaAndTable() {
      assertEquals(
          "GRANT SELECT ON \"iot\".\"observations\" TO \"reader\"",
          dialect.grantSelectOnTable("reader", "iot", "observations"));
    }

    @Test
    void grantSelectOnTableWithoutSchemaUsesBareTable() {
      assertEquals(
          "GRANT SELECT ON \"observations\" TO \"reader\"",
          dialect.grantSelectOnTable("reader", null, "observations"));
    }

    @Test
    void readSchemaGrantsQueryExposesGrantableState() {
      assertTrue(dialect.readSchemaGrantsQuery().contains("is_grantable"));
    }

    @Test
    void grantWithoutPrivilegesIsRejected() {
      assertThrows(
          IllegalArgumentException.class,
          () -> dialect.grantOnSchema("analyst", "iot", List.of(), false));
    }
  }

  @Nested
  class RoleSqlStateClassification {

    @Test
    void duplicateObjectIsIdentifiedAsDuplicate() {
      assertTrue(dialect.isDuplicate(new SQLException("role exists", "42710")));
    }

    @Test
    void undefinedObjectIsIdentifiedAsMissing() {
      assertTrue(dialect.isMissing(new SQLException("role missing", "42704")));
    }
  }
}
