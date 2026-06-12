/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.postgis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.adapter.ConfigAdapter;
import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.configuration.ApplicationConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.messaging.EventPublisher;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.Config;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.ConfigValue;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.Payload;
import de.civitascore.configadapter.model.postgis.ColumnConfig;
import de.civitascore.configadapter.model.postgis.ColumnType;
import de.civitascore.configadapter.model.postgis.DbRoleConfig;
import de.civitascore.configadapter.model.postgis.GeometryColumnConfig;
import de.civitascore.configadapter.model.postgis.GeometryType;
import de.civitascore.configadapter.model.postgis.IndexConfig;
import de.civitascore.configadapter.model.postgis.IndexConfig.IndexMethod;
import de.civitascore.configadapter.model.postgis.PostgisConfigValue;
import de.civitascore.configadapter.model.postgis.SchemaConfig;
import de.civitascore.configadapter.model.postgis.SchemaGrant;
import de.civitascore.configadapter.model.postgis.SchemaPrivilege;
import de.civitascore.configadapter.model.postgis.TableConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.configuration2.MapConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for {@link PostgisAdapter} against a real PostgreSQL/PostGIS container. Verifies
 * that the generated DDL is accepted by Postgres, that geometry types and GIST indexes work, and
 * that idempotency policy holds on duplicate / missing objects.
 */
class PostgisAdapterIT extends AbstractPostgisIT {

  private PostgisAdapter adapter;
  private RecordingEventPublisher eventPublisher;

  @BeforeEach
  void setUp() {
    adapter = newAdapter(jdbcUrl(), username(), password());
    eventPublisher = new RecordingEventPublisher();
    adapter.setEventPublisher(eventPublisher);
  }

  @AfterEach
  void tearDown() {
    if (adapter != null) {
      adapter.close();
    }
  }

  @Test
  void simpleTableCreatePersistsTableInPublicSchema() throws Exception {
    TableConfig table = new TableConfig();
    table.setName("widgets_it_simple");
    table.setColumns(
        List.of(
            new ColumnConfig("id", ColumnType.BIGINT, null, null, null, false),
            new ColumnConfig("label", ColumnType.TEXT, null, null, null, true)));
    table.setPrimaryKey(List.of("id"));

    adapter.processConfigEvent(
        Topics.SQL_TABLE_CREATED.toString(), createEvent(Operation.CREATE, table));

    assertSingleSuccessfulResult();
    assertTrue(
        tableExists(null, "widgets_it_simple"),
        "widgets_it_simple should exist in the public schema after CREATE");
  }

  @Test
  void tableWithGeometryAndGistIndexIsCreatedSuccessfully() throws Exception {
    TableConfig table = new TableConfig();
    table.setName("places_it");
    table.setColumns(List.of(new ColumnConfig("id", ColumnType.BIGINT, null, null, null, false)));
    table.setGeometryColumns(
        List.of(new GeometryColumnConfig("geom", GeometryType.POINT, 4326, null, false)));
    table.setPrimaryKey(List.of("id"));
    table.setIndexes(
        List.of(new IndexConfig("idx_places_it_geom", List.of("geom"), false, IndexMethod.GIST)));

    adapter.processConfigEvent(
        Topics.SQL_TABLE_CREATED.toString(), createEvent(Operation.CREATE, table));

    assertSingleSuccessfulResult();
    assertTrue(tableExists(null, "places_it"), "places_it table should exist");
    assertTrue(indexExists("idx_places_it_geom"), "GIST index should be created");
  }

  @Test
  void tableInNewSchemaCreatesSchemaAndTableTogether() throws Exception {
    String schema = "iot_it";
    TableConfig table = new TableConfig();
    table.setSchema(schema);
    table.setName("readings_it");
    table.setColumns(
        List.of(
            new ColumnConfig("id", ColumnType.BIGINT, null, null, null, false),
            new ColumnConfig("recorded_at", ColumnType.TIMESTAMPTZ, null, null, null, false)));
    table.setPrimaryKey(List.of("id"));

    adapter.processConfigEvent(
        Topics.SQL_TABLE_CREATED.toString(), createEvent(Operation.CREATE, table));

    assertSingleSuccessfulResult();
    assertTrue(
        tableExists(schema, "readings_it"),
        "readings_it should exist in the dynamically created schema");
  }

  @Test
  void duplicateTableCreateIsAbsorbedAsSuccess() throws Exception {
    TableConfig table = new TableConfig();
    table.setName("dup_it");
    table.setColumns(List.of(new ColumnConfig("id", ColumnType.BIGINT, null, null, null, false)));

    adapter.processConfigEvent(
        Topics.SQL_TABLE_CREATED.toString(), createEvent(Operation.CREATE, table));
    adapter.processConfigEvent(
        Topics.SQL_TABLE_CREATED.toString(), createEvent(Operation.CREATE, table));

    List<ConfigResultEvent> results = eventPublisher.published();
    assertEquals(2, results.size());
    assertTrue(
        results.stream().allMatch(r -> r.status() == ConfigResultEvent.Status.SUCCESS),
        "Both CREATE attempts should be reported as SUCCESS (idempotent on duplicate)");
  }

  @Test
  void redeliveredTableCreateWithSchemaIsAbsorbedAsSuccess() throws Exception {
    TableConfig table = new TableConfig();
    table.setSchema("redeliver_it");
    table.setName("readings_redeliver_it");
    table.setColumns(List.of(new ColumnConfig("id", ColumnType.BIGINT, null, null, null, false)));
    table.setPrimaryKey(List.of("id"));

    adapter.processConfigEvent(
        Topics.SQL_TABLE_CREATED.toString(), createEvent(Operation.CREATE, table));
    adapter.processConfigEvent(
        Topics.SQL_TABLE_CREATED.toString(), createEvent(Operation.CREATE, table));

    List<ConfigResultEvent> results = eventPublisher.published();
    assertEquals(2, results.size());
    assertTrue(
        results.stream().allMatch(r -> r.status() == ConfigResultEvent.Status.SUCCESS),
        "Redelivered CREATE of a schema-qualified table must absorb the duplicate schema"
            + " and still treat the duplicate table as success");
  }

  @Test
  void deleteRemovesExistingTable() throws Exception {
    executeSql("CREATE TABLE \"to_drop_it\" (id BIGINT)");
    TableConfig table = new TableConfig();
    table.setName("to_drop_it");

    adapter.processConfigEvent(
        Topics.SQL_TABLE_DELETED.toString(), createEvent(Operation.DELETE, table));

    assertSingleSuccessfulResult();
    assertFalse(tableExists(null, "to_drop_it"), "to_drop_it should be gone after DELETE");
  }

  @Test
  void deleteOnMissingTableIsAbsorbedAsSuccess() throws Exception {
    TableConfig table = new TableConfig();
    table.setName("never_existed_it");

    adapter.processConfigEvent(
        Topics.SQL_TABLE_DELETED.toString(), createEvent(Operation.DELETE, table));

    assertSingleSuccessfulResult();
  }

  @Test
  void updateOperationIsRejectedAsUnsupported() {
    TableConfig table = new TableConfig();
    table.setName("anything");

    FatalAdapterException thrown =
        assertThrows(
            FatalAdapterException.class,
            () ->
                adapter.processConfigEvent(
                    Topics.SQL_TABLE_UPDATED.toString(), createEvent(Operation.UPDATE, table)));

    assertEquals(AdapterErrorCode.UNSUPPORTED_OPERATION, thrown.getErrorCode());
  }

  @Test
  void invalidDdlIsReportedAsFatalAdapterException() {
    TableConfig table = new TableConfig();
    table.setName("bad_it");
    table.setColumns(List.of(new ColumnConfig("id", ColumnType.BIGINT, null, null, null, false)));
    // Reference a column that does not exist in the table — Postgres rejects with
    // SQLState 42703 (undefined_column), which the adapter must classify as fatal.
    table.setPrimaryKey(List.of("does_not_exist"));

    FatalAdapterException thrown =
        assertThrows(
            FatalAdapterException.class,
            () ->
                adapter.processConfigEvent(
                    Topics.SQL_TABLE_CREATED.toString(), createEvent(Operation.CREATE, table)));

    assertEquals(AdapterErrorCode.POSTGIS_DDL_ERROR, thrown.getErrorCode());
    assertFalse(
        eventPublisher.published().stream()
            .anyMatch(r -> r.status() == ConfigResultEvent.Status.SUCCESS),
        "No SUCCESS result should be published for a failed CREATE");
  }

  @Test
  void createSchemaWithOwnerPersistsSchema() throws Exception {
    executeSql("CREATE ROLE \"schema_owner_it\"");
    SchemaConfig schema = new SchemaConfig();
    schema.setName("owned_it");
    schema.setOwner("schema_owner_it");

    adapter.processConfigEvent(
        Topics.SQL_SCHEMA_CREATED.toString(), createEvent(Operation.CREATE, schema, "owned_it"));

    assertSingleSuccessfulResult();
    assertTrue(schemaExists("owned_it"), "owned_it schema should exist");
    assertEquals("schema_owner_it", schemaOwner("owned_it"));
  }

  @Test
  void deleteSchemaWithCascadeRemovesContainedObjects() throws Exception {
    executeSql("CREATE SCHEMA \"cascade_it\"");
    executeSql("CREATE TABLE \"cascade_it\".\"t\" (id BIGINT)");
    SchemaConfig schema = new SchemaConfig();
    schema.setName("cascade_it");
    schema.setCascade(true);

    adapter.processConfigEvent(
        Topics.SQL_SCHEMA_DELETED.toString(), createEvent(Operation.DELETE, schema, "cascade_it"));

    assertSingleSuccessfulResult();
    assertFalse(schemaExists("cascade_it"), "cascade_it schema should be gone");
  }

  @Test
  void deleteNonEmptySchemaWithoutCascadeFailsFatally() throws Exception {
    executeSql("CREATE SCHEMA \"restrict_it\"");
    executeSql("CREATE TABLE \"restrict_it\".\"t\" (id BIGINT)");
    SchemaConfig schema = new SchemaConfig();
    schema.setName("restrict_it");

    assertThrows(
        FatalAdapterException.class,
        () ->
            adapter.processConfigEvent(
                Topics.SQL_SCHEMA_DELETED.toString(),
                createEvent(Operation.DELETE, schema, "restrict_it")));
    assertTrue(schemaExists("restrict_it"), "RESTRICT drop must not remove a non-empty schema");
  }

  @Test
  void createLoginRoleWithPasswordPersistsRole() throws Exception {
    DbRoleConfig role = new DbRoleConfig();
    role.setName("login_it");
    role.setCanLogin(true);
    role.setPassword("plaintext-secret");

    adapter.processConfigEvent(
        Topics.SQL_ROLE_CREATED.toString(), createEvent(Operation.CREATE, role, "login_it"));

    assertSingleSuccessfulResult();
    assertTrue(roleExists("login_it"), "login_it role should exist");
    assertTrue(roleCanLogin("login_it"), "login_it role should be allowed to log in");
  }

  @Test
  void createRoleWithGrantsAppliesSchemaPrivileges() throws Exception {
    executeSql("CREATE SCHEMA IF NOT EXISTS \"granted_it\"");
    DbRoleConfig role = new DbRoleConfig();
    role.setName("granted_role_it");
    role.setGrants(
        List.of(
            new SchemaGrant(
                "granted_it", List.of(SchemaPrivilege.USAGE, SchemaPrivilege.CREATE), null)));

    adapter.processConfigEvent(
        Topics.SQL_ROLE_CREATED.toString(), createEvent(Operation.CREATE, role, "granted_role_it"));

    assertSingleSuccessfulResult();
    assertTrue(hasSchemaPrivilege("granted_role_it", "granted_it", "USAGE"));
    assertTrue(hasSchemaPrivilege("granted_role_it", "granted_it", "CREATE"));
  }

  @Test
  void redeliveredRoleCreateWithGrantsIsAbsorbedAsSuccess() throws Exception {
    executeSql("CREATE SCHEMA IF NOT EXISTS \"regrant_it\"");
    DbRoleConfig role = new DbRoleConfig();
    role.setName("regrant_role_it");
    role.setGrants(List.of(new SchemaGrant("regrant_it", List.of(SchemaPrivilege.USAGE), null)));

    adapter.processConfigEvent(
        Topics.SQL_ROLE_CREATED.toString(), createEvent(Operation.CREATE, role, "regrant_role_it"));
    adapter.processConfigEvent(
        Topics.SQL_ROLE_CREATED.toString(), createEvent(Operation.CREATE, role, "regrant_role_it"));

    List<ConfigResultEvent> results = eventPublisher.published();
    assertEquals(2, results.size());
    assertTrue(
        results.stream().allMatch(r -> r.status() == ConfigResultEvent.Status.SUCCESS),
        "Redelivered CREATE of a role with grants must absorb the duplicate role"
            + " and still apply the grant statements");
  }

  @Test
  void updateRoleReconcilesGrantsRevokingRemovedPrivilege() throws Exception {
    executeSql("CREATE SCHEMA IF NOT EXISTS \"reconcile_it\"");
    executeSql("CREATE ROLE \"reconcile_role_it\"");
    executeSql("GRANT USAGE, CREATE ON SCHEMA \"reconcile_it\" TO \"reconcile_role_it\"");

    DbRoleConfig role = new DbRoleConfig();
    role.setName("reconcile_role_it");
    role.setGrants(List.of(new SchemaGrant("reconcile_it", List.of(SchemaPrivilege.USAGE), null)));

    adapter.processConfigEvent(
        Topics.SQL_ROLE_UPDATED.toString(),
        createEvent(Operation.UPDATE, role, "reconcile_role_it"));

    assertSingleSuccessfulResult();
    assertTrue(
        hasSchemaPrivilege("reconcile_role_it", "reconcile_it", "USAGE"),
        "USAGE should be retained");
    assertFalse(
        hasSchemaPrivilege("reconcile_role_it", "reconcile_it", "CREATE"),
        "CREATE should have been revoked by reconciliation");
  }

  @Test
  void updateRoleUpgradesHeldPrivilegeToWithGrantOption() throws Exception {
    executeSql("CREATE SCHEMA IF NOT EXISTS \"optionup_it\"");
    executeSql("CREATE ROLE \"optionup_role_it\"");
    executeSql("GRANT USAGE ON SCHEMA \"optionup_it\" TO \"optionup_role_it\"");

    DbRoleConfig role = new DbRoleConfig();
    role.setName("optionup_role_it");
    role.setGrants(List.of(new SchemaGrant("optionup_it", List.of(SchemaPrivilege.USAGE), true)));

    adapter.processConfigEvent(
        Topics.SQL_ROLE_UPDATED.toString(),
        createEvent(Operation.UPDATE, role, "optionup_role_it"));

    assertSingleSuccessfulResult();
    assertTrue(
        hasSchemaPrivilege("optionup_role_it", "optionup_it", "USAGE WITH GRANT OPTION"),
        "USAGE should have been upgraded to WITH GRANT OPTION");
  }

  @Test
  void updateRoleStripsGrantOptionWhileKeepingPrivilege() throws Exception {
    executeSql("CREATE SCHEMA IF NOT EXISTS \"optiondown_it\"");
    executeSql("CREATE ROLE \"optiondown_role_it\"");
    executeSql(
        "GRANT USAGE ON SCHEMA \"optiondown_it\" TO \"optiondown_role_it\" WITH GRANT OPTION");

    DbRoleConfig role = new DbRoleConfig();
    role.setName("optiondown_role_it");
    role.setGrants(
        List.of(new SchemaGrant("optiondown_it", List.of(SchemaPrivilege.USAGE), null)));

    adapter.processConfigEvent(
        Topics.SQL_ROLE_UPDATED.toString(),
        createEvent(Operation.UPDATE, role, "optiondown_role_it"));

    assertSingleSuccessfulResult();
    assertTrue(
        hasSchemaPrivilege("optiondown_role_it", "optiondown_it", "USAGE"),
        "USAGE itself should be retained");
    assertFalse(
        hasSchemaPrivilege("optiondown_role_it", "optiondown_it", "USAGE WITH GRANT OPTION"),
        "the grant option should have been revoked");
  }

  @Test
  void deleteRoleRemovesIt() throws Exception {
    executeSql("CREATE ROLE \"doomed_role_it\"");
    DbRoleConfig role = new DbRoleConfig();
    role.setName("doomed_role_it");

    adapter.processConfigEvent(
        Topics.SQL_ROLE_DELETED.toString(), createEvent(Operation.DELETE, role, "doomed_role_it"));

    assertSingleSuccessfulResult();
    assertFalse(roleExists("doomed_role_it"), "doomed_role_it should be gone");
  }

  @Test
  void unreachableJdbcUrlCausesRetryableException() {
    PostgisAdapter offlineAdapter =
        newAdapter("jdbc:postgresql://127.0.0.1:1/never_there", "u", "p");
    offlineAdapter.setEventPublisher(eventPublisher);

    TableConfig table = new TableConfig();
    table.setName("any_offline");
    table.setColumns(List.of(new ColumnConfig("id", ColumnType.BIGINT, null, null, null, false)));

    try {
      assertThrows(
          RetryableAdapterException.class,
          () ->
              offlineAdapter.processConfigEvent(
                  Topics.SQL_TABLE_CREATED.toString(), createEvent(Operation.CREATE, table)));
    } finally {
      offlineAdapter.close();
    }
  }

  // --- Helpers ---

  private static PostgisAdapter newAdapter(String url, String user, String password) {
    Map<String, Object> props = new HashMap<>();
    props.put("postgis.jdbc.url", url);
    props.put("postgis.jdbc.user", user);
    props.put("postgis.jdbc.password", password);
    props.put("postgis.jdbc.maxPoolSize", "2");
    props.put("postgis.jdbc.connectionTimeoutMs", "2000");
    props.put(
        "postgis.topics",
        String.join(
            ",",
            Topics.SQL_TABLE_CREATED.toString(),
            Topics.SQL_TABLE_UPDATED.toString(),
            Topics.SQL_TABLE_DELETED.toString(),
            Topics.SQL_SCHEMA_CREATED.toString(),
            Topics.SQL_SCHEMA_UPDATED.toString(),
            Topics.SQL_SCHEMA_DELETED.toString(),
            Topics.SQL_ROLE_CREATED.toString(),
            Topics.SQL_ROLE_UPDATED.toString(),
            Topics.SQL_ROLE_DELETED.toString()));
    AppConfig config = new AppConfig(new MapConfiguration(props));

    PostgisAdapter created = new PostgisAdapter();
    created.initialize(config);
    return created;
  }

  private static ConfigEvent createEvent(Operation operation, TableConfig table) {
    return createEvent(operation, table, table.qualifiedName());
  }

  private static ConfigEvent createEvent(
      Operation operation, ConfigValue value, String resourceName) {
    Metadata metadata =
        new Metadata(
            UUID.randomUUID().toString(),
            OffsetDateTime.now(),
            "postgis-it",
            UUID.randomUUID().toString(),
            "1.0",
            "result.topic");
    Payload payload =
        new Payload(PostgisAdapter.ADAPTER_NAME, resourceName, operation, new Config(null, value));
    return new ConfigEvent(metadata, payload);
  }

  private void assertSingleSuccessfulResult() {
    List<ConfigResultEvent> results = eventPublisher.published();
    assertEquals(1, results.size(), "Exactly one result event should be published");
    ConfigResultEvent result = results.get(0);
    assertEquals(
        ConfigResultEvent.Status.SUCCESS,
        result.status(),
        () -> "Operation should succeed, but got: " + result.message());
    assertEquals(PostgisConfigValue.POSTGIS_RESULT_TYPE, result.resultType());
  }

  private static boolean indexExists(String indexName) throws SQLException {
    try (Connection connection = DriverManager.getConnection(jdbcUrl(), username(), password());
        PreparedStatement ps =
            connection.prepareStatement("SELECT count(*) FROM pg_indexes WHERE indexname = ?")) {
      ps.setString(1, indexName);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() && rs.getInt(1) > 0;
      }
    }
  }

  private static boolean schemaExists(String schema) throws SQLException {
    return queryCount(
            "SELECT count(*) FROM information_schema.schemata WHERE schema_name = ?", schema)
        > 0;
  }

  private static String schemaOwner(String schema) throws SQLException {
    try (Connection connection = DriverManager.getConnection(jdbcUrl(), username(), password());
        PreparedStatement ps =
            connection.prepareStatement(
                "SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname = ?")) {
      ps.setString(1, schema);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getString(1) : null;
      }
    }
  }

  private static boolean roleExists(String role) throws SQLException {
    return queryCount("SELECT count(*) FROM pg_roles WHERE rolname = ?", role) > 0;
  }

  private static boolean roleCanLogin(String role) throws SQLException {
    try (Connection connection = DriverManager.getConnection(jdbcUrl(), username(), password());
        PreparedStatement ps =
            connection.prepareStatement("SELECT rolcanlogin FROM pg_roles WHERE rolname = ?")) {
      ps.setString(1, role);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() && rs.getBoolean(1);
      }
    }
  }

  private static boolean hasSchemaPrivilege(String role, String schema, String privilege)
      throws SQLException {
    try (Connection connection = DriverManager.getConnection(jdbcUrl(), username(), password());
        PreparedStatement ps =
            connection.prepareStatement("SELECT has_schema_privilege(?, ?, ?)")) {
      ps.setString(1, role);
      ps.setString(2, schema);
      ps.setString(3, privilege);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() && rs.getBoolean(1);
      }
    }
  }

  private static int queryCount(String sql, String param) throws SQLException {
    try (Connection connection = DriverManager.getConnection(jdbcUrl(), username(), password());
        PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setString(1, param);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : 0;
      }
    }
  }

  static class RecordingEventPublisher implements EventPublisher {
    private final List<ConfigResultEvent> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void publish(String topic, ConfigResultEvent event) {
      events.add(event);
    }

    public List<ConfigResultEvent> published() {
      return new ArrayList<>(events);
    }

    @Override
    public String getName() {
      return "recording";
    }

    @Override
    public void initialize(ApplicationConfig config, ConfigAdapter adapter) {}
  }
}
