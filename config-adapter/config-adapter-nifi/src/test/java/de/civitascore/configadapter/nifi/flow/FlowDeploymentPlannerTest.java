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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest.SinkSpec;
import de.civitascore.configadapter.nifi.graph.GraphParser;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlowDeploymentPlannerTest {

  private static final String MASTER_KEY_HEX =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  private static final String SECRET = "sup3r-s3cret-mqtt-pw";

  private final ObjectMapper mapper = new ObjectMapper();

  private byte[] stretchedKey() throws Exception {
    return CryptoKeyLoader.stretchMasterKey(CryptoKeyLoader.hexStringToBytes(MASTER_KEY_HEX));
  }

  private FlowDeploymentPlanner planner(CredentialResolver resolver) {
    return new FlowDeploymentPlanner(
        new GraphParser(),
        new MappingConfigParser(),
        new RecordPathCompiler(),
        new NifiFlowBuilder(),
        resolver,
        new FlowDeploymentPlanner.PlatformSinkConfig(
            "jdbc:postgresql://db:5432/civitas", "nifi", "db-secret"),
        "http://frost:8080/FROST-Server/v1.1");
  }

  private Map<String, Object> map(String json) throws Exception {
    return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
  }

  private Map<String, Object> graphWithMapping() throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "type": "start", "data": {} },
            { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                "fields": {
                  "$.station_id": "$.station_id",
                  "$.temperature": "$.temperature",
                  "$.observed_at": { "op": "toDate", "input": "$.ts", "pattern": "yyyy-MM-dd" }
                } } } },
            { "id": "n-end", "type": "end", "data": {} }
          ],
          "edges": [
            { "id": "e1", "source": "n-start", "target": "n-map" },
            { "id": "e2", "source": "n-map", "target": "n-end" }
          ]
        }
        """);
  }

  private Map<String, Object> graphWithGeoPoint() throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "type": "start", "data": {} },
            { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                "fields": {
                  "$.location": { "op": "geoPoint", "lon": "$.lon", "lat": "$.lat" }
                } } } },
            { "id": "n-end", "type": "end", "data": {} }
          ],
          "edges": [
            { "id": "e1", "source": "n-start", "target": "n-map" },
            { "id": "e2", "source": "n-map", "target": "n-end" }
          ]
        }
        """);
  }

  /**
   * A graph with no mapping node (source feeds the sink directly) — e.g. a FROST find-or-create.
   */
  private Map<String, Object> graphWithoutMapping() throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "type": "start", "data": {} },
            { "id": "n-end", "type": "end", "data": {} }
          ],
          "edges": [ { "id": "e1", "source": "n-start", "target": "n-end" } ]
        }
        """);
  }

  private Datasource mqttSource(String encryptedPassword) {
    Datasource source = new Datasource();
    source.setId("a1");
    source.setType("MQTT");
    // the portal connector shape: urls/topics are lists, user/client_id/qos scalars
    source.handleUnknownProperty("urls", List.of("tcp://mosquitto:1883"));
    source.handleUnknownProperty("topics", List.of("sensors/+/temp"));
    source.handleUnknownProperty("user", "mqttuser");
    source.handleUnknownProperty("client_id", "civitas-it");
    source.handleUnknownProperty("qos", 1);
    source.handleUnknownProperty("password", encryptedPassword);
    return source;
  }

  private SinkSpec postgisSink() {
    return new SinkSpec(SinkType.POSTGIS, "sensor_observations");
  }

  /** A PostGIS sink with a primary key — required for a cron-scheduled (re-reading) SQL source. */
  private SinkSpec postgisSinkWithPk() {
    return new SinkSpec(SinkType.POSTGIS, "sensor_observations", List.of("id"));
  }

  /** The first processor of the given type in a flow snapshot. */
  private com.fasterxml.jackson.databind.JsonNode processorOfType(
      String snapshot, String typeSuffix) throws Exception {
    for (com.fasterxml.jackson.databind.JsonNode p :
        mapper.readTree(snapshot).get("flowContents").get("processors")) {
      if (p.path("type").asText().endsWith(typeSuffix)) {
        return p;
      }
    }
    throw new AssertionError("no processor of type " + typeSuffix);
  }

  private Datasource sqlSource(String encryptedPassword) {
    Datasource source = new Datasource();
    source.setId("s1");
    source.setType("SQL");
    // the portal SQL connector shape (see datasources/contract)
    source.handleUnknownProperty("driver", "postgres");
    source.handleUnknownProperty("dsn", "postgres://reader@srcdb:5432/in");
    source.handleUnknownProperty("user", "reader");
    source.handleUnknownProperty("table", "events");
    source.handleUnknownProperty("columns", List.of("*"));
    source.handleUnknownProperty("password", encryptedPassword);
    return source;
  }

  /** A SQL datasource with explicit connector fields (any may be null to omit it). */
  private Datasource sqlSourceWith(String driver, String table, Object columns, String where) {
    Datasource source = new Datasource();
    source.setId("s2");
    source.setType("SQL");
    source.handleUnknownProperty("dsn", "postgres://reader@srcdb:5432/in");
    source.handleUnknownProperty("user", "reader");
    if (driver != null) {
      source.handleUnknownProperty("driver", driver);
    }
    if (table != null) {
      source.handleUnknownProperty("table", table);
    }
    if (columns != null) {
      source.handleUnknownProperty("columns", columns);
    }
    if (where != null) {
      source.handleUnknownProperty("where", where);
    }
    return source;
  }

  /** A basic, valid SQL datasource (postgres) for negative/WHERE tests. */
  private Datasource sqlSourceBasic() {
    Datasource source = new Datasource();
    source.setId("s3");
    source.setType("SQL");
    source.handleUnknownProperty("driver", "postgres");
    source.handleUnknownProperty("dsn", "postgres://reader@srcdb:5432/in");
    source.handleUnknownProperty("user", "reader");
    source.handleUnknownProperty("table", "events");
    source.handleUnknownProperty("columns", List.of("*"));
    return source;
  }

  @Test
  void unsupportedRedpandaQueryFieldsAreRejected() throws Exception {
    // #5: prefix/suffix/init_statement are Redpanda Connect concepts the NiFi mapping cannot honor
    // —
    // fail loud rather than silently drop them
    for (String field : List.of("prefix", "suffix", "init_statement")) {
      Datasource source = sqlSourceBasic();
      source.handleUnknownProperty(field, "SELECT 1");
      try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
        FatalAdapterException ex =
            assertThrows(
                FatalAdapterException.class,
                () ->
                    planner(resolver)
                        .plan(
                            new PipelineDeploymentRequest(
                                "p-sql-" + field, graphWithMapping(), source, postgisSinkWithPk())),
                "must reject unsupported field: " + field);
        assertEquals(
            de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
            ex.getErrorCode());
      }
    }
  }

  @Test
  void whereWithCursorPlaceholderIsRejected() throws Exception {
    // #5: the Redpanda cursor idiom (":last_id") is not bound by NiFi and would be invalid SQL
    Datasource source = sqlSourceBasic();
    source.handleUnknownProperty("where", "id > :last_id");
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-sql-cursor", graphWithMapping(), source, postgisSinkWithPk())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void whereWithPositionalPlaceholderIsRejected() throws Exception {
    // #5: a JDBC '?' positional placeholder is not bound by NiFi either — must be rejected too
    Datasource source = sqlSourceBasic();
    source.handleUnknownProperty("where", "col = ?");
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-sql-q", graphWithMapping(), source, postgisSinkWithPk())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void whereWithPostgresCastIsAllowed() throws Exception {
    // a PostgreSQL '::' type cast is valid literal SQL, not a ':name' placeholder — must NOT be
    // rejected
    Datasource source = sqlSourceBasic();
    source.handleUnknownProperty("where", "created_at::date >= '2024-01-01'");
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-sql-cast", graphWithMapping(), source, postgisSinkWithPk()));
      assertTrue(
          plan.snapshotJson().contains("created_at::date >= '2024-01-01'"),
          "cast WHERE is bound, not rejected");
    }
  }

  @Test
  void primaryKeyOnSinkWritesUpsertKeyedOnIt() throws Exception {
    // x-core-primaryKey on the target → PutDatabaseRecord UPSERT keyed on it, so a re-reading cron
    // source updates instead of duplicating rows. NiFi needs the key columns explicitly (Update
    // Keys); it does not derive them from the table's PRIMARY KEY.
    Datasource source = sqlSourceBasic();
    SinkSpec sink = new SinkSpec(SinkType.POSTGIS, "sensor_observations", List.of("id"));
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest("p-sql-upsert", graphWithMapping(), source, sink));
      String snapshot = plan.snapshotJson();
      assertTrue(snapshot.contains("\"Statement Type\":\"UPSERT\""), "UPSERT statement type");
      assertTrue(snapshot.contains("\"Update Keys\":\"id\""), "Update Keys = primary key");
      assertTrue(
          snapshot.contains("\"Database Type\":\"PostgreSQL\""),
          "PostgreSQL adapter required for UPSERT (ON CONFLICT)");
    }
  }

  @Test
  void compositePrimaryKeyWritesAllKeyColumnsToUpdateKeys() throws Exception {
    // a multi-column PK must join ALL key columns into Update Keys, not just the first — otherwise
    // the UPSERT ON CONFLICT target would not match the composite PRIMARY KEY
    Datasource source = sqlSourceBasic();
    SinkSpec sink = new SinkSpec(SinkType.POSTGIS, "sensor_observations", List.of("tenant", "id"));
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      String snapshot =
          planner(resolver)
              .plan(new PipelineDeploymentRequest("p-sql-ckpk", graphWithMapping(), source, sink))
              .snapshotJson();
      assertTrue(snapshot.contains("\"Update Keys\":\"tenant,id\""), "all key columns joined");
    }
  }

  @Test
  void withoutPrimaryKeyTheSinkStaysInsert() throws Exception {
    // no marker → keep the fragment's INSERT default (no UPSERT/Update Keys forced on). Uses an
    // MQTT
    // source: a push source delivers new data per message, so a PK is not required (unlike a
    // re-reading SQL source, which the planner rejects without one).
    Datasource source = mqttSource(null);
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-mqtt-insert", graphWithMapping(), source, postgisSink()));
      assertFalse(
          plan.snapshotJson().contains("\"Statement Type\":\"UPSERT\""), "no UPSERT without a PK");
    }
  }

  @Test
  void whereWithTimeLiteralIsAllowed() throws Exception {
    // a ':' inside a quoted time literal must not be mistaken for a placeholder
    Datasource source = sqlSourceBasic();
    source.handleUnknownProperty("where", "ts >= '2024-01-01 12:00:00'");
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-sql-time", graphWithMapping(), source, postgisSinkWithPk()));
      assertTrue(
          plan.snapshotJson().contains("ts >= '2024-01-01 12:00:00'"),
          "time-literal WHERE is bound, not rejected");
    }
  }

  @Test
  void sslModeInDsnIsPreservedInJdbcUrl() throws Exception {
    // #4: TLS to the source DB is configured via the dsn's sslmode (PgJDBC honors it in the URL);
    // the dsn→jdbc conversion must preserve query parameters
    Datasource source = sqlSourceBasic();
    source.handleUnknownProperty("dsn", "postgres://srcdb:5432/in?sslmode=require");
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-sql-ssl", graphWithMapping(), source, postgisSinkWithPk()));
      assertTrue(
          plan.snapshotJson().contains("jdbc:postgresql://srcdb:5432/in?sslmode=require"),
          "sslmode preserved in the JDBC URL");
    }
  }

  @Test
  void fileBasedClientCertTlsIsRejected() throws Exception {
    // #4: cert-file TLS (sslcert/sslkey/sslrootcert) needs files we cannot provision into NiFi —
    // reject rather than deploy a connection that silently fails
    Datasource source = sqlSourceBasic();
    source.handleUnknownProperty(
        "dsn", "postgres://srcdb:5432/in?sslmode=verify-full&sslcert=/x.pem");
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-sql-mtls", graphWithMapping(), source, postgisSinkWithPk())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void sqlSourceConnectionProbeFailureFailsLoud() throws Exception {
    // #3: an unreachable / mis-credentialed source must fail the plan loudly, not deploy a flow
    // that silently produces no data
    FlowDeploymentPlanner.SqlSourceProbe failing =
        (url, user, pw) -> {
          throw new FatalAdapterException(
              de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
              "unreachable: " + url);
        };
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FlowDeploymentPlanner probing =
          new FlowDeploymentPlanner(
              new GraphParser(),
              new MappingConfigParser(),
              new RecordPathCompiler(),
              new NifiFlowBuilder(),
              resolver,
              new FlowDeploymentPlanner.PlatformSinkConfig(
                  "jdbc:postgresql://db:5432/civitas", "nifi", "db-secret"),
              "http://frost:8080/FROST-Server/v1.1",
              failing);
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  probing.plan(
                      new PipelineDeploymentRequest(
                          "p-probe", graphWithMapping(), sqlSource(null), postgisSinkWithPk())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void sqlColumnsAndWhereClauseAreBound() throws Exception {
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-sql-cols",
                      graphWithMapping(),
                      sqlSourceWith("postgres", "events", List.of("id", "name"), "id > 10"),
                      postgisSinkWithPk()));
      String snapshot = plan.snapshotJson();
      // a specific column list is bound (not "*"), and the WHERE clause flows onto the query
      assertTrue(snapshot.contains("\"Columns to Return\":\"id,name\""), "columns bound");
      assertTrue(snapshot.contains("\"Additional WHERE Clause\":\"id > 10\""), "where bound");
    }
  }

  @Test
  void allColumnsWildcardLeavesColumnsToReturnUnset() throws Exception {
    // columns ["*"] (or empty) means all columns — QueryDatabaseTableRecord's default; the adapter
    // must NOT bind a literal "Columns to Return":"*" which NiFi would treat as a column named '*'.
    // The fragment ships the property as null, so the bound value must stay null/empty.
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      String snapshot =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-sql-allcols",
                      graphWithMapping(),
                      sqlSourceWith("postgres", "events", List.of("*"), null),
                      postgisSinkWithPk()))
              .snapshotJson();
      var cols =
          processorOfType(snapshot, "QueryDatabaseTableRecord")
              .path("properties")
              .path("Columns to Return");
      assertTrue(
          cols.isMissingNode() || cols.isNull() || cols.asText("").isEmpty(),
          "no specific columns bound for '*'");
    }
  }

  @Test
  void positionalBindPlaceholderInWhereIsRejected() throws Exception {
    // Postgres positional ($1) and numeric (:1) bind params can't be honored either (NiFi appends
    // the clause verbatim)
    for (String where : List.of("id = $1", "active AND rank > :1")) {
      try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
        FatalAdapterException ex =
            assertThrows(
                FatalAdapterException.class,
                () ->
                    planner(resolver)
                        .plan(
                            new PipelineDeploymentRequest(
                                "p-sql-posph",
                                graphWithMapping(),
                                sqlSourceWith("postgres", "events", List.of("*"), where),
                                postgisSinkWithPk())));
        assertEquals(
            de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
            ex.getErrorCode());
      }
    }
  }

  @Test
  void frostSinkWithMappingIsRejected() throws Exception {
    // a FROST sink consumes the raw SensorThings envelope; a configured record mapping would be
    // silently ignored by the builder — reject rather than deploy a no-op transformation
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-frost-map",
                              graphWithMapping(),
                              mqttSource(null),
                              new SinkSpec(SinkType.FROST, null))));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void unsupportedSqlDriverIsRejected() throws Exception {
    // only PostgreSQL is wired (the bundled NiFi JDBC driver); any other driver fails the plan
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-sql-drv",
                              graphWithMapping(),
                              sqlSourceWith("mysql", "events", List.of("*"), null),
                              postgisSinkWithPk())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void sqlSourceWithBlankDsnIsRejected() throws Exception {
    // without a DSN the pool URL is unset and the flow would silently fall back to the fragment's
    // demo database — fail loud instead
    Datasource source = sqlSourceBasic();
    source.handleUnknownProperty("dsn", "   ");
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-sql-nodsn", graphWithMapping(), source, postgisSinkWithPk())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void sqlSourcePlaintextPasswordIsRejected() throws Exception {
    // a non-ENC(...) password must be rejected: a plaintext secret must never enter the snapshot,
    // and a probe-only bind would pass while the deployed pool fails to authenticate
    Datasource source = sqlSource("plaintext-secret");
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-sql-plainpw", graphWithMapping(), source, postgisSinkWithPk())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void sqlSourceWithoutTableIsRejected() throws Exception {
    // QueryDatabaseTableRecord cannot run without a table — reject rather than deploy a broken flow
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-sql-notable",
                              graphWithMapping(),
                              sqlSourceWith("postgres", null, List.of("*"), null),
                              postgisSinkWithPk())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  /** A start → cron → mapping → end graph (cron wired into the functional component). */
  private Map<String, Object> graphWithCron(String cronExpression) throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "type": "start", "data": {} },
            { "id": "n-cron", "type": "cron", "data": { "cronExpression": "%s" } },
            { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                "fields": { "$.a": "$.b" } } } },
            { "id": "n-end", "type": "end", "data": {} }
          ],
          "edges": [
            { "id": "e1", "source": "n-start", "target": "n-cron" },
            { "id": "e2", "source": "n-cron", "target": "n-map" },
            { "id": "e3", "source": "n-map", "target": "n-end" }
          ]
        }
        """
            .formatted(cronExpression));
  }

  @Test
  void buildsDeployablePlanWithoutLeakingSecrets() throws Exception {
    byte[] key = stretchedKey();
    String enc =
        "ENC("
            + CredentialEncryptor.encrypt(
                SECRET, key, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT)
            + ")";

    try (CredentialResolver resolver = new CredentialResolver(key)) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "b222", graphWithMapping(), mqttSource(enc), postgisSink()));

      // process-group name is the idempotency key
      assertEquals("pipeline-b222", plan.processGroupName());

      String snapshot = plan.snapshotJson();
      // non-sensitive slots are bound
      assertTrue(snapshot.contains("tcp://mosquitto:1883"));
      assertTrue(snapshot.contains("sensors/+/temp"));
      assertTrue(snapshot.contains("sensor_observations"));
      // compiled RecordPath mapping is present (copied field + a toDate conversion)
      assertTrue(snapshot.contains("station_id"));
      assertTrue(snapshot.contains("toDate(/ts, 'yyyy-MM-dd')"));

      // SECURITY INVARIANT: the plaintext secret never appears in the uploaded snapshot
      assertFalse(snapshot.contains(SECRET));
      // ...but is available for the post-upload sensitive-property push
      assertEquals(SECRET, plan.sensitivePropsByComponent().get("ConsumeMQTT").get("Password"));
    }
  }

  @Test
  void postgisSinkWithoutPlatformConnectionIsRejected() throws Exception {
    FlowDeploymentPlanner noDbPlanner =
        new FlowDeploymentPlanner(
            new GraphParser(),
            new MappingConfigParser(),
            new RecordPathCompiler(),
            new NifiFlowBuilder(),
            new CredentialResolver(stretchedKey()),
            null,
            "http://frost:8080/FROST-Server/v1.1");
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                noDbPlanner.plan(
                    new PipelineDeploymentRequest(
                        "p-nodb", graphWithMapping(), mqttSource(null), postgisSink())));
    assertEquals(
        de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
  }

  @Test
  void typedConversionPassesThroughWhenNoSinkSchema() throws Exception {
    // toInt/toFloat are transparent in the adapter — coercion happens at the sink
    // (PutDatabaseRecord
    // against the DB columns). A FROST sink without a data structure is therefore NOT rejected.
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "n-start", "type": "start", "data": {} },
                { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.count": { "op": "toInt", "input": "$.n" } } } } },
                { "id": "n-end", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "n-start", "target": "n-map" },
                { "id": "e2", "source": "n-map", "target": "n-end" } ] }
            """);
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest("p-typed", graph, mqttSource(null), postgisSink()));
      // the conversion is rendered as a transparent record-path copy, no schema involved
      assertTrue(plan.snapshotJson().contains("/n"));
    }
  }

  @Test
  void frostSinkWithoutBaseUrlIsRejected() throws Exception {
    // A null FROST base URL must fail fast — otherwise the flow would deploy and silently POST
    // observations to a bogus/empty URL.
    FlowDeploymentPlanner noFrostPlanner =
        new FlowDeploymentPlanner(
            new GraphParser(),
            new MappingConfigParser(),
            new RecordPathCompiler(),
            new NifiFlowBuilder(),
            new CredentialResolver(stretchedKey()),
            new FlowDeploymentPlanner.PlatformSinkConfig(
                "jdbc:postgresql://db:5432/civitas", "nifi", "db-secret"),
            null);
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                noFrostPlanner.plan(
                    new PipelineDeploymentRequest(
                        "p-nofrost",
                        graphWithoutMapping(),
                        mqttSource(null),
                        new SinkSpec(SinkType.FROST, null))));
    assertEquals(
        de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
  }

  // Note: rejection of a POSTGIS sink without a table name now happens at SinkSpec construction
  // (the type rejects the invalid state) — see PipelineDeploymentRequestTest.

  @Test
  void mixedConstAndCopyMappingIsAccepted() throws Exception {
    // A const is rendered as a RecordPath literal, so it shares one UpdateRecord with a copy — the
    // combination is valid (previously rejected as a "mixed strategy").
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "n-start", "type": "start", "data": {} },
                { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                    "fields": {
                      "$.station_id": "$.station_id",
                      "$.unit": { "op": "const", "value": "celsius" }
                    } } } },
                { "id": "n-end", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "n-start", "target": "n-map" },
                { "id": "e2", "source": "n-map", "target": "n-end" } ] }
            """);
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest("p-mixed", graph, mqttSource(null), postgisSink()));
      String snapshot = plan.snapshotJson();
      // both fields are bound: the copy as a record-path, the const as a literal-value — across the
      // two strategy-grouped UpdateRecord processors
      assertTrue(snapshot.contains("/station_id"));
      assertTrue(snapshot.contains("celsius"));
    }
  }

  @Test
  void toleratesRealFrontendNodeTypes() throws Exception {
    // the editor emits source/sink nodes (dataSource, frost, geoPersistence) alongside the mapping
    // —
    // the adapter must tolerate them and still build, not reject the graph
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "n-src", "type": "dataSource", "data": {} },
                { "id": "n-geo", "type": "geoPersistence", "data": {} },
                { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.a": "$.b" } } } },
                { "id": "n-frost", "type": "frost", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "n-src", "target": "n-map" },
                { "id": "e2", "source": "n-map", "target": "n-frost" },
                { "id": "e3", "source": "n-frost", "target": "n-geo" } ] }
            """);
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest("p-real", graph, mqttSource(null), postgisSink()));
      assertTrue(plan.snapshotJson().contains("/a")); // the mapping was still found and compiled
    }
  }

  @Test
  void cronSchedulesSqlSourceAndKeepsSecretOut() throws Exception {
    // a cron node drives the SQL source's NiFi schedule; the SQL config binds onto the query
    // processor; the DB password is pushed separately, never in the snapshot
    byte[] key = stretchedKey();
    String enc =
        "ENC("
            + CredentialEncryptor.encrypt(
                SECRET, key, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT)
            + ")";
    try (CredentialResolver resolver = new CredentialResolver(key)) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-sql-cron",
                      graphWithCron("0 0 6 * * ?"),
                      sqlSource(enc),
                      postgisSinkWithPk()));

      String snapshot = plan.snapshotJson();
      assertTrue(snapshot.contains("\"Table Name\":\"events\""), "table bound onto the query");
      // pin the schedule to the SQL source processor (not just "appears somewhere in the snapshot")
      var sourceProc = processorOfType(snapshot, "QueryDatabaseTableRecord");
      assertEquals(
          "CRON_DRIVEN",
          sourceProc.path("schedulingStrategy").asText(),
          "source is cron-scheduled");
      assertEquals(
          "0 0 6 * * ?",
          sourceProc.path("schedulingPeriod").asText(),
          "cron expression bound to the source processor schedule");
      // jdbc URL derived from the dsn (userinfo stripped)
      assertTrue(snapshot.contains("jdbc:postgresql://srcdb:5432/in"), "dsn → jdbc url");

      // SECURITY INVARIANT: the plaintext secret never appears in the snapshot, but is pushed
      assertFalse(snapshot.contains(SECRET));
      assertEquals(
          SECRET, plan.sensitivePropsByComponent().get("SourceConnectionPool").get("Password"));
    }
  }

  @Test
  void sevenFieldQuartzCronWithYearIsAccepted() throws Exception {
    // Quartz allows an optional 7th field (year); a valid 7-field expression must be accepted
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      String snapshot =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-cron7",
                      graphWithCron("0 0 6 * * ? 2026"),
                      sqlSource(null),
                      postgisSinkWithPk()))
              .snapshotJson();
      var sourceProc = processorOfType(snapshot, "QueryDatabaseTableRecord");
      assertEquals("0 0 6 * * ? 2026", sourceProc.path("schedulingPeriod").asText());
    }
  }

  @Test
  void sqlSourceToPostgisWithoutPrimaryKeyIsRejected() throws Exception {
    // any SQL source re-reads the whole table on a recurring schedule (even with no explicit cron,
    // via the fragment's 5-min default); without a target PK the PostGIS write would
    // INSERT-duplicate
    // every run — fail the plan. Both the no-cron and the cron graph must be rejected.
    for (Map<String, Object> graph : List.of(graphWithMapping(), graphWithCron("0 0 6 * * ?"))) {
      try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
        FatalAdapterException ex =
            assertThrows(
                FatalAdapterException.class,
                () ->
                    planner(resolver)
                        .plan(
                            new PipelineDeploymentRequest(
                                "p-sql-nopk", graph, sqlSource(null), postgisSink())));
        assertEquals(
            de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
            ex.getErrorCode());
      }
    }
  }

  @Test
  void invalidCronExpressionIsRejected() throws Exception {
    // a 5-field (non-NiFi/Quartz) expression must fail the plan, not reach the remote NiFi
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-bad-cron",
                              graphWithCron("0 0 * * *"),
                              sqlSource(null),
                              postgisSink())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void cronOnMqttSourceIsRejected() throws Exception {
    // ConsumeMQTT is push-based — a cron schedule there is rejected rather than misleadingly
    // applied
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-mqtt-cron",
                              graphWithCron("0 0 6 * * ?"),
                              mqttSource(null),
                              postgisSink())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void multipleMappingNodesAreRejected() throws Exception {
    // the adapter builds a single transform; two mapping nodes are ambiguous and must fail loudly
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "n-map1", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.a": "$.b" } } } },
                { "id": "n-map2", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.c": "$.d" } } } } ],
              "edges": [] }
            """);
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-2map", graph, mqttSource(null), postgisSink())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void multipleMqttTopicsAreRejected() throws Exception {
    // one ConsumeMQTT subscribes to a single topic filter; a list of distinct topics must fail
    Datasource source = mqttSource(null);
    source.handleUnknownProperty("topics", List.of("a/+", "b/+"));
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-2topic", graphWithMapping(), source, postgisSink())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void mqttTlsEnabledIsRejected() throws Exception {
    // TLS needs a NiFi SSL Context Service the adapter does not provision — reject, don't silently
    // deploy a plaintext connection
    Datasource source = mqttSource(null);
    source.handleUnknownProperty("tls", Map.of("enabled", true));
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-tls", graphWithMapping(), source, postgisSink())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void connectTimeoutAndKeepaliveAreBoundAsSeconds() throws Exception {
    // portal sends durations like "5s"/"30s"; NiFi's Connection Timeout / Keep Alive want plain
    // integer seconds, so they must be normalized and actually set (not silently ignored)
    Datasource source = mqttSource(null);
    source.handleUnknownProperty("connect_timeout", "5s");
    source.handleUnknownProperty("keepalive", "30s");
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      String snapshot =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest("p-to", graphWithMapping(), source, postgisSink()))
              .snapshotJson();
      assertTrue(snapshot.contains("\"Connection Timeout\":\"5\""));
      assertTrue(snapshot.contains("\"Keep Alive\":\"30\""));
    }
  }

  @Test
  void blankTopicElementIsRejected() throws Exception {
    // after trimming, a blank-only topic list is empty → treated as missing, not a blank filter
    Datasource source = mqttSource(null);
    source.handleUnknownProperty("topics", List.of("  "));
    assertPlanRejected(source, "p-blanktopic");
  }

  @Test
  void invalidConnectTimeoutIsRejected() throws Exception {
    // a non-blank but non-parseable duration must fail loudly, not silently fall back to NiFi's
    // default
    Datasource source = mqttSource(null);
    source.handleUnknownProperty("connect_timeout", "soon");
    assertPlanRejected(source, "p-badto");
  }

  @Test
  void tlsBrokerSchemeIsRejected() throws Exception {
    // an ssl:// broker would need a NiFi SSL Context Service we do not provision — reject even when
    // the tls flag is absent
    Datasource source = mqttSource(null);
    source.handleUnknownProperty("urls", List.of("ssl://broker:8883"));
    assertPlanRejected(source, "p-ssl");
  }

  @Test
  void mappingNodeWithoutConfigIsRejected() throws Exception {
    // a wired mapping node with no mappingConfig is a corrupted payload — it must not deploy
    // untransformed
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "n-src", "type": "dataSource", "data": {} },
                { "id": "n-map", "type": "mapping", "data": {} },
                { "id": "n-frost", "type": "frost", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "n-src", "target": "n-map" },
                { "id": "e2", "source": "n-map", "target": "n-frost" } ] }
            """);
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-nocfg", graph, mqttSource(null), postgisSink())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  private void assertPlanRejected(Datasource source, String pipelineId) throws Exception {
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              pipelineId, graphWithMapping(), source, postgisSink())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void geoPointMappingOnPostgisSinkCompilesToWkt() throws Exception {
    // Positive path: geometryEncoding(POSTGIS)=WKT propagates through plan(); the deployed snapshot
    // carries the WKT concat that the geometry column parses on insert.
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      String snapshot =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-pg-geo", graphWithGeoPoint(), mqttSource(null), postgisSink()))
              .snapshotJson();
      assertTrue(snapshot.contains("concat('POINT(', /lon, ' ', /lat, ')')"));
    }
  }

  // Note: a FROST sink with a geoPoint (or any) mapping is now rejected wholesale by
  // frostSinkWithMappingIsRejected — a FROST sink consumes the raw STA envelope and has no mapping
  // stage — so the former geoPoint-specific FROST rejection test is subsumed by it.

  @Test
  void withStringtypeUnspecifiedCoversAllBranches() {
    // no query string → append with '?'
    assertEquals(
        "jdbc:postgresql://db:5432/civitas?stringtype=unspecified",
        FlowDeploymentPlanner.withStringtypeUnspecified("jdbc:postgresql://db:5432/civitas"));
    // existing query string → append with '&'
    assertEquals(
        "jdbc:postgresql://db:5432/civitas?ssl=true&stringtype=unspecified",
        FlowDeploymentPlanner.withStringtypeUnspecified(
            "jdbc:postgresql://db:5432/civitas?ssl=true"));
    // already present → unchanged (idempotent, no double-append)
    String already = "jdbc:postgresql://db:5432/civitas?stringtype=unspecified";
    assertEquals(already, FlowDeploymentPlanner.withStringtypeUnspecified(already));
    // null / blank pass through untouched
    assertNull(FlowDeploymentPlanner.withStringtypeUnspecified(null));
    assertEquals("", FlowDeploymentPlanner.withStringtypeUnspecified(""));
  }

  @Test
  void unsupportedSourceSinkCombinationIsRejected() throws Exception {
    Datasource source = new Datasource();
    source.setId("x");
    source.setType("kafka"); // no curated template

    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p1", graphWithMapping(), source, postgisSink())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }
}
