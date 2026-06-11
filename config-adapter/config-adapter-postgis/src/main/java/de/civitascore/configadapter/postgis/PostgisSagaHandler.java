/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.postgis;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.crypto.CredentialDecryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.configadapter.model.postgis.ColumnConfig;
import de.civitascore.configadapter.model.postgis.DbRoleConfig;
import de.civitascore.configadapter.model.postgis.GeometryColumnConfig;
import de.civitascore.configadapter.model.postgis.IndexConfig;
import de.civitascore.configadapter.model.postgis.PostgisConfigValue;
import de.civitascore.configadapter.model.postgis.SchemaConfig;
import de.civitascore.configadapter.model.postgis.SchemaGrant;
import de.civitascore.configadapter.model.postgis.SchemaPrivilege;
import de.civitascore.configadapter.model.postgis.TableConfig;
import de.civitascore.configadapter.postgis.ddl.GrantReconciler;
import de.civitascore.configadapter.postgis.ddl.GrantReconciler.GrantReconcilePlan;
import de.civitascore.configadapter.postgis.ddl.GrantReconciler.SchemaRevoke;
import de.civitascore.configadapter.postgis.ddl.TableDdlBuilder;
import de.civitascore.configadapter.postgis.dialect.PostgisDialect;
import de.civitascore.configadapter.postgis.dialect.SqlDialect;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saga command handler for PostGIS, mirroring the FROST / APISIX / RedPanda handlers but executing
 * DDL over JDBC instead of HTTP. Discovered via {@link java.util.ServiceLoader} and registered in
 * the saga orchestrator's handler registry.
 *
 * <p>Two families of operations:
 *
 * <p><b>1. Generic resource operations</b> (nested config payload under {@code tableConfig} /
 * {@code schemaConfig} / {@code roleConfig}, each carrying its {@code resourceType} discriminator):
 *
 * <ul>
 *   <li>{@code CREATE_TABLE} ↔ {@code DROP_TABLE}
 *   <li>{@code CREATE_SCHEMA} ↔ {@code DROP_SCHEMA}
 *   <li>{@code CREATE_ROLE} ↔ {@code DROP_ROLE}
 * </ul>
 *
 * <p><b>2. Dataset-saga sink provisioning</b> — {@code PROVISION_SINK} ↔ {@code DEPROVISION_SINK}.
 * These read the dataset trigger directly (no nested config) and provision the PostGIS objects a
 * GeoServer datastore publishes from. For every {@code POSTGIS} entry in the trigger's {@code
 * dataSinks}, the {@code configuration} carries the table definition:
 *
 * <pre>{@code
 * { "dataSinkType": "POSTGIS",
 *   "configuration": {
 *     "schema": "ds_42",                 // optional; created (idempotent) if present
 *     "owner": "ds_42_admin",            // optional schema owner
 *     "tableName": "sensor_readings",    // required; the table GeoServer reads
 *     "columns": [ {name,type,...} ],    // required (>=1 column or geometryColumn)
 *     "geometryColumns": [ {name,geometryType,srid,...} ],
 *     "primaryKey": ["id"],
 *     "indexes": [ {...} ],
 *     "readRole": { "name":"ds_42_geo", "canLogin":true,
 *                   "password":"ENC(...)", "privileges":["USAGE"] } } }  // optional GeoServer role
 * }</pre>
 *
 * {@code PROVISION_SINK} creates schema (if given), table, and read role + grants for each sink in
 * one transaction; {@code DEPROVISION_SINK} drops the table and role (schemas are left, as they may
 * be shared). Both re-derive their targets from {@code dataSinks}, so the
 * compensation/forward-delete paths are symmetric.
 *
 * <p>CREATE/PROVISION are idempotent (duplicate-object SQLStates absorbed); DROP/DEPROVISION are
 * idempotent (missing-object SQLStates absorbed), so steps and compensations are safe to retry.
 *
 * <p>Role passwords may be encrypted {@code ENC(...)} values, decrypted with {@code
 * CIVITAS_MASTER_KEY} exactly as in {@link PostgisAdapter}. Plaintext passwords are never logged.
 */
public class PostgisSagaHandler implements SagaCommandHandler {

  private static final Logger logger = LoggerFactory.getLogger(PostgisSagaHandler.class);

  private static final String ADAPTER_NAME = "postgis";
  private static final String ROLE_CREDENTIAL_CONTEXT = PostgisAdapter.ROLE_CREDENTIAL_CONTEXT;
  private static final String MASTER_KEY_ENV = "CIVITAS_MASTER_KEY";

  private static final String COMPENSATE_TYPE = "COMPENSATE_STEP";
  private static final String DATASINK_TYPE_POSTGIS = "POSTGIS";

  private static final String JDBC_URL_KEY = "postgis.jdbc.url";
  private static final String JDBC_USER_KEY = "postgis.jdbc.user";
  private static final String JDBC_PASSWORD_KEY = "postgis.jdbc.password";
  private static final String JDBC_MAX_POOL_SIZE_KEY = "postgis.jdbc.maxPoolSize";
  private static final String JDBC_CONNECTION_TIMEOUT_MS_KEY = "postgis.jdbc.connectionTimeoutMs";

  private static final String DEFAULT_MAX_POOL_SIZE = "5";
  private static final String DEFAULT_CONNECTION_TIMEOUT_MS = "5000";

  private static final Pattern PASSWORD_LITERAL =
      Pattern.compile("(?i)(PASSWORD\\s+)'(?:[^']|'')*'");

  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private ConnectionProvider connectionProvider;
  private SqlDialect dialect;
  private TableDdlBuilder ddlBuilder;
  private byte[] stretchedKey = new byte[0];

  /** No-arg constructor for ServiceLoader discovery. Call {@link #initialize} before use. */
  public PostgisSagaHandler() {}

  @Override
  public String adapter() {
    return ADAPTER_NAME;
  }

  @Override
  public void initialize(AdapterConfig config) {
    String jdbcUrl = requireProperty(config, JDBC_URL_KEY);
    String user = requireProperty(config, JDBC_USER_KEY);
    String password = config.getProperty(JDBC_PASSWORD_KEY, "");
    int maxPoolSize =
        Integer.parseInt(config.getProperty(JDBC_MAX_POOL_SIZE_KEY, DEFAULT_MAX_POOL_SIZE));
    long connectionTimeoutMs =
        Long.parseLong(
            config.getProperty(JDBC_CONNECTION_TIMEOUT_MS_KEY, DEFAULT_CONNECTION_TIMEOUT_MS));

    if (this.connectionProvider == null) {
      this.connectionProvider =
          new ConnectionProvider(jdbcUrl, user, password, maxPoolSize, connectionTimeoutMs);
    }
    if (this.dialect == null) {
      this.dialect = new PostgisDialect();
    }
    this.ddlBuilder = new TableDdlBuilder(this.dialect);

    this.stretchedKey = CryptoKeyLoader.loadAndStretchKeyFromEnv(MASTER_KEY_ENV);
    if (this.stretchedKey.length == 0) {
      logger.warn("{} not set — encrypted role passwords cannot be decrypted", MASTER_KEY_ENV);
    }

    logger.info("PostgisSagaHandler initialized for: {}", Encode.forJava(jdbcUrl));
  }

  /** Test seam — inject a pre-configured provider before {@link #initialize(AdapterConfig)}. */
  void setConnectionProvider(ConnectionProvider connectionProvider) {
    this.connectionProvider = connectionProvider;
  }

  /** Test seam — inject an alternate dialect. */
  void setDialect(SqlDialect dialect) {
    this.dialect = dialect;
  }

  @Override
  public SagaCommandResult handle(SagaCommandMessage command) {
    boolean compensation = COMPENSATE_TYPE.equals(command.type());
    try {
      return switch (command.operation()) {
        case "CREATE_TABLE" -> createTable(command);
        case "DROP_TABLE" -> dropTable(command, compensation);
        case "CREATE_SCHEMA" -> createSchema(command);
        case "DROP_SCHEMA" -> dropSchema(command, compensation);
        case "CREATE_ROLE" -> createRole(command);
        case "DROP_ROLE" -> dropRole(command, compensation);
        case "PROVISION_SINK" -> provisionSink(command);
        case "DEPROVISION_SINK" -> deprovisionSink(command, compensation);
        default -> unknownOperation(command, compensation);
      };
    } catch (Exception e) {
      String error = command.operation() + " failed: " + redact(e.getMessage());
      logger.error(
          "PostGIS {} failed for saga {}",
          Encode.forJava(command.operation()),
          Encode.forJava(command.sagaId()),
          e);
      return compensation
          ? SagaCommandResult.compensationFailure(command.sagaId(), command.stepId(), error)
          : SagaCommandResult.failure(command.sagaId(), command.stepId(), error);
    }
  }

  // ─── Forward operations ────────────────────────────────────────────────────

  private SagaCommandResult createTable(SagaCommandMessage command) throws SQLException {
    TableConfig table = convert(command, "tableConfig", TableConfig.class);
    runDdl(ddlBuilder.buildCreate(table), true, false);

    Map<String, Object> identifiers = new java.util.HashMap<>();
    if (table.getSchema() != null && !table.getSchema().isBlank()) {
      identifiers.put("schema", table.getSchema());
    }
    identifiers.put("table", table.getName());
    logger.info(
        "PostGIS table {} created, saga={}",
        Encode.forJava(table.qualifiedName()),
        Encode.forJava(command.sagaId()));
    return SagaCommandResult.success(
        command.sagaId(), command.stepId(), Map.copyOf(identifiers), Map.copyOf(identifiers));
  }

  private SagaCommandResult createSchema(SagaCommandMessage command) throws SQLException {
    SchemaConfig schema = convert(command, "schemaConfig", SchemaConfig.class);
    runDdl(dialect.createSchema(schema), true, false);

    Map<String, Object> identifiers = Map.of("schema", schema.getName());
    logger.info(
        "PostGIS schema {} created, saga={}",
        Encode.forJava(schema.getName()),
        Encode.forJava(command.sagaId()));
    return SagaCommandResult.success(command.sagaId(), command.stepId(), identifiers, identifiers);
  }

  private SagaCommandResult createRole(SagaCommandMessage command) throws SQLException {
    DbRoleConfig role = convert(command, "roleConfig", DbRoleConfig.class);
    String password = resolvePassword(role.getPassword());

    List<String> statements = new ArrayList<>(dialect.createRole(role, password));
    appendGrantStatements(statements, role.getName(), role.getGrants());
    runDdl(statements, true, false);

    Map<String, Object> identifiers = Map.of("role", role.getName());
    logger.info(
        "PostGIS role {} created, saga={}",
        Encode.forJava(role.getName()),
        Encode.forJava(command.sagaId()));
    return SagaCommandResult.success(command.sagaId(), command.stepId(), identifiers, identifiers);
  }

  // ─── Dataset-saga sink provisioning ─────────────────────────────────────────

  private SagaCommandResult provisionSink(SagaCommandMessage command) throws SQLException {
    List<SinkSpec> sinks = parseSinks(command);
    List<String> statements = new ArrayList<>();
    List<Map<String, Object>> provisioned = new ArrayList<>();
    for (SinkSpec sink : sinks) {
      // buildCreate already emits CREATE SCHEMA for the table's schema; only the owner (if any)
      // needs a separate ALTER SCHEMA … OWNER TO.
      statements.addAll(ddlBuilder.buildCreate(sink.table()));
      if (sink.schema() != null) {
        statements.addAll(dialect.alterSchemaOwner(sink.schema()));
      }
      if (sink.role() != null) {
        String password = resolvePassword(sink.role().getPassword());
        statements.addAll(dialect.createRole(sink.role(), password));
        appendGrantStatements(statements, sink.role().getName(), sink.role().getGrants());
      }
      provisioned.add(sink.identifiers());
    }
    runDdl(statements, true, false);

    logger.info(
        "PostGIS sink(s) provisioned: {}, saga={}",
        Encode.forJava(String.valueOf(provisioned)),
        Encode.forJava(command.sagaId()));
    Map<String, Object> data = Map.of("provisionedSinks", provisioned);
    return SagaCommandResult.success(command.sagaId(), command.stepId(), data, data);
  }

  private SagaCommandResult deprovisionSink(SagaCommandMessage command, boolean compensation)
      throws SQLException {
    List<SinkSpec> sinks = parseSinks(command);
    List<String> statements = new ArrayList<>();
    for (SinkSpec sink : sinks) {
      if (sink.role() != null) {
        statements.addAll(dialect.dropRole(sink.role()));
      }
      statements.addAll(dialect.dropTable(sink.table().getSchema(), sink.table().getName()));
      // The schema is intentionally left in place: it may be shared across datasets. The table and
      // read role are the per-dataset artifacts this step removes.
    }
    runDdl(statements, false, true);

    logger.info("PostGIS sink(s) deprovisioned, saga={}", Encode.forJava(command.sagaId()));
    return done(command, compensation);
  }

  // ─── Drop operations (also serve as compensations) ──────────────────────────

  private SagaCommandResult dropTable(SagaCommandMessage command, boolean compensation)
      throws SQLException {
    String schema = optionalString(command, "schema");
    String table = requireString(command, "table");
    runDdl(dialect.dropTable(schema, table), false, true);
    logger.info(
        "PostGIS table {} dropped, saga={}",
        Encode.forJava(qualified(schema, table)),
        Encode.forJava(command.sagaId()));
    return done(command, compensation);
  }

  private SagaCommandResult dropSchema(SagaCommandMessage command, boolean compensation)
      throws SQLException {
    SchemaConfig schema = new SchemaConfig();
    schema.setName(requireString(command, "schema"));
    schema.setCascade(Boolean.parseBoolean(String.valueOf(command.payload().get("cascade"))));
    runDdl(dialect.dropSchema(schema), false, true);
    logger.info(
        "PostGIS schema {} dropped, saga={}",
        Encode.forJava(schema.getName()),
        Encode.forJava(command.sagaId()));
    return done(command, compensation);
  }

  private SagaCommandResult dropRole(SagaCommandMessage command, boolean compensation)
      throws SQLException {
    DbRoleConfig role = new DbRoleConfig();
    role.setName(requireString(command, "role"));
    runDdl(dialect.dropRole(role), false, true);
    logger.info(
        "PostGIS role {} dropped, saga={}",
        Encode.forJava(role.getName()),
        Encode.forJava(command.sagaId()));
    return done(command, compensation);
  }

  // ─── DDL execution ───────────────────────────────────────────────────────

  private void runDdl(List<String> statements, boolean absorbDuplicate, boolean absorbMissing)
      throws SQLException {
    try (Connection connection = connectionProvider.getConnection()) {
      connection.setAutoCommit(false);
      try (Statement stmt = connection.createStatement()) {
        for (String sql : statements) {
          executeOne(connection, stmt, sql, absorbDuplicate, absorbMissing);
        }
        connection.commit();
      } catch (SQLException | RuntimeException e) {
        safeRollback(connection);
        throw e;
      }
    }
  }

  /**
   * Executes one DDL statement, wrapped in a savepoint. An absorbed duplicate/missing object rolls
   * back to the savepoint rather than the whole transaction: PostgreSQL aborts the entire
   * transaction on any error, so without the savepoint the following statements in a
   * multi-statement step (schema → table → role) would fail with "current transaction is aborted".
   * The savepoint keeps the idempotency-by-absorption contract working inside one transaction.
   */
  private void executeOne(
      Connection connection,
      Statement stmt,
      String sql,
      boolean absorbDuplicate,
      boolean absorbMissing)
      throws SQLException {
    Savepoint savepoint = connection.setSavepoint();
    try {
      logger.debug("Executing DDL: {}", Encode.forJava(redact(sql)));
      stmt.execute(sql);
      connection.releaseSavepoint(savepoint);
    } catch (SQLException e) {
      boolean absorb =
          (absorbDuplicate && dialect.isDuplicate(e)) || (absorbMissing && dialect.isMissing(e));
      if (!absorb) {
        throw e;
      }
      connection.rollback(savepoint);
      logger.info(
          "PostGIS object already in desired state (SQLState {}), treating as success (idempotent)",
          Encode.forJava(String.valueOf(e.getSQLState())));
    }
  }

  private void appendGrantStatements(
      List<String> statements, String roleName, List<SchemaGrant> grants) {
    GrantReconcilePlan plan = GrantReconciler.reconcile(grants, Map.of());
    for (SchemaGrant grant : plan.toGrant()) {
      statements.add(
          dialect.grantOnSchema(
              roleName, grant.schema(), grant.effectivePrivileges(), grant.isWithGrantOption()));
    }
    for (SchemaRevoke revoke : plan.toRevoke()) {
      statements.add(dialect.revokeOnSchema(roleName, revoke.schema(), revoke.privileges()));
    }
  }

  private void safeRollback(Connection connection) {
    try {
      connection.rollback();
    } catch (SQLException rollbackEx) {
      logger.warn("Rollback failed: {}", Encode.forJava(String.valueOf(rollbackEx.getMessage())));
    }
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private SagaCommandResult done(SagaCommandMessage command, boolean compensation) {
    return compensation
        ? SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId())
        : SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());
  }

  private SagaCommandResult unknownOperation(SagaCommandMessage command, boolean compensation) {
    String error = "Unknown postgis operation: " + command.operation();
    logger.warn(error);
    return compensation
        ? SagaCommandResult.compensationFailure(command.sagaId(), command.stepId(), error)
        : SagaCommandResult.failure(command.sagaId(), command.stepId(), error);
  }

  /**
   * Converts a nested config map into the expected {@link PostgisConfigValue} subtype. The map must
   * carry the {@code resourceType} discriminator (e.g. {@code "sql-table"}), exactly as the config
   * travels inside CloudEvents.
   */
  private <T extends PostgisConfigValue> T convert(
      SagaCommandMessage command, String key, Class<T> type) {
    Object value = command.payload().get(key);
    if (value == null) {
      throw new IllegalArgumentException(
          command.operation() + " payload missing required field: " + key);
    }
    PostgisConfigValue config = objectMapper.convertValue(value, PostgisConfigValue.class);
    if (!type.isInstance(config)) {
      throw new IllegalArgumentException(
          command.operation()
              + " payload field '"
              + key
              + "' is a "
              + config.getClass().getSimpleName()
              + ", expected "
              + type.getSimpleName());
    }
    return type.cast(config);
  }

  /** One PostGIS data sink resolved into the objects to provision/drop. */
  private record SinkSpec(SchemaConfig schema, TableConfig table, DbRoleConfig role) {
    Map<String, Object> identifiers() {
      Map<String, Object> id = new LinkedHashMap<>();
      if (table.getSchema() != null && !table.getSchema().isBlank()) {
        id.put("schema", table.getSchema());
      }
      id.put("table", table.getName());
      if (role != null) {
        id.put("role", role.getName());
      }
      return id;
    }
  }

  /**
   * Parses the trigger's {@code dataSinks} into per-sink specs. See the class javadoc for the
   * {@code POSTGIS} sink {@code configuration} shape. Used identically by {@code PROVISION_SINK}
   * and {@code DEPROVISION_SINK} so the forward and rollback paths stay symmetric.
   */
  private List<SinkSpec> parseSinks(SagaCommandMessage command) {
    List<SinkSpec> specs = new ArrayList<>();
    for (Map<String, Object> sink : mapList(command.payload(), "dataSinks")) {
      if (!DATASINK_TYPE_POSTGIS.equals(sink.get("dataSinkType"))) {
        continue;
      }
      Map<String, Object> config = mapValue(sink, "configuration");
      String tableName = stringValue(config, "tableName");
      if (tableName == null || tableName.isBlank()) {
        throw new IllegalArgumentException("POSTGIS data sink is missing configuration.tableName");
      }
      String schemaName = stringValue(config, "schema");

      SchemaConfig schema = null;
      if (schemaName != null && !schemaName.isBlank()) {
        schema = new SchemaConfig();
        schema.setName(schemaName);
        schema.setOwner(stringValue(config, "owner"));
      }

      TableConfig table = new TableConfig();
      table.setSchema(schemaName);
      table.setName(tableName);
      table.setColumns(convertList(config.get("columns"), ColumnConfig.class));
      table.setGeometryColumns(
          convertList(config.get("geometryColumns"), GeometryColumnConfig.class));
      table.setPrimaryKey(stringList(config.get("primaryKey")));
      table.setIndexes(convertList(config.get("indexes"), IndexConfig.class));
      if (table.getColumns().isEmpty() && table.getGeometryColumns().isEmpty()) {
        throw new IllegalArgumentException(
            "POSTGIS data sink '" + tableName + "' configuration must define at least one column");
      }

      specs.add(new SinkSpec(schema, table, parseReadRole(config, schemaName)));
    }
    if (specs.isEmpty()) {
      throw new IllegalArgumentException(
          command.operation() + " requires at least one POSTGIS data sink in the payload");
    }
    return specs;
  }

  private DbRoleConfig parseReadRole(Map<String, Object> config, String schemaName) {
    if (!(config.get("readRole") instanceof Map<?, ?> roleMap)) {
      return null;
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> roleConfig = (Map<String, Object>) roleMap;
    String name = stringValue(roleConfig, "name");
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("POSTGIS sink readRole is missing 'name'");
    }
    DbRoleConfig role = new DbRoleConfig();
    role.setName(name);
    role.setCanLogin(Boolean.TRUE.equals(roleConfig.get("canLogin")));
    role.setPassword(stringValue(roleConfig, "password"));
    if (schemaName != null && !schemaName.isBlank()) {
      role.setGrants(
          List.of(
              new SchemaGrant(schemaName, parsePrivileges(roleConfig.get("privileges")), null)));
    }
    return role;
  }

  private static List<SchemaPrivilege> parsePrivileges(Object raw) {
    if (raw instanceof List<?> list && !list.isEmpty()) {
      List<SchemaPrivilege> privileges = new ArrayList<>();
      for (Object value : list) {
        privileges.add(SchemaPrivilege.valueOf(String.valueOf(value).toUpperCase()));
      }
      return privileges;
    }
    return List.of(SchemaPrivilege.USAGE);
  }

  private <T> List<T> convertList(Object raw, Class<T> elementType) {
    if (!(raw instanceof List<?> list) || list.isEmpty()) {
      return List.of();
    }
    return objectMapper.convertValue(
        list, objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
  }

  @SuppressWarnings("unchecked")
  private static List<String> stringList(Object raw) {
    return raw instanceof List<?> list ? (List<String>) list : null;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> mapList(Map<String, Object> payload, String key) {
    if (!(payload.get(key) instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        result.add((Map<String, Object>) map);
      }
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapValue(Map<String, Object> parent, String key) {
    return parent.get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  private static String stringValue(Map<String, Object> map, String key) {
    return map.get(key) instanceof String s ? s : null;
  }

  private static String requireString(SagaCommandMessage command, String key) {
    Object value = command.payload().get(key);
    if (!(value instanceof String s) || s.isBlank()) {
      throw new IllegalArgumentException(
          command.operation() + " payload missing required String field: " + key);
    }
    return s;
  }

  private static String optionalString(SagaCommandMessage command, String key) {
    Object value = command.payload().get(key);
    return value instanceof String s && !s.isBlank() ? s : null;
  }

  private String resolvePassword(String rawPassword) {
    if (rawPassword == null || rawPassword.isBlank()) {
      return null;
    }
    Map<String, Object> wrapper = Map.of("password", rawPassword);
    if (CredentialDecryptor.containsEncryptedValues(wrapper) && stretchedKey.length == 0) {
      throw new IllegalStateException(
          MASTER_KEY_ENV + " is required to decrypt the encrypted role password");
    }
    try {
      Map<String, Object> decrypted =
          CredentialDecryptor.decryptMapValues(wrapper, stretchedKey, ROLE_CREDENTIAL_CONTEXT);
      return (String) decrypted.get("password");
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to decrypt role password", e);
    }
  }

  private static String qualified(String schema, String name) {
    return schema == null || schema.isBlank() ? name : schema + "." + name;
  }

  private static String redact(String text) {
    return text == null ? null : PASSWORD_LITERAL.matcher(text).replaceAll("$1'***'");
  }

  private static String requireProperty(AdapterConfig config, String key) {
    String value = config.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Required postgis saga handler property missing: " + key);
    }
    return value;
  }

  @Override
  public void close() {
    if (connectionProvider != null) {
      connectionProvider.close();
      connectionProvider = null;
    }
    if (stretchedKey != null) {
      Arrays.fill(stretchedKey, (byte) 0);
    }
  }
}
