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
import static de.civitascore.configadapter.nifi.flow.stage.BindingSupport.trimmedNonBlank;
import static de.civitascore.configadapter.nifi.flow.stage.BindingSupport.trimmedString;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL pull source: QueryDatabaseTableRecord reading a table over a source-side DBCP connection
 * pool. Emits records directly (no ConvertRecord) and re-reads on a recurring schedule, so it
 * supports an explicit cron.
 */
public final class SqlSourceStage implements SourceStage {

  /** Friendly name of the source-side DB connection pool (also the sensitive-push match key). */
  private static final String SOURCE_DBCP = "SourceConnectionPool";

  /**
   * A {@code :name} bind placeholder that is NOT part of a PostgreSQL {@code ::} cast — the
   * lookbehind excludes the second colon of {@code ::}, and the trailing letter excludes the first.
   */
  private static final Pattern NAMED_PLACEHOLDER = Pattern.compile("(?<!:):[A-Za-z_]");

  /**
   * A PostgreSQL positional {@code $1} or numeric {@code :1} bind placeholder (not a {@code ::}).
   */
  private static final Pattern POSITIONAL_PLACEHOLDER = Pattern.compile("\\$\\d|(?<!:):\\d");

  /**
   * Query parameters allowed in a tenant SQL {@code dsn}. Deliberately an allowlist, not a
   * blocklist: the pre-deploy JDBC probe (and the deployed flow) connect to this fully
   * tenant-controlled URL, and several PgJDBC parameters take a class name that the driver loads
   * and instantiates ({@code socketFactory}, {@code sslfactory}, {@code
   * authenticationPluginClassName}, {@code sslhostnameverifier}, …) — a code-execution / SSRF
   * surface. Cert-file TLS ({@code sslcert}/{@code sslkey}/{@code sslrootcert}) is also excluded:
   * this adapter cannot provision the files, so {@code sslmode} without client certificates is the
   * supported form.
   */
  private static final Set<String> ALLOWED_DSN_PARAMS =
      Set.of(
          "sslmode",
          "connecttimeout",
          "sockettimeout",
          "logintimeout",
          "tcpkeepalive",
          "applicationname",
          "currentschema",
          "readonly");

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
  public void bind(Datasource source, String sourceNodeKey, PlanContext out)
      throws FatalAdapterException {
    Map<String, Object> original = source.getAdditionalProperties();
    Map<String, Object> decrypted = credentials.decrypt(original);
    rejectUnsupportedSqlFields(decrypted);
    bindSqlQuery(decrypted, out);

    // NOTE: a cron-recurring SQL source re-reads the whole table each run. Duplicate prevention is
    // handled sink-side (PutDatabaseRecord UPSERT keyed on the target's x-core-primaryKey), not in
    // the source.

    // Only PostgreSQL is wired (the bundled /opt/nifi/drivers/postgresql.jar). Reject others.
    String driver = trimmedString(decrypted.get("driver"));
    if (!driver.isEmpty()
        && !"postgres".equalsIgnoreCase(driver)
        && !"postgresql".equalsIgnoreCase(driver)) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, "SQL source driver not supported: " + driver);
    }

    // A blank DSN must be rejected: without it the DBCP pool URL is left unset and the flow falls
    // back to the fragment's hardcoded demo database, silently producing wrong/no data.
    String dsn = trimmedString(decrypted.get("dsn"));
    if (dsn.isEmpty()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, "SQL source requires a non-empty 'dsn'");
    }
    String jdbcUrl = postgresDsnToJdbcUrl(dsn);
    Map<String, String> pool = new LinkedHashMap<>();
    putIfPresent(pool::put, "Database Connection URL", jdbcUrl);
    putIfPresent(pool::put, "Database User", decrypted.get("user"));
    pool.put("Database Driver Class Name", "org.postgresql.Driver");
    pool.put("Database Driver Locations", "/opt/nifi/drivers/postgresql.jar");
    // The Redpanda conn_max_* fields (idle/lifetime/open) are intentionally NOT mapped: the
    // connection pool is platform-managed (NiFi DBCPConnectionPool defaults), not tenant-tunable.
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
   * where}). Table is required; an empty/{@code *} column list means all columns (the processor
   * default); a blank {@code where} is not bound (NiFi would emit an invalid {@code WHERE ()}).
   * Each value is checked for a NiFi Expression Language reference first (see {@link
   * #rejectExpressionLanguage}).
   */
  private static void bindSqlQuery(Map<String, Object> decrypted, PlanContext out)
      throws FatalAdapterException {
    String table = trimmedString(decrypted.get("table"));
    if (table.isEmpty()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, "SQL source requires a non-empty 'table'");
    }
    rejectExpressionLanguage("table", table);
    out.putSourceProperty("Table Name", table);

    List<String> columns = trimmedNonBlank(decrypted.get("columns"));
    for (String column : columns) {
      rejectExpressionLanguage("columns", column);
    }
    if (!columns.isEmpty() && !columns.equals(List.of("*"))) {
      out.putSourceProperty("Columns to Return", String.join(",", columns));
    }

    String where = trimmedString(decrypted.get("where"));
    rejectExpressionLanguage("where", where);
    if (!where.isEmpty()) {
      out.putSourceProperty("Additional WHERE Clause", where);
    }
  }

  private static String passwordOf(Map<String, Object> decrypted) {
    Object password = decrypted.get("password");
    return password == null ? null : String.valueOf(password);
  }

  /**
   * Fails loud on SQL connector fields the NiFi mapping cannot honor — rather than silently
   * dropping them. {@code prefix}/{@code suffix}/{@code init_statement} are Redpanda Connect (the
   * former engine) query concepts with no QueryDatabaseTableRecord equivalent; a {@code where}
   * carrying a bind placeholder ({@code :name} or {@code ?}) would reach NiFi as unbound, invalid
   * SQL; and the {@code dsn} query string is restricted to an allowlist of safe parameters (see
   * {@link #rejectUnknownDsnParams}).
   */
  private static void rejectUnsupportedSqlFields(Map<String, Object> config)
      throws FatalAdapterException {
    for (String field : List.of("prefix", "suffix", "init_statement")) {
      if (!trimmedString(config.get(field)).isEmpty()) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_TEMPLATE_ERROR,
            "SQL source field not supported by the pipeline engine: " + field);
      }
    }
    if (containsBindPlaceholder(trimmedString(config.get("where")))) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "SQL 'where' must be literal SQL without bind placeholders (':name' or '?')");
    }
    rejectUnknownDsnParams(trimmedString(config.get("dsn")));
  }

  /**
   * Rejects a {@code dsn} whose query string carries a parameter outside {@link
   * #ALLOWED_DSN_PARAMS}.
   */
  private static void rejectUnknownDsnParams(String dsn) throws FatalAdapterException {
    int query = dsn.indexOf('?');
    if (query < 0) {
      return;
    }
    for (String pair : dsn.substring(query + 1).split("&")) {
      if (pair.isEmpty()) {
        continue;
      }
      int eq = pair.indexOf('=');
      String name = (eq < 0 ? pair : pair.substring(0, eq)).trim().toLowerCase(Locale.ROOT);
      if (!ALLOWED_DSN_PARAMS.contains(name)) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_TEMPLATE_ERROR,
            "SQL source dsn parameter not allowed: '"
                + name
                + "'; permitted parameters are "
                + ALLOWED_DSN_PARAMS);
      }
    }
  }

  /**
   * Whether a WHERE clause contains a bind placeholder NiFi cannot honor — a named {@code :name}, a
   * positional {@code ?}, or a PostgreSQL positional {@code $1}/numeric {@code :1}. NiFi appends
   * the clause verbatim with no parameter binding, so any of these would be invalid. String
   * literals are stripped first so a {@code ?}/{@code :} inside quotes is ignored (e.g. {@code
   * 'why?'}, {@code '12:00:00'}), and PostgreSQL {@code ::} casts (e.g. {@code created_at::date})
   * are not mistaken for a {@code :name} placeholder.
   *
   * <p>Known conservative limitation: the bare {@code ?} check also rejects jsonb key-exists
   * operators ({@code ?}, {@code ?|}, {@code ?&amp;}) in a literal WHERE. A jsonb-operator filter
   * is therefore not supported here; this is a deliberate false-positive favouring safety.
   */
  private static boolean containsBindPlaceholder(String where) {
    if (where.isEmpty()) {
      return false;
    }
    String sansLiterals = where.replaceAll("'(?:[^']|'')*'", "");
    return sansLiterals.indexOf('?') >= 0
        || NAMED_PLACEHOLDER.matcher(sansLiterals).find()
        || POSITIONAL_PLACEHOLDER.matcher(sansLiterals).find();
  }

  /**
   * Rejects a NiFi Expression Language reference ({@code ${...}}) in a value bound verbatim into a
   * QueryDatabaseTableRecord property. Those properties evaluate EL in the environment scope, so a
   * tenant-supplied {@code table}/{@code columns}/{@code where} carrying {@code
   * ${NIFI_SECURITY_USER_OIDC_CLIENT_SECRET}} would have NiFi's own OIDC client secret expanded and
   * sent in the SQL to the tenant's source DB — an environment-variable exfiltration path. {@code
   * $$} is EL's own literal escape for a {@code $}, so it is not a reference and is allowed
   * through.
   */
  private static void rejectExpressionLanguage(String field, String value)
      throws FatalAdapterException {
    if (value != null && value.replace("$$", "").contains("${")) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "SQL source '" + field + "' must not contain a NiFi expression reference ('${...}')");
    }
  }

  /**
   * Converts a portal SQL {@code dsn} ({@code postgres://[user[:pw]@]host:port/db[?params]}) to the
   * JDBC URL the DBCP pool expects ({@code jdbc:postgresql://host:port/db[?params]}). User/password
   * carried in the DSN userinfo are dropped here — they are bound separately as Database User /
   * sensitive Password. Returns {@code null} for a null/blank DSN (the pool URL is then left
   * unset). Package-private for unit testing.
   */
  static String postgresDsnToJdbcUrl(Object dsn) {
    if (dsn == null) {
      return null;
    }
    String text = String.valueOf(dsn).trim();
    if (text.isEmpty()) {
      return null;
    }
    String withoutScheme = text.replaceFirst("(?i)^(postgres|postgresql)://", "");
    // Strip userinfo only within the authority (before the path/query). An '@' in a query parameter
    // value (e.g. ?options=...) must not be mistaken for the userinfo separator and truncate the
    // URL, so split off the path/query first.
    int authorityEnd = indexOfAny(withoutScheme, '/', '?');
    String authority = authorityEnd < 0 ? withoutScheme : withoutScheme.substring(0, authorityEnd);
    String rest = authorityEnd < 0 ? "" : withoutScheme.substring(authorityEnd);
    int at = authority.lastIndexOf('@');
    if (at >= 0) {
      authority = authority.substring(at + 1);
    }
    return "jdbc:postgresql://" + authority + rest;
  }

  /** The first index of any of {@code chars} in {@code text}, or -1 if none is present. */
  private static int indexOfAny(String text, char... chars) {
    int best = -1;
    for (char c : chars) {
      int index = text.indexOf(c);
      if (index >= 0 && (best < 0 || index < best)) {
        best = index;
      }
    }
    return best;
  }
}
