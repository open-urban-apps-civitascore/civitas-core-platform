/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder.FlowBuildSpec;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest.SinkSpec;
import de.civitascore.configadapter.nifi.graph.GraphParser;
import de.civitascore.configadapter.nifi.graph.PipelineGraph;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import de.civitascore.configadapter.nifi.mapping.GeometryEncoding;
import de.civitascore.configadapter.nifi.mapping.MappingConfig;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns one resolved pipeline (graph + source + sink) into a {@link DeploymentPlan}: it compiles
 * the mapping to RecordPath, resolves the source/sink processor properties, decrypts secrets
 * (collected separately for a post-upload REST push), and delegates the NiFi flow assembly to
 * {@link NifiFlowBuilder} (programmatic composition of building blocks per the graph).
 */
public class FlowDeploymentPlanner {

  private static final String MQTT_PROCESSOR = "ConsumeMQTT";
  private static final String DBCP = "PostGISConnectionPool";

  /** A plain seconds value, optionally with a seconds unit suffix (e.g. {@code 5}, {@code 5s}). */
  private static final Pattern SECONDS =
      Pattern.compile("(\\d+)\\s*(?:s|sec|secs|second|seconds)?", Pattern.CASE_INSENSITIVE);

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

  private final ObjectMapper mapper = new ObjectMapper();
  private final GraphParser graphParser;
  private final MappingConfigParser mappingConfigParser;
  private final RecordPathCompiler recordPathCompiler;
  private final NifiFlowBuilder flowBuilder;
  private final CredentialResolver credentialResolver;
  private final PlatformSinkConfig platformSink;
  private final String frostBaseUrl;
  private final SqlSourceProbe sqlSourceProbe;

  /** Platform-managed sink connection (not a tenant credential). */
  public record PlatformSinkConfig(String postgisUrl, String postgisUser, String postgisPassword) {}

  /**
   * Validates that a SQL source is actually reachable before deploy, so a misconfigured source
   * (wrong host/credentials) fails the saga loudly instead of deploying a flow that silently
   * produces no data. The default {@link #NO_OP} skips the check (used in unit tests with no real
   * DB); production wires a real JDBC probe.
   */
  @FunctionalInterface
  public interface SqlSourceProbe {
    /** A probe that performs no check. */
    SqlSourceProbe NO_OP = (jdbcUrl, user, password) -> {};

    void probe(String jdbcUrl, String user, String password) throws FatalAdapterException;
  }

  /**
   * Creates a planner with no SQL source connectivity probe (the {@link SqlSourceProbe#NO_OP}).
   *
   * @param graphParser the graph parser
   * @param mappingConfigParser the mapping parser
   * @param recordPathCompiler the RecordPath compiler
   * @param flowBuilder the NiFi flow builder
   * @param credentialResolver the credential resolver
   * @param platformSink the platform sink connection (may be null)
   * @param frostBaseUrl the FROST SensorThings base URL for FROST sinks (may be null)
   */
  public FlowDeploymentPlanner(
      GraphParser graphParser,
      MappingConfigParser mappingConfigParser,
      RecordPathCompiler recordPathCompiler,
      NifiFlowBuilder flowBuilder,
      CredentialResolver credentialResolver,
      PlatformSinkConfig platformSink,
      String frostBaseUrl) {
    this(
        graphParser,
        mappingConfigParser,
        recordPathCompiler,
        flowBuilder,
        credentialResolver,
        platformSink,
        frostBaseUrl,
        SqlSourceProbe.NO_OP);
  }

  /**
   * Creates a planner.
   *
   * @param graphParser the graph parser
   * @param mappingConfigParser the mapping parser
   * @param recordPathCompiler the RecordPath compiler
   * @param flowBuilder the NiFi flow builder
   * @param credentialResolver the credential resolver
   * @param platformSink the platform sink connection (may be null)
   * @param frostBaseUrl the FROST SensorThings base URL for FROST sinks (may be null)
   * @param sqlSourceProbe the SQL source connectivity probe
   */
  public FlowDeploymentPlanner(
      GraphParser graphParser,
      MappingConfigParser mappingConfigParser,
      RecordPathCompiler recordPathCompiler,
      NifiFlowBuilder flowBuilder,
      CredentialResolver credentialResolver,
      PlatformSinkConfig platformSink,
      String frostBaseUrl,
      SqlSourceProbe sqlSourceProbe) {
    this.graphParser = graphParser;
    this.mappingConfigParser = mappingConfigParser;
    this.recordPathCompiler = recordPathCompiler;
    this.flowBuilder = flowBuilder;
    this.credentialResolver = credentialResolver;
    this.platformSink = platformSink;
    this.frostBaseUrl = frostBaseUrl;
    this.sqlSourceProbe = sqlSourceProbe;
  }

  /**
   * Plans the deployment of a single pipeline.
   *
   * @param request the resolved pipeline inputs
   * @return the deployment plan
   * @throws FatalAdapterException if the source/sink is unsupported or the mapping is invalid
   */
  public DeploymentPlan plan(PipelineDeploymentRequest request) throws FatalAdapterException {
    PipelineGraph graph;
    try {
      graph = graphParser.parse(request.graphData());
    } catch (IllegalStateException e) {
      // a corrupt graph payload (missing/duplicate node id, edge to an unknown node) — rejected at
      // construction so a malformed graph never deploys silently
      throw new FatalAdapterException(AdapterErrorCode.NIFI_TEMPLATE_ERROR, e, e.getMessage());
    }
    Optional<MappingConfig> mapping = parseMapping(graph);
    Optional<String> sourceCron = parseTriggerCron(graph);
    SinkSpec sink = request.sink();
    // A FROST sink runs the find-or-create on the raw SensorThings envelope and has no
    // record-mapping
    // stage — a configured mapping would be silently ignored. Reject the combination rather than
    // deploy a flow whose transformation never runs.
    if (sink.type() == SinkType.FROST && mapping.isPresent()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "a FROST sink does not support a record mapping; the SensorThings envelope from the source"
              + " is consumed as-is");
    }
    // compile() throws a checked FatalAdapterException (an op may be unrenderable for the sink),
    // which a lambda in Optional.map() cannot propagate — hence the explicit isPresent() branch.
    List<UpdateRecordProperty> mappingProperties =
        mapping.isPresent()
            ? recordPathCompiler.compile(mapping.get(), geometryEncoding(sink.type()))
            : List.of();

    Datasource source = request.source();
    if (source == null) {
      throw template("<no source>");
    }
    SourceType sourceType =
        SourceType.fromRaw(source.getType()).orElseThrow(() -> template(source.getType()));

    // Cron schedules the source processor. ConsumeMQTT is push-based (it self-triggers on broker
    // messages), so a cron there would only throttle the drain, not the data — reject rather than
    // deploy a misleading schedule. Cron belongs on a pull source (SQL).
    if (sourceCron.isPresent() && sourceType == SourceType.MQTT) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "cron scheduling is not supported for push-based MQTT sources");
    }

    // A SQL source ALWAYS re-reads the whole table on a recurring schedule — an explicit cron, or
    // the QueryDatabaseTableRecord fragment's built-in default (every 5 min) when no cron node is
    // present — and it tracks no max-value column. So without a sink primary key the PostGIS write
    // stays INSERT and every run duplicates all rows. Require a key for ANY SQL→PostGIS pipeline
    // (from x-core-primaryKey on the target, or an explicit configuration.primaryKey); this also
    // surfaces the case where a marker exists but the schema is too ambiguous to resolve one.
    if (sourceType == SourceType.SQL
        && sink.type() == SinkType.POSTGIS
        && sink.primaryKeyColumns().isEmpty()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "a SQL source writing to PostGIS requires a primary key on the target data structure (mark"
              + " the identifying attribute with the UML {id} flag) so re-read rows are"
              + " de-duplicated; none was resolved");
    }

    Map<String, String> sourceProperties = new LinkedHashMap<>();
    Map<String, String> sinkProperties = new LinkedHashMap<>();
    Map<String, Map<String, String>> controllerServiceProperties = new LinkedHashMap<>();
    Map<String, Map<String, String>> sensitive = new LinkedHashMap<>();

    bindSource(sourceType, source, sourceProperties, controllerServiceProperties, sensitive);
    bindSink(request, sink, sinkProperties, controllerServiceProperties, sensitive);

    String processGroupName = "pipeline-" + request.pipelineId();
    String snapshot =
        flowBuilder.build(
            new FlowBuildSpec(
                processGroupName,
                sourceType,
                sourceProperties,
                sink.type(),
                sinkProperties,
                mappingProperties,
                controllerServiceProperties,
                sourceCron.orElse(null)));

    return new DeploymentPlan(processGroupName, snapshot, Map.copyOf(sensitive));
  }

  private Optional<MappingConfig> parseMapping(PipelineGraph graph) throws FatalAdapterException {
    Optional<GraphNode> mappingNode;
    try {
      mappingNode = graph.transformNode();
    } catch (IllegalStateException e) {
      // an unbuildable graph topology (unsupported node kind, multiple/disconnected mapping nodes)
      throw new FatalAdapterException(AdapterErrorCode.NIFI_TEMPLATE_ERROR, e, e.getMessage());
    }
    if (mappingNode.isEmpty()) {
      return Optional.empty();
    }
    Object rawConfig = mappingNode.get().data().get("mappingConfig");
    if (rawConfig == null) {
      // A wired mapping node must carry a config; a missing one is a corrupted payload that would
      // otherwise deploy untransformed. (A pipeline with no mapping node at all is fine — handled
      // above by the empty Optional.)
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, "mapping node has no mappingConfig");
    }
    return Optional.of(mappingConfigParser.parse(mapper.valueToTree(rawConfig)));
  }

  private Optional<String> parseTriggerCron(PipelineGraph graph) throws FatalAdapterException {
    Optional<String> cron;
    try {
      cron = graph.triggerCron();
    } catch (IllegalStateException e) {
      // an unbuildable schedule (multiple cron nodes, blank expression)
      throw new FatalAdapterException(AdapterErrorCode.NIFI_TEMPLATE_ERROR, e, e.getMessage());
    }
    if (cron.isPresent() && !isValidNifiCron(cron.get())) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, "invalid NiFi cron expression: " + cron.get());
    }
    return cron;
  }

  /**
   * Whether {@code expression} is a NiFi cron — exactly 6 whitespace-separated fields ({@code sec
   * min hour day-of-month month day-of-week}). NiFi 2.x replaced Quartz with Spring's cron parser,
   * which dropped Quartz's optional 7th "year" field; a year-qualified expression passes a 7-field
   * count here but is then rejected inside NiFi at deploy as an opaque saga failure. NiFi itself
   * validates the field syntax; this only guards the field count so an obviously malformed value
   * fails the plan early rather than the remote NiFi REST call. Mirrors the 6-field check in the
   * editor's {@code validationService.isValidQuartzCron}. Package-private for unit testing.
   */
  static boolean isValidNifiCron(String expression) {
    if (expression == null || expression.isBlank()) {
      return false;
    }
    return expression.trim().split("\\s+").length == 6;
  }

  private void bindSource(
      SourceType sourceType,
      Datasource source,
      Map<String, String> sourceProperties,
      Map<String, Map<String, String>> controllerServiceProperties,
      Map<String, Map<String, String>> sensitive)
      throws FatalAdapterException {
    Map<String, Object> original = source.getAdditionalProperties();
    Map<String, Object> decrypted = credentialResolver.decrypt(original);
    switch (sourceType) {
      case MQTT -> bindMqttSource(decrypted, original, sourceProperties, sensitive);
      case SQL ->
          bindSqlSource(
              decrypted, original, sourceProperties, controllerServiceProperties, sensitive);
    }
  }

  /**
   * Binds a SQL datasource to QueryDatabaseTableRecord + a source-side DBCP connection pool, using
   * the portal's connector field names ({@code table}/{@code columns}/{@code where} for the query,
   * {@code dsn}/{@code user}/{@code password}/{@code driver} for the connection). Table is required
   * — QueryDatabaseTableRecord cannot run without one. Only the PostgreSQL driver is supported so
   * far (the bundled NiFi JDBC driver), so any other {@code driver} is rejected rather than
   * mis-built.
   */
  private void bindSqlSource(
      Map<String, Object> decrypted,
      Map<String, Object> original,
      Map<String, String> sourceProperties,
      Map<String, Map<String, String>> controllerServiceProperties,
      Map<String, Map<String, String>> sensitive)
      throws FatalAdapterException {
    rejectUnsupportedSqlFields(decrypted);
    bindSqlQuery(decrypted, sourceProperties);

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
    putIfPresent(pool, "Database Connection URL", jdbcUrl);
    putIfPresent(pool, "Database User", decrypted.get("user"));
    pool.put("Database Driver Class Name", "org.postgresql.Driver");
    pool.put("Database Driver Locations", "/opt/nifi/drivers/postgresql.jar");
    // The Redpanda conn_max_* fields (idle/lifetime/open) are intentionally NOT mapped: the
    // connection pool is platform-managed (NiFi DBCPConnectionPool defaults), not tenant-tunable.
    controllerServiceProperties.put("SourceConnectionPool", pool);

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
      sensitive
          .computeIfAbsent("SourceConnectionPool", k -> new LinkedHashMap<>())
          .put("Password", String.valueOf(decrypted.get("password")));
    }

    // Fail loud if the source DB is not reachable, rather than deploying a flow that silently
    // produces no data.
    sqlSourceProbe.probe(jdbcUrl, trimmedString(decrypted.get("user")), passwordOf(decrypted));
  }

  /**
   * Binds the QueryDatabaseTableRecord query properties ({@code table}/{@code columns}/{@code
   * where}). Table is required; an empty/{@code *} column list means all columns (the processor
   * default); a blank {@code where} is not bound (NiFi would emit an invalid {@code WHERE ()}).
   * Each value is checked for a NiFi Expression Language reference first (see {@link
   * #rejectExpressionLanguage}).
   */
  private static void bindSqlQuery(
      Map<String, Object> decrypted, Map<String, String> sourceProperties)
      throws FatalAdapterException {
    String table = trimmedString(decrypted.get("table"));
    if (table.isEmpty()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, "SQL source requires a non-empty 'table'");
    }
    rejectExpressionLanguage("table", table);
    sourceProperties.put("Table Name", table);

    List<String> columns = trimmedNonBlank(decrypted.get("columns"));
    for (String column : columns) {
      rejectExpressionLanguage("columns", column);
    }
    if (!columns.isEmpty() && !columns.equals(List.of("*"))) {
      sourceProperties.put("Columns to Return", String.join(",", columns));
    }

    String where = trimmedString(decrypted.get("where"));
    rejectExpressionLanguage("where", where);
    if (!where.isEmpty()) {
      sourceProperties.put("Additional WHERE Clause", where);
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

  /** A value as a trimmed string, or {@code ""} for null. */
  private static String trimmedString(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
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
   * ${SINGLE_USER_CREDENTIALS_PASSWORD}} would have the config-adapter's own NiFi admin password
   * expanded and sent in the SQL to the tenant's source DB — an environment-variable exfiltration
   * path. {@code $$} is EL's own literal escape for a {@code $}, so it is not a reference and is
   * allowed through.
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
   * unset).
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

  /**
   * Binds an MQTT datasource to ConsumeMQTT, using the portal's connector field names ({@code
   * urls}/{@code topics} as lists, {@code user}, {@code client_id}, {@code qos}). Broker URI and
   * Topic Filter are required — without them ConsumeMQTT would fall back to the fragment's demo
   * broker/topic and silently consume from the wrong source, so a missing value fails the deploy.
   */
  private void bindMqttSource(
      Map<String, Object> decrypted,
      Map<String, Object> original,
      Map<String, String> sourceProperties,
      Map<String, Map<String, String>> sensitive)
      throws FatalAdapterException {
    // NiFi's Broker URI accepts a comma-separated list; Topic Filter is a single filter, so one
    // ConsumeMQTT cannot subscribe to multiple distinct topics — reject rather than mis-build.
    List<String> brokers = trimmedNonBlank(decrypted.get("urls"));
    List<String> topics = trimmedNonBlank(decrypted.get("topics"));
    if (brokers.isEmpty() || topics.isEmpty()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "MQTT source requires non-empty 'urls' and 'topics'");
    }
    rejectTlsSource(decrypted.get("tls"), brokers);
    if (topics.size() > 1) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "multiple MQTT topics are not supported (one ConsumeMQTT subscribes to a single topic"
              + " filter): "
              + topics);
    }
    sourceProperties.put("Broker URI", String.join(",", brokers));
    sourceProperties.put("Topic Filter", topics.get(0));
    putIfPresent(sourceProperties, "Username", decrypted.get("user"));
    putIfPresent(sourceProperties, "Client ID", decrypted.get("client_id"));
    putIfPresent(sourceProperties, "Quality of Service", decrypted.get("qos"));
    bindSeconds(sourceProperties, "Connection Timeout", decrypted.get("connect_timeout"));
    bindSeconds(sourceProperties, "Keep Alive", decrypted.get("keepalive"));
    if (isEncrypted(original.get("password"))) {
      sensitive
          .computeIfAbsent(MQTT_PROCESSOR, k -> new LinkedHashMap<>())
          .put("Password", String.valueOf(decrypted.get("password")));
    }
  }

  /**
   * Rejects a TLS MQTT source: NiFi requires an SSL Context Service for a TLS broker, which this
   * adapter does not provision, so deploying would silently fall back to a plaintext connection.
   * Both an explicit {@code tls.enabled=true} and a TLS broker scheme ({@code ssl://}/{@code
   * mqtts://}/{@code wss://}) are rejected.
   */
  private void rejectTlsSource(Object tls, List<String> brokers) throws FatalAdapterException {
    boolean tlsEnabled =
        tls instanceof Map<?, ?> map && "true".equalsIgnoreCase(String.valueOf(map.get("enabled")));
    boolean tlsScheme =
        brokers.stream()
            .map(b -> b.toLowerCase(Locale.ROOT))
            .anyMatch(
                b -> b.startsWith("ssl://") || b.startsWith("mqtts://") || b.startsWith("wss://"));
    if (tlsEnabled || tlsScheme) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "MQTT TLS is not supported yet (needs a NiFi SSL Context Service)");
    }
  }

  /** A scalar or list value as trimmed, non-blank strings (empty for null/all-blank). */
  private static List<String> trimmedNonBlank(Object value) {
    List<String> raw =
        value instanceof List<?> list
            ? list.stream().map(String::valueOf).toList()
            : value == null ? List.of() : List.of(String.valueOf(value));
    return raw.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  /**
   * Binds a portal duration (the connector sends e.g. {@code "5s"}/{@code "30s"}) to a NiFi
   * property as the plain integer seconds it expects. An absent/blank value is skipped; a non-blank
   * value that is not a simple seconds duration is rejected rather than silently dropped (which
   * would leave NiFi's default).
   */
  private void bindSeconds(Map<String, String> properties, String nifiKey, Object value)
      throws FatalAdapterException {
    if (value == null) {
      return;
    }
    String text = String.valueOf(value).trim();
    if (text.isEmpty()) {
      return;
    }
    Matcher matcher = SECONDS.matcher(text);
    if (!matcher.matches()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "MQTT '" + nifiKey + "' is not a valid seconds duration: " + text);
    }
    properties.put(nifiKey, matcher.group(1));
  }

  private void bindSink(
      PipelineDeploymentRequest request,
      SinkSpec sink,
      Map<String, String> sinkProperties,
      Map<String, Map<String, String>> controllerServiceProperties,
      Map<String, Map<String, String>> sensitive)
      throws FatalAdapterException {
    switch (sink.type()) {
      case POSTGIS -> {
        // SinkSpec guarantees a non-blank tableName for POSTGIS (the invalid state is rejected at
        // construction), so PutDatabaseRecord always has a target here.
        sinkProperties.put("Table Name", sink.tableName());
        // With a primary key (the data structure's x-core-primaryKey marker), write UPSERT keyed on
        // it so a cron-recurring source that re-reads rows updates instead of duplicating them.
        // NiFi
        // does not derive the conflict key from the table PK — it must be given via Update Keys.
        // The PutDatabaseRecord fragment quotes identifiers and does NOT translate field names: the
        // PostGIS table is created with quoted (case-preserving) identifiers, so an UPSERT of a
        // camelCase key would otherwise emit an unquoted "ON CONFLICT (stationId)" that PostgreSQL
        // folds to "stationid" and rejects as a missing column.
        if (!sink.primaryKeyColumns().isEmpty()) {
          sinkProperties.put("Statement Type", "UPSERT");
          sinkProperties.put("Update Keys", String.join(",", sink.primaryKeyColumns()));
          // UPSERT needs the PostgreSQL DatabaseAdapter to emit ON CONFLICT; the default "Generic"
          // adapter throws "UPSERT not supported" and routes every record to failure.
          sinkProperties.put("Database Type", "PostgreSQL");
        }
        bindPlatformDbcp(controllerServiceProperties, sensitive);
      }
      case FROST -> bindFrost(request, sinkProperties);
    }
  }

  private void bindFrost(PipelineDeploymentRequest request, Map<String, String> sinkProperties)
      throws FatalAdapterException {
    if (frostBaseUrl == null || frostBaseUrl.isBlank()) {
      // A localhost fallback would deploy a flow that silently posts observations into the void.
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "FROST sink requires the FROST base URL (nifi.frost.url) to be configured");
    }
    // The base URL feeds the find-or-create sub-flow (NifiFlowBuilder), which derives the per-stage
    // URLs (/Things, /Datastreams, /Observations) — not a single POST endpoint.
    sinkProperties.put(NifiFlowBuilder.FROST_BASE_URL, frostBaseUrl);
    // The saga's project id scopes those URLs to the dataset's FROST project; the request record
    // guarantees it is present and numeric for a FROST sink.
    sinkProperties.put(NifiFlowBuilder.FROST_PROJECT_ID, request.frostProjectId());
  }

  private void bindPlatformDbcp(
      Map<String, Map<String, String>> controllerServiceProperties,
      Map<String, Map<String, String>> sensitive)
      throws FatalAdapterException {
    if (platformSink == null
        || platformSink.postgisUrl() == null
        || platformSink.postgisUrl().isBlank()) {
      // A POSTGIS flow without a DB connection URL would deploy but never enable its DBCP service.
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "POSTGIS sink configured but no platform database connection URL is available");
    }
    Map<String, String> dbcp = new LinkedHashMap<>();
    // Intentionally applied to every PostGIS sink (not only geoPoint flows): unspecified is benign
    // for non-geometry columns and is what lets a geoPoint WKT bind into a geometry column.
    putIfPresent(
        dbcp, "Database Connection URL", withStringtypeUnspecified(platformSink.postgisUrl()));
    putIfPresent(dbcp, "Database User", platformSink.postgisUser());
    if (!dbcp.isEmpty()) {
      controllerServiceProperties.put(DBCP, dbcp);
    }
    if (platformSink.postgisPassword() != null) {
      sensitive
          .computeIfAbsent(DBCP, k -> new LinkedHashMap<>())
          .put("Password", platformSink.postgisPassword());
    }
  }

  private static void putIfPresent(Map<String, String> target, String key, Object value) {
    if (value != null) {
      target.put(key, String.valueOf(value));
    }
  }

  private static boolean isEncrypted(Object value) {
    return value instanceof String text && text.startsWith("ENC(") && text.endsWith(")");
  }

  private static FatalAdapterException template(String combination) {
    return new FatalAdapterException(AdapterErrorCode.NIFI_TEMPLATE_ERROR, combination);
  }

  /** The geometry encoding a sink expects: PostGIS parses WKT, FROST expects GeoJSON. */
  private static GeometryEncoding geometryEncoding(SinkType sinkType) {
    return switch (sinkType) {
      case POSTGIS -> GeometryEncoding.WKT;
      case FROST -> GeometryEncoding.GEOJSON;
    };
  }

  /**
   * Ensures the PostGIS JDBC URL carries {@code stringtype=unspecified}, so PutDatabaseRecord's
   * string-bound WKT reaches a {@code geometry} column. With PgJDBC's default ({@code VARCHAR}) the
   * value is sent as {@code varchar}, which has no implicit cast to {@code geometry} →
   * type-mismatch error; {@code unspecified} sends it untyped so the server parses the WKT. Benign
   * for non-geometry columns. Package-private for unit testing.
   */
  static String withStringtypeUnspecified(String url) {
    if (url == null || url.isBlank() || url.contains("stringtype=")) {
      return url;
    }
    return url + (url.contains("?") ? '&' : '?') + "stringtype=unspecified";
  }
}
