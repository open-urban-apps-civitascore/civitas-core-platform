/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.source;

import static de.civitascore.configadapter.nifi.flow.stage.BindingSupport.isEncrypted;
import static de.civitascore.configadapter.nifi.flow.stage.BindingSupport.putIfPresent;
import static de.civitascore.configadapter.nifi.flow.stage.BindingSupport.trimmedString;

import de.civitascore.configadapter.connector.ConnectorValueException;
import de.civitascore.configadapter.connector.SqlConnectorValues;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.SourceType;
import de.civitascore.configadapter.nifi.flow.SqlSourceProbe;
import de.civitascore.configadapter.nifi.flow.stage.BuildContext;
import de.civitascore.configadapter.nifi.flow.stage.Fragment;
import de.civitascore.configadapter.nifi.flow.stage.PayloadForm;
import de.civitascore.configadapter.nifi.flow.stage.PlanContext;
import de.civitascore.configadapter.nifi.flow.stage.Processor;
import de.civitascore.configadapter.nifi.flow.stage.SourceStage;
import de.civitascore.configadapter.nifi.flow.stage.StageResult;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL pull source: QueryDatabaseTableRecord reading a table over a source-side DBCP connection
 * pool. Emits records directly (no ConvertRecord) and re-reads on a recurring schedule, so it
 * supports an explicit cron.
 */
public final class SqlSourceStage implements SourceStage {

  /** Friendly name of the source-side DB connection pool (also the sensitive-push match key). */
  private static final String SOURCE_DBCP = "SourceConnectionPool";

  private final CredentialResolver credentials;
  private final SqlSourceProbe sqlSourceProbe;

  public SqlSourceStage(CredentialResolver credentials, SqlSourceProbe sqlSourceProbe) {
    this.credentials = credentials;
    this.sqlSourceProbe = sqlSourceProbe;
  }

  @Override
  public SourceType type() {
    return SourceType.SQL;
  }

  @Override
  public PayloadForm output() {
    return PayloadForm.RECORDS;
  }

  @Override
  public boolean acceptsSchedule() {
    // QueryDatabaseTableRecord is pull-based — a cron on the entry processor is meaningful.
    return true;
  }

  /**
   * Binds a SQL datasource to QueryDatabaseTableRecord + a source-side DBCP connection pool, using
   * the portal's connector field names ({@code table}/{@code columns}/{@code where} for the query,
   * {@code dsn}/{@code user}/{@code password}/{@code driver} for the connection). Table is required
   * — QueryDatabaseTableRecord cannot run without one. Only the PostgreSQL driver is supported so
   * far (the bundled NiFi JDBC driver), so any other {@code driver} is rejected rather than
   * mis-built.
   */
  @Override
  public void bind(
      Datasource source,
      Map<String, Map<String, Object>> artifacts,
      GraphNode node,
      String sourceNodeKey,
      PlanContext out)
      throws FatalAdapterException {
    Map<String, Object> original = source.getAdditionalProperties();
    Map<String, Object> decrypted = credentials.decrypt(original);
    SqlConnectorValues values = readValues(decrypted);
    bindSqlQuery(values, out);

    // NOTE: a cron-recurring SQL source re-reads the whole table each run. Duplicate prevention is
    // handled sink-side (PutDatabaseRecord UPSERT keyed on the target's x-core-primaryKey), not in
    // the source.

    // The values allow only PostgreSQL, the bundled /opt/nifi/drivers/postgresql.jar driver.
    // They also require a DSN, without which the DBCP pool stays on the fragment's demo database.
    String jdbcUrl = values.jdbcUrl();
    Map<String, String> pool = new LinkedHashMap<>();
    putIfPresent(pool::put, "Database Connection URL", jdbcUrl);
    putIfPresent(pool::put, "Database User", decrypted.get("user"));
    pool.put("Database Driver Class Name", "org.postgresql.Driver");
    pool.put("Database Driver Locations", "/opt/nifi/drivers/postgresql.jar");
    pool.forEach((key, value) -> out.putControllerServiceProperty(SOURCE_DBCP, key, value));

    // A password, if present, must be encrypted: a plaintext secret must never be written into the
    // (logged, non-secret) flow snapshot, and binding it only to the probe but not to the deployed
    // pool would make the probe pass while the running flow fails to authenticate.
    Object password = original.get("password");
    if (password != null && !String.valueOf(password).isBlank() && !isEncrypted(password)) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "SQL source password must be encrypted (plaintext secrets are not accepted)");
    }
    if (isEncrypted(password)) {
      out.putSensitive(SOURCE_DBCP, "Password", String.valueOf(decrypted.get("password")));
    }

    // Fail loud if the source DB is not reachable, rather than deploying a flow that silently
    // produces no data.
    sqlSourceProbe.probe(jdbcUrl, trimmedString(decrypted.get("user")), passwordOf(decrypted));
  }

  @Override
  public void registerControllerServices(BuildContext ctx) throws FatalAdapterException {
    // SQL pull source reads records over its own DB connection pool.
    ctx.addControllerService(Fragment.DBCP_CONNECTION_POOL, SOURCE_DBCP);
  }

  @Override
  public StageResult build(BuildContext ctx) throws FatalAdapterException {
    Processor source = ctx.loadProcessor(Fragment.QUERY_DATABASE_TABLE_RECORD, "success");
    BuildContext.applySchedule(source, ctx.spec().sourceCron());
    ctx.spec().sourceProperties().forEach((key, value) -> BuildContext.setProp(source, key, value));
    return new StageResult(List.of(source), List.of());
  }

  /**
   * Binds the QueryDatabaseTableRecord query properties ({@code table}/{@code columns}/{@code
   * where}). An empty/{@code *} column list means all columns (the processor default); a blank
   * {@code where} is not bound (NiFi would emit an invalid {@code WHERE ()}).
   */
  private static void bindSqlQuery(SqlConnectorValues values, PlanContext out) {
    out.putSourceProperty("Table Name", values.table());
    if (!values.allColumns()) {
      out.putSourceProperty("Columns to Return", String.join(",", values.columns()));
    }
    if (!values.where().isEmpty()) {
      out.putSourceProperty("Additional WHERE Clause", values.where());
    }
  }

  private static SqlConnectorValues readValues(Map<String, Object> decrypted)
      throws FatalAdapterException {
    try {
      return SqlConnectorValues.read(decrypted);
    } catch (ConnectorValueException e) {
      throw new FatalAdapterException(AdapterErrorCode.NIFI_TEMPLATE_ERROR, e, e.getMessage());
    }
  }

  private static String passwordOf(Map<String, Object> decrypted) {
    Object password = decrypted.get("password");
    return password == null ? null : String.valueOf(password);
  }
}
