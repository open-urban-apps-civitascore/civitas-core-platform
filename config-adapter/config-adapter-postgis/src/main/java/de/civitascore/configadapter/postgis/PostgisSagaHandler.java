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
import de.civitascore.configadapter.model.dataset.DataStructureSchema;
import de.civitascore.configadapter.model.dataset.WorkspaceNames;
import de.civitascore.configadapter.model.postgis.ColumnConfig;
import de.civitascore.configadapter.model.postgis.DbRoleConfig;
import de.civitascore.configadapter.model.postgis.GeometryColumnConfig;
import de.civitascore.configadapter.model.postgis.IndexConfig;
import de.civitascore.configadapter.model.postgis.PostgisConfigValue;
import de.civitascore.configadapter.model.postgis.SchemaConfig;
import de.civitascore.configadapter.model.postgis.SchemaGrant;
import de.civitascore.configadapter.model.postgis.SchemaPrivilege;
import de.civitascore.configadapter.model.postgis.TableConfig;
import de.civitascore.configadapter.postgis.ddl.DataStructureTableMapper;
import de.civitascore.configadapter.postgis.ddl.GrantReconciler;
import de.civitascore.configadapter.postgis.dialect.SqlDialect;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saga command handler for PostGIS, mirroring the FROST / APISIX / NiFi handlers but executing DDL
 * over JDBC instead of HTTP. Discovered via {@link java.util.ServiceLoader} and registered in the
 * saga orchestrator's handler registry.
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
 * datasinks}, the {@code configuration} carries the table definition:
 *
 * <pre>{@code
 * { "type": "POSTGIS",
 *   "configuration": {
 *     "schema": "ds_42",                 // optional; else derived from the trigger's datasetId
 *     "owner": "ds_42_admin",            // optional schema owner
 *     "tableName": "sensor_readings",    // required; the table GeoServer reads
 *     "columns": [ {name,type,...} ],    // optional explicit override (see below)
 *     "geometryColumns": [ {name,geometryType,srid,...} ],
 *     "primaryKey": ["id"],              // optional override (non-empty); else derived from schema
 *     "indexes": [ {...} ],
 *     "readRole": { "name":"ds_42_geo", "canLogin":true,
 *                   "password":"ENC(...)", "privileges":["USAGE"] } },  // optional GeoServer role
 *   "dataStructure": { ... } }           // resolved JSON Schema from Model Atlas
 * }</pre>
 *
 * <p>Columns come from explicit {@code configuration.columns} when present, otherwise derived from
 * {@code dataStructure} via {@link DataStructureTableMapper}; at least one column or geometry
 * column must result for provisioning (deprovisioning needs only the identifiers). The primary key
 * comes from explicit {@code configuration.primaryKey} when present, otherwise from the {@code
 * x-core-primaryKey} markers in {@code dataStructure}.
 *
 * <p>The per-DataSet schema name is taken from {@code configuration.schema} when present, otherwise
 * derived from the trigger's {@code datasetId} via {@link
 * de.civitascore.configadapter.model.dataset.WorkspaceNames#fromDatasetId} — the same rule the
 * GeoServer workspace uses, so the table lands in the schema GeoServer reads from.
 *
 * <p>{@code PROVISION_SINK} creates the schema, table, and read role + grants for each sink in one
 * transaction; {@code DEPROVISION_SINK} drops the table and role, then drops the now-empty
 * per-DataSet schema ({@code RESTRICT}, never {@code public}). Both re-derive their targets from
 * {@code datasinks} + {@code datasetId}, so the compensation/forward-delete paths are symmetric.
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
  private static final String COMPENSATE_TYPE = "COMPENSATE_STEP";
  private static final String DATASINK_TYPE_POSTGIS = "POSTGIS";

  /** The shared schema that predates per-DataSet schemas; never dropped on deprovision. */
  private static final String SHARED_PUBLIC_SCHEMA = "public";

  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final SqlDdlSupport ddl = new SqlDdlSupport();

  /** No-arg constructor for ServiceLoader discovery. Call {@link #initialize} before use. */
  public PostgisSagaHandler() {}

  @Override
  public String adapter() {
    return ADAPTER_NAME;
  }

  @Override
  public void initialize(AdapterConfig config) {
    String jdbcUrl = ddl.initialize(config);
    logger.info(
        "PostgisSagaHandler initialized for: {}",
        Encode.forJava(SqlDdlSupport.sanitizeJdbcUrl(jdbcUrl)));
  }

  /** Test seam — inject a pre-configured provider before {@link #initialize(AdapterConfig)}. */
  void setConnectionProvider(ConnectionProvider connectionProvider) {
    ddl.setConnectionProvider(connectionProvider);
  }

  /** Test seam — inject an alternate dialect. */
  void setDialect(SqlDialect dialect) {
    ddl.setDialect(dialect);
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
      String error = command.operation() + " failed: " + SqlDdlSupport.redact(e.getMessage());
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
    ddl.runDdl(dialect().createTable(table), true, false);

    Map<String, Object> identifiers = new LinkedHashMap<>();
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
    ddl.runDdl(dialect().createSchema(schema), true, false);

    Map<String, Object> identifiers = Map.of("schema", schema.getName());
    logger.info(
        "PostGIS schema {} created, saga={}",
        Encode.forJava(schema.getName()),
        Encode.forJava(command.sagaId()));
    return SagaCommandResult.success(command.sagaId(), command.stepId(), identifiers, identifiers);
  }

  private SagaCommandResult createRole(SagaCommandMessage command) throws SQLException {
    DbRoleConfig role = convert(command, "roleConfig", DbRoleConfig.class);
    String password = ddl.resolvePassword(role.getPassword());

    List<String> statements = new ArrayList<>(dialect().createRole(role, password));
    appendGrantStatements(statements, role.getName(), role.getGrants());
    ddl.runDdl(statements, true, false);

    Map<String, Object> identifiers = Map.of("role", role.getName());
    logger.info(
        "PostGIS role {} created, saga={}",
        Encode.forJava(role.getName()),
        Encode.forJava(command.sagaId()));
    return SagaCommandResult.success(command.sagaId(), command.stepId(), identifiers, identifiers);
  }

  // ─── Dataset-saga sink provisioning ─────────────────────────────────────────

  private SagaCommandResult provisionSink(SagaCommandMessage command) throws SQLException {
    List<SinkSpec> sinks = parseSinks(command, true);
    List<String> statements = new ArrayList<>();
    List<Map<String, Object>> provisioned = new ArrayList<>();
    for (SinkSpec sink : sinks) {
      // createTable already emits CREATE SCHEMA for the table's schema.
      statements.addAll(dialect().createTable(sink.table()));
      if (sink.role() != null) {
        String password = ddl.resolvePassword(sink.role().getPassword());
        statements.addAll(dialect().createRole(sink.role(), password));
        appendGrantStatements(statements, sink.role().getName(), sink.role().getGrants());
        statements.add(
            dialect()
                .grantSelectOnTable(
                    sink.role().getName(), sink.table().getSchema(), sink.table().getName()));
      }
      // After role creation: ALTER SCHEMA … OWNER TO requires the owner role to already exist.
      if (sink.schema() != null) {
        statements.addAll(dialect().alterSchemaOwner(sink.schema()));
      }
      provisioned.add(sink.identifiers());
    }
    ddl.runDdl(statements, true, false);

    logger.info(
        "PostGIS sink(s) provisioned: {}, saga={}",
        Encode.forJava(String.valueOf(provisioned)),
        Encode.forJava(command.sagaId()));
    Map<String, Object> data = Map.of("provisionedSinks", provisioned);
    return SagaCommandResult.success(command.sagaId(), command.stepId(), data, data);
  }

  private SagaCommandResult deprovisionSink(SagaCommandMessage command, boolean compensation)
      throws SQLException {
    List<SinkSpec> sinks = parseSinks(command, false);
    List<String> statements = new ArrayList<>();
    Set<String> schemasToDrop = new LinkedHashSet<>();
    for (SinkSpec sink : sinks) {
      if (sink.role() != null) {
        statements.addAll(dialect().dropRole(sink.role()));
      }
      statements.addAll(dialect().dropTable(sink.table().getSchema(), sink.table().getName()));
      // Collect the per-DataSet schema to drop after its tables (never public or a blank one).
      SchemaConfig schema = sink.schema();
      if (schema != null
          && schema.getName() != null
          && !schema.getName().isBlank()
          && !SHARED_PUBLIC_SCHEMA.equalsIgnoreCase(schema.getName())) {
        schemasToDrop.add(schema.getName());
      }
    }
    // Drop each per-DataSet schema once, after its tables: a DataSet's sinks share one schema, so a
    // per-sink drop would hit sibling tables. RESTRICT removes it only if empty — never data.
    for (String schemaName : schemasToDrop) {
      SchemaConfig schema = new SchemaConfig();
      schema.setName(schemaName);
      statements.addAll(dialect().dropSchema(schema));
    }
    ddl.runDdl(statements, false, true);

    logger.info("PostGIS sink(s) deprovisioned, saga={}", Encode.forJava(command.sagaId()));
    return done(command, compensation);
  }

  // ─── Drop operations (also serve as compensations) ──────────────────────────

  private SagaCommandResult dropTable(SagaCommandMessage command, boolean compensation)
      throws SQLException {
    String schema = optionalString(command, "schema");
    String table = requireString(command, "table");
    ddl.runDdl(dialect().dropTable(schema, table), false, true);
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
    schema.setCascade(booleanValue(command.payload(), "cascade"));
    ddl.runDdl(dialect().dropSchema(schema), false, true);
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
    ddl.runDdl(dialect().dropRole(role), false, true);
    logger.info(
        "PostGIS role {} dropped, saga={}",
        Encode.forJava(role.getName()),
        Encode.forJava(command.sagaId()));
    return done(command, compensation);
  }

  /** Renders the desired grants for a freshly created role (no current state to reconcile). */
  private void appendGrantStatements(
      List<String> statements, String roleName, List<SchemaGrant> grants) {
    ddl.appendGrantStatements(statements, roleName, GrantReconciler.reconcile(grants, Map.of()));
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private SqlDialect dialect() {
    return ddl.dialect();
  }

  private SagaCommandResult done(SagaCommandMessage command, boolean compensation) {
    return compensation
        ? SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId())
        : SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());
  }

  private SagaCommandResult unknownOperation(SagaCommandMessage command, boolean compensation) {
    String error = "Unknown postgis operation: " + command.operation();
    logger.warn("Unknown postgis operation: {}", Encode.forJava(command.operation()));
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
   * Parses the trigger's {@code datasinks} into per-sink specs. See the class javadoc for the
   * {@code POSTGIS} sink {@code configuration} shape. Used by {@code PROVISION_SINK} and {@code
   * DEPROVISION_SINK} so the forward and rollback paths resolve the same identifiers; only the
   * provisioning path needs the column definitions ({@code requireColumns}) — dropping a table
   * requires just its name, so a delete trigger may omit columns and {@code dataStructure}.
   */
  private List<SinkSpec> parseSinks(SagaCommandMessage command, boolean requireColumns) {
    List<SinkSpec> specs = new ArrayList<>();
    for (Map<String, Object> sink : mapList(command.payload(), "datasinks")) {
      if (!DATASINK_TYPE_POSTGIS.equals(sink.get("type"))) {
        continue;
      }
      Map<String, Object> config = mapValue(sink, "configuration");
      String tableName = stringValue(config, "tableName");
      if (tableName == null || tableName.isBlank()) {
        throw new IllegalArgumentException("POSTGIS data sink is missing configuration.tableName");
      }
      String schemaName = stringValue(config, "schema");
      if (schemaName == null || schemaName.isBlank()) {
        // Derive the per-DataSet schema from the datasetId (same rule as the GeoServer workspace),
        // so the table lands in its own schema — the one GeoServer reads — not public.
        schemaName = datasetSchemaName(command);
      }

      SchemaConfig schema = null;
      if (schemaName != null && !schemaName.isBlank()) {
        schema = new SchemaConfig();
        schema.setName(schemaName);
        schema.setOwner(stringValue(config, "owner"));
      }

      List<GeometryColumnConfig> geometryColumns =
          convertList(config.get("geometryColumns"), GeometryColumnConfig.class);
      List<ColumnConfig> columns = convertList(config.get("columns"), ColumnConfig.class);
      @SuppressWarnings("unchecked")
      Map<String, Object> dataStructure =
          sink.get("dataStructure") instanceof Map<?, ?> model ? (Map<String, Object>) model : null;
      // The primary key only matters when the table is created (PROVISION). For DEPROVISION
      // (requireColumns=false) columns are intentionally not derived, so deriving/validating a PK
      // there would wrongly fail — the table is being dropped, not built.
      if (requireColumns && columns.isEmpty() && dataStructure != null) {
        Set<String> geometryNames = new HashSet<>();
        for (GeometryColumnConfig geometry : geometryColumns) {
          geometryNames.add(geometry.name());
        }
        DataStructureTableMapper.TableColumns derived =
            DataStructureTableMapper.deriveColumns(dataStructure, geometryNames);
        columns = derived.columns();
        if (!derived.geometryColumns().isEmpty()) {
          geometryColumns = new ArrayList<>(geometryColumns);
          geometryColumns.addAll(derived.geometryColumns());
        }
      }

      // Explicit configuration wins; otherwise the schema's x-core-primaryKey marker via the shared
      // resolver — the same "explicit wins, else marker" rule the NiFi adapter uses, so the table
      // PRIMARY KEY and the NiFi UPSERT keys cannot diverge.
      List<String> primaryKey =
          requireColumns
              ? DataStructureSchema.resolvePrimaryKey(config.get("primaryKey"), dataStructure)
              : List.of();
      // The primary key must reference actual table columns; otherwise the emitted PRIMARY KEY(...)
      // would name a missing column and provisioning fails with broken DDL. This can happen when
      // columns are configured explicitly but a primary-key column (marked or explicit) is omitted
      // —
      // including a partial composite key, which would also silently tighten the table's
      // uniqueness.
      if (!primaryKey.isEmpty()) {
        Set<String> columnNames = new HashSet<>();
        columns.forEach(column -> columnNames.add(column.name()));
        geometryColumns.forEach(geometry -> columnNames.add(geometry.name()));
        List<String> missingKeyColumns =
            primaryKey.stream().filter(key -> !columnNames.contains(key)).toList();
        if (!missingKeyColumns.isEmpty()) {
          throw new IllegalArgumentException(
              "POSTGIS data sink '"
                  + tableName
                  + "' primary key references column(s) not present in the table: "
                  + missingKeyColumns);
        }
      }

      TableConfig table = new TableConfig();
      table.setSchema(schemaName);
      table.setName(tableName);
      table.setColumns(columns);
      table.setGeometryColumns(geometryColumns);
      table.setPrimaryKey(primaryKey);
      table.setIndexes(convertList(config.get("indexes"), IndexConfig.class));
      if (requireColumns && table.getColumns().isEmpty() && table.getGeometryColumns().isEmpty()) {
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
    role.setCanLogin(booleanValue(roleConfig, "canLogin"));
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
  private static List<Map<String, Object>> mapList(Map<String, Object> payload, String key) {
    Object raw = payload.get(key);
    if (raw == null) {
      return List.of();
    }
    if (!(raw instanceof List<?> list)) {
      throw new IllegalArgumentException(key + " must be a list");
    }
    List<Map<String, Object>> result = new ArrayList<>(list.size());
    for (Object item : list) {
      // Reject a non-map entry rather than silently dropping it: a malformed entry must fail the
      // whole command, not yield a partial provision the saga still reports as success.
      if (!(item instanceof Map<?, ?> map)) {
        throw new IllegalArgumentException(key + " entries must be objects");
      }
      result.add((Map<String, Object>) map);
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

  /**
   * The dedicated per-DataSet schema, derived from the trigger's {@code datasetId} via the shared
   * {@link WorkspaceNames#fromDatasetId} rule — the same derivation the GeoServer workspace uses,
   * so the sink table lands in the schema GeoServer reads from. Returns {@code null} when the
   * payload carries no {@code datasetId} (e.g. a non-dataset caller), leaving the table
   * unqualified.
   */
  private static String datasetSchemaName(SagaCommandMessage command) {
    return command.payload().get("datasetId") instanceof String datasetId && !datasetId.isBlank()
        ? WorkspaceNames.fromDatasetId(datasetId)
        : null;
  }

  /** A payload boolean; absent or non-{@code Boolean} → false. */
  private static boolean booleanValue(Map<String, Object> map, String key) {
    return map.get(key) instanceof Boolean b && b;
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

  private static String qualified(String schema, String name) {
    return schema == null || schema.isBlank() ? name : schema + "." + name;
  }

  @Override
  public void close() {
    ddl.close();
  }
}
