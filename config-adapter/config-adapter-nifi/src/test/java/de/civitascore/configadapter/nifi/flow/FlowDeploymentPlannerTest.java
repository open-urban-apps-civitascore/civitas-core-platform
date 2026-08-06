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

import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.graphWithCron;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.graphWithGeoPoint;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.graphWithMapping;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.graphWithoutMapping;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.map;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.mqttSource;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.planner;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.postgisSink;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.postgisSinkWithPk;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.sqlSource;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.sqlSourceBasic;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.sqlSourceWith;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.stretchedKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkSpec;
import de.civitascore.configadapter.nifi.flow.stage.sink.PostgisSinkSpec;
import de.civitascore.configadapter.nifi.flow.stage.sink.SinkSpec;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class FlowDeploymentPlannerTest {

  private static final String SECRET = "sup3r-s3cret-mqtt-pw";

  private final ObjectMapper mapper = new ObjectMapper();

  /** The first processor of the given type in a flow snapshot. */
  private JsonNode processorOfType(String snapshot, String typeSuffix) throws Exception {
    for (JsonNode p : mapper.readTree(snapshot).get("flowContents").get("processors")) {
      if (p.path("type").asText().endsWith(typeSuffix)) {
        return p;
      }
    }
    throw new AssertionError("no processor of type " + typeSuffix);
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
        assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
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
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
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
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
    }
  }

  @Test
  void whereWithExpressionLanguageReferenceIsRejected() throws Exception {
    // a NiFi Expression Language reference in 'where' is evaluated in the environment scope and
    // would
    // exfiltrate an env var (e.g. the NiFi admin password) into the SQL sent to the tenant DB
    Datasource source = sqlSourceBasic();
    source.handleUnknownProperty("where", "1=0 OR x='${SINGLE_USER_CREDENTIALS_PASSWORD}'");
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-sql-el", graphWithMapping(), source, postgisSinkWithPk())));
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
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
    SinkSpec sink = new PostgisSinkSpec("sensor_observations", List.of("id"));
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
    SinkSpec sink = new PostgisSinkSpec("sensor_observations", List.of("tenant", "id"));
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
  void perDatasetSchemaBindsOntoTheWrite() throws Exception {
    // The dedicated per-DataSet schema lands on PutDatabaseRecord's "Schema Name", so the write
    // targets that schema instead of the connection default (public). The template default is now
    // null, so an unset schema would fall back to search_path rather than forcing public.
    Datasource source = mqttSource(null);
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-mqtt-schema",
                      graphWithMapping(),
                      source,
                      new PostgisSinkSpec("sensor_observations", "ds_test", List.of())));
      assertTrue(
          plan.snapshotJson().contains("\"Schema Name\":\"ds_test\""),
          "per-DataSet schema bound onto PutDatabaseRecord");
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
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
    }
  }

  @Test
  void allowlistedNonSslModeDsnParamIsAccepted() throws Exception {
    // guards the allowlist happy-path: a permitted parameter other than sslmode must pass and be
    // preserved, so accidentally dropping one from ALLOWED_DSN_PARAMS is caught, not only rejection
    Datasource source = sqlSourceBasic();
    source.handleUnknownProperty("dsn", "postgres://srcdb:5432/in?applicationname=civitas");
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-sql-appname", graphWithMapping(), source, postgisSinkWithPk()));
      assertTrue(
          plan.snapshotJson().contains("jdbc:postgresql://srcdb:5432/in?applicationname=civitas"),
          "allowlisted parameter preserved in the JDBC URL");
    }
  }

  @Test
  void classLoadingDsnParamIsRejected() throws Exception {
    // a PgJDBC parameter that loads a class (socketFactory) is a code-execution/SSRF surface on the
    // tenant-controlled probe URL — reject anything outside the safe parameter allowlist
    Datasource source = sqlSourceBasic();
    source.handleUnknownProperty("dsn", "postgres://srcdb:5432/in?socketFactory=org.example.Evil");
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-sql-sf", graphWithMapping(), source, postgisSinkWithPk())));
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
    }
  }

  @Test
  void sqlSourceConnectionProbeFailureFailsLoud() throws Exception {
    // #3: an unreachable / mis-credentialed source must fail the plan loudly, not deploy a flow
    // that silently produces no data
    SqlSourceProbe failing =
        (url, user, pw) -> {
          throw new FatalAdapterException(
              AdapterErrorCode.NIFI_TEMPLATE_ERROR, "unreachable: " + url);
        };
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FlowDeploymentPlanner probing = planner(resolver, failing);
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  probing.plan(
                      new PipelineDeploymentRequest(
                          "p-probe", graphWithMapping(), sqlSource(null), postgisSinkWithPk())));
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
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
        assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
      }
    }
  }

  @Test
  void frostMappingOutsideTheStaCatalogIsRejected() throws Exception {
    // FROST accepts a mapping only onto the closed STA target catalog — free target paths
    // (here the PostGIS-shaped graphWithMapping) can never become envelope JSON keys
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
                              new FrostSinkSpec("1", NifiTestFixtures.STA_KEYS))));
      assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
      assertTrue(ex.getMessage().contains("unsupported FROST mapping target path"));
    }
  }

  @Test
  void frostMappingMissingLookupKeysIsRejected() throws Exception {
    // A mapped group must cover its required lookup keys — otherwise the flow deploys and routes
    // every message to the error sink, invisible to the tenant. Fail the plan instead.
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-frost-incomplete",
                              NifiTestFixtures.graphWithIncompleteFrostMapping(),
                              mqttSource(null),
                              new FrostSinkSpec("1", NifiTestFixtures.STA_KEYS))));
      assertEquals(AdapterErrorCode.NIFI_MAPPING_ERROR, ex.getErrorCode());
      assertTrue(
          ex.getMessage().contains("must map the thing match key(s): $.properties.reference"));
    }
  }

  @Test
  void sqlToFrostWithoutMappingIsRejected() throws Exception {
    // Without a mapping the find-or-create consumes the source's envelope as-is; a SQL source emits
    // plain records, so the combination is rejected — with the hint that a mapping unlocks it
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-sql-frost-nomap",
                              graphWithoutMapping(),
                              sqlSource(null),
                              new FrostSinkSpec("1", null))));
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
      assertTrue(
          ex.getMessage()
              .contains(
                  "FROST sink without a record mapping requires a source that emits the"
                      + " SensorThings envelope (MQTT)"));
    }
  }

  @Test
  void mqttToFrostWithMappingIsPlanned() throws Exception {
    // MQTT delivers raw bytes, so the mapped FROST flow needs the record chain (ConvertRecord +
    // UpdateRecord) before the envelope rebuild region.
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      String snapshot =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-mqtt-frost-map",
                      NifiTestFixtures.graphWithFrostMapping(),
                      mqttSource(null),
                      new FrostSinkSpec("7", NifiTestFixtures.STA_KEYS)))
              .snapshotJson();
      processorOfType(snapshot, "ConvertRecord");
      assertEquals(
          "$[*]",
          processorOfType(snapshot, "SplitJson")
              .path("properties")
              .path("JsonPath Expression")
              .asText(),
          "the record-writer array is split before the legs");
      assertTrue(snapshot.contains("/sta_0_name"), "flat mapping fields are bound");
      assertTrue(
          snapshot.contains("${sta_2_reference:escapeJson()}"),
          "the entity bodies reference the captured attributes");
    }
  }

  @Test
  void sqlToFrostWithMappingIsPlanned() throws Exception {
    // A SQL source already emits records (no convert), needs no primary key for FROST (the PK
    // guard is PostGIS-specific), and is accepted because the mapping rebuilds the envelope.
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      String snapshot =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-sql-frost-map",
                      NifiTestFixtures.graphWithFrostMapping(),
                      sqlSource(null),
                      new FrostSinkSpec("7", NifiTestFixtures.STA_KEYS)))
              .snapshotJson();
      processorOfType(snapshot, "QueryDatabaseTableRecord");
      assertFalse(snapshot.contains("ConvertRecord"), "SQL records need no convert step");
      // quotes inside the bound template are JSON-escaped in the snapshot, so match a quote-free
      // placeholder instead of the raw template bytes
      assertTrue(snapshot.contains("${sta_2_reference:escapeJson()}"), "entity bodies are bound");
    }
  }

  @Test
  void frostSinkIsScopedToTheSagaProject() throws Exception {
    // the flow must target the dataset's FROST project: Things under /Projects(n), the Datastream
    // lookup filtered by Thing/Projects/id — root-scoped writes would be invisible through the
    // project-scoped named API
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-frost-scoped",
                      graphWithoutMapping(),
                      mqttSource(null),
                      new FrostSinkSpec("7", null)));
      String snapshot = plan.snapshotJson();
      assertTrue(
          snapshot.contains("/Projects(7)/Things"),
          "Thing leg must be scoped to the saga's project");
      assertTrue(
          snapshot.contains("Thing/Projects/id%20eq%207"),
          "Datastream lookup must be filtered by the saga's project");
      assertFalse(snapshot.contains("secret"), "FROST secret must not enter the snapshot");
      assertEquals(
          "secret",
          plan.sensitivePropsByComponent().get("FrostPublish").get("Request Password"),
          "the Basic Auth password must be patched onto FROST processors after upload");
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
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
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
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
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
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
    }
  }

  @Test
  void mqttSourcePlaintextPasswordIsRejected() throws Exception {
    // same rule as the SQL source: a non-ENC(...) password must never enter the snapshot, and
    // silently dropping it would deploy a flow that connects to the broker unauthenticated
    Datasource source = mqttSource("plaintext-secret");
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-mqtt-plainpw", graphWithMapping(), source, postgisSink())));
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
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
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
    }
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
      // compiled RecordPath mapping is present (copied field + a toDateTime conversion). The
      // date-only op renders the same call wrapped in format(), so a substring match would accept
      // either — the full property value is what tells the two apart.
      assertTrue(snapshot.contains("station_id"));
      assertTrue(snapshot.contains("\"toDate(/ts, 'yyyy-MM-dd')\""));

      // SECURITY INVARIANT: the plaintext secret never appears in the uploaded snapshot
      assertFalse(snapshot.contains(SECRET));
      // ...but is available for the post-upload sensitive-property push
      assertEquals(SECRET, plan.sensitivePropsByComponent().get("ConsumeMQTT").get("Password"));
    }
  }

  @Test
  void sqlSourceProbeReceivesTheDecryptedPassword() throws Exception {
    // The probe must connect with the real password, not the ENC(...) ciphertext: binding the raw
    // token would make the probe fail to authenticate (or, worse, mask a bad credential). Capture
    // what the probe is handed and assert it is the decrypted secret.
    byte[] key = stretchedKey();
    String enc =
        "ENC("
            + CredentialEncryptor.encrypt(
                SECRET, key, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT)
            + ")";
    Datasource source = sqlSourceBasic();
    source.handleUnknownProperty("password", enc);

    String[] probedPassword = {null};
    SqlSourceProbe capturingProbe = (jdbcUrl, user, password) -> probedPassword[0] = password;

    try (CredentialResolver resolver = new CredentialResolver(key)) {
      FlowDeploymentPlanner probing = planner(resolver, capturingProbe);

      probing.plan(
          new PipelineDeploymentRequest(
              "p-sql-probe", graphWithMapping(), source, postgisSinkWithPk()));
    }

    assertEquals(SECRET, probedPassword[0], "probe must receive the decrypted password");
  }

  @Test
  void postgisSinkWithoutPlatformConnectionIsRejected() throws Exception {
    FlowDeploymentPlanner noDbPlanner =
        NifiTestFixtures.planner(
            new CredentialResolver(stretchedKey()),
            SqlSourceProbe.NO_OP,
            null,
            "http://frost:8080/FROST-Server/v1.1");
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                noDbPlanner.plan(
                    new PipelineDeploymentRequest(
                        "p-nodb", graphWithMapping(), mqttSource(null), postgisSink())));
    assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
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
                { "id": "n-src", "type": "dataSource", "data": { "entityId": "src-1" } },
                { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.count": { "op": "toInt", "input": "$.n" } } } } },
                { "id": "n-sink", "type": "geoPersistence", "data": { "entityId": "sink-1" } } ],
              "edges": [
                { "id": "e1", "source": "n-src", "target": "n-map" },
                { "id": "e2", "source": "n-map", "target": "n-sink" } ] }
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
        NifiTestFixtures.planner(
            new CredentialResolver(stretchedKey()),
            SqlSourceProbe.NO_OP,
            new PlatformSinkConfig("jdbc:postgresql://db:5432/civitas", "nifi", "db-secret"),
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
                        new FrostSinkSpec("1", null))));
    assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
  }

  // Note: rejection of a POSTGIS sink without a table name happens at SinkSpec construction
  // (the type rejects the invalid state) — see PipelineDeploymentRequestTest.

  @Test
  void mixedConstAndCopyMappingIsAccepted() throws Exception {
    // A const is rendered as a RecordPath literal, so it shares one UpdateRecord with a copy — the
    // combination is valid, not a "mixed strategy" conflict.
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "n-src", "type": "dataSource", "data": { "entityId": "src-1" } },
                { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                    "fields": {
                      "$.station_id": "$.station_id",
                      "$.unit": { "op": "const", "value": "celsius" }
                    } } } },
                { "id": "n-sink", "type": "geoPersistence", "data": { "entityId": "sink-1" } } ],
              "edges": [
                { "id": "e1", "source": "n-src", "target": "n-map" },
                { "id": "e2", "source": "n-map", "target": "n-sink" } ] }
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
  void unbuildableGraphTopologyIsRejectedAsTemplateError() throws Exception {
    // The graph is the authoritative data-flow description: a second sink node wired into the
    // flow is rejected at plan time (FlowPath), surfaced as a template error with the
    // node-anchored message the editor mirrors.
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
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-real", graph, mqttSource(null), postgisSink())));
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
      assertTrue(
          ex.getMessage().contains("pipeline graph has 2 datasink nodes; exactly one is"),
          ex.getMessage());
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
  void sevenFieldYearQualifiedCronIsRejected() throws Exception {
    // NiFi 2.x replaced Quartz with Spring's cron parser, which dropped the optional 7th (year)
    // field. A year-qualified expression must fail the plan here rather than be accepted and then
    // rejected inside NiFi at deploy as an opaque saga failure.
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-cron7",
                              graphWithCron("0 0 6 * * ? 2026"),
                              sqlSource(null),
                              postgisSinkWithPk())));
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
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
        assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
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
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
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
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
    }
  }

  @Test
  void unwiredMappingsAlongsideAValidPathAreRejected() throws Exception {
    // chained mappings on the path are supported (NifiFlowBuilderTest pins the chained ids); a
    // mapping
    // that exists on the canvas but is not wired into the path would silently not be applied —
    // the derivation rejects it with the unwired-node message
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "n-src", "type": "dataSource", "data": { "entityId": "a1" } },
                { "id": "n-sink", "type": "geoPersistence", "data": { "entityId": "sk-1" } },
                { "id": "n-map1", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.a": "$.b" } } } },
                { "id": "n-map2", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.c": "$.d" } } } } ],
              "edges": [ { "id": "e1", "source": "n-src", "target": "n-sink" } ] }
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
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
      assertTrue(
          ex.getMessage().contains("not wired into the flow"),
          "the error names the unwired-mapping condition");
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
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
    }
  }

  @Test
  void mqttTlsEnabledBuildsSslContextServiceAndNormalizesMqtts() throws Exception {
    Datasource source = mqttSource(null);
    source.handleUnknownProperty("tls", Map.of("enabled", true));
    source.handleUnknownProperty("urls", List.of("MQTTS://Broker.Example:8883"));
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      String snapshot =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest("p-tls", graphWithMapping(), source, postgisSink()))
              .snapshotJson();
      JsonNode flow = mapper.readTree(snapshot).path("flowContents");
      JsonNode mqtt = processorOfType(snapshot, "ConsumeMQTT");
      assertEquals(
          "ssl://Broker.Example:8883", mqtt.path("properties").path("Broker URI").asText());
      String sslContextId = mqtt.path("properties").path("SSL Context Service").asText();
      long matchingServices =
          StreamSupport.stream(flow.path("controllerServices").spliterator(), false)
              .filter(
                  service ->
                      "org.apache.nifi.ssl.StandardSSLContextService"
                          .equals(service.path("type").asText()))
              .peek(service -> assertEquals(sslContextId, service.path("identifier").asText()))
              .count();
      assertEquals(1, matchingServices);
    }
  }

  @Test
  void mqttPlaintextSchemeIsNormalizedToTcp() throws Exception {
    // brokers advertise mqtt://, but NiFi's ConsumeMQTT (Paho) only accepts tcp://; no SSL Context
    // Service is built for a plaintext source
    Datasource source = mqttSource(null);
    source.handleUnknownProperty("urls", List.of("MQTT://Broker.Example:1883?q=1"));
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      String snapshot =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-mqtt-plain", graphWithMapping(), source, postgisSink()))
              .snapshotJson();
      JsonNode mqtt = processorOfType(snapshot, "ConsumeMQTT");
      assertEquals(
          "tcp://Broker.Example:1883?q=1", mqtt.path("properties").path("Broker URI").asText());
      assertTrue(mqtt.path("properties").path("SSL Context Service").isNull());
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
  void validMqttSchemeCombinationsAreAccepted() throws Exception {
    for (String url : List.of("tcp://broker:1883", "ws://broker:8080/mqtt", "mqtt://broker:1883")) {
      Datasource source = mqttSource(null);
      source.handleUnknownProperty("urls", List.of(url));
      try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
        planner(resolver)
            .plan(
                new PipelineDeploymentRequest(
                    "p-plain-" + url.substring(0, 2), graphWithMapping(), source, postgisSink()));
      }
    }
    for (String url : List.of("ssl://broker:8883", "mqtts://broker:8883", "wss://broker/mqtt")) {
      Datasource source = mqttSource(null);
      source.handleUnknownProperty("tls", Map.of("enabled", true));
      source.handleUnknownProperty("urls", List.of(url));
      try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
        planner(resolver)
            .plan(
                new PipelineDeploymentRequest(
                    "p-tls-" + url.substring(0, 2), graphWithMapping(), source, postgisSink()));
      }
    }
  }

  @Test
  void invalidMqttSchemeCombinationsAreRejected() throws Exception {
    for (Map.Entry<String, Boolean> combination :
        Map.of(
                "tcp://broker:1883", true,
                "ws://broker/mqtt", true,
                "mqtt://broker:1883", true,
                "ssl://broker:8883", false,
                "mqtts://broker:8883", false,
                "wss://broker/mqtt", false,
                "https://broker/mqtt", true)
            .entrySet()) {
      Datasource source = mqttSource(null);
      source.handleUnknownProperty("tls", Map.of("enabled", combination.getValue()));
      source.handleUnknownProperty("urls", List.of(combination.getKey()));
      assertPlanRejected(source, "p-invalid-scheme");
    }
  }

  @Test
  void everyMqttBrokerInAListIsValidated() throws Exception {
    Datasource secure = mqttSource(null);
    secure.handleUnknownProperty("tls", Map.of("enabled", true));
    secure.handleUnknownProperty("urls", List.of("ssl://one:8883", "mqtts://two:8883"));
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      String snapshot =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-secure-list", graphWithMapping(), secure, postgisSink()))
              .snapshotJson();
      assertEquals(
          "ssl://one:8883,ssl://two:8883",
          processorOfType(snapshot, "ConsumeMQTT").path("properties").path("Broker URI").asText());
    }

    Datasource plain = mqttSource(null);
    plain.handleUnknownProperty("urls", List.of("tcp://one:1883", "mqtt://two:1883"));
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      String snapshot =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-plain-list", graphWithMapping(), plain, postgisSink()))
              .snapshotJson();
      assertEquals(
          "tcp://one:1883,tcp://two:1883",
          processorOfType(snapshot, "ConsumeMQTT").path("properties").path("Broker URI").asText());
    }

    Datasource mixed = mqttSource(null);
    mixed.handleUnknownProperty("urls", List.of("tcp://one:1883", "ssl://two:8883"));
    assertPlanRejected(mixed, "p-mixed-schemes");
  }

  @Test
  void mqttBrokerListMixingTransportsIsRejected() throws Exception {
    // ConsumeMQTT's customValidate compares every URI's scheme to the first, so a websocket entry
    // alongside a TCP one deploys an INVALID processor that never runs
    Datasource secure = mqttSource(null);
    secure.handleUnknownProperty("tls", Map.of("enabled", true));
    secure.handleUnknownProperty("urls", List.of("ssl://one:8883", "wss://two:8884/mqtt"));
    assertPlanRejected(secure, "p-tls-mixed-transport");

    // the mqtt/mqtts aliases normalize onto tcp/ssl, so the check must run after normalization
    Datasource aliased = mqttSource(null);
    aliased.handleUnknownProperty("urls", List.of("mqtt://one:1883", "ws://two:8080/mqtt"));
    assertPlanRejected(aliased, "p-plain-mixed-transport");
  }

  @Test
  void mqttTcpTransportRejectsAPath() throws Exception {
    // Paho's TCP/SSL network modules require an empty URI path; NiFi does not validate it, so such
    // a broker deploys clean and silently never ingests
    Datasource secure = mqttSource(null);
    secure.handleUnknownProperty("tls", Map.of("enabled", true));
    secure.handleUnknownProperty("urls", List.of("ssl://broker:8883/mqtt"));
    assertPlanRejected(secure, "p-tls-path");

    Datasource plain = mqttSource(null);
    plain.handleUnknownProperty("urls", List.of("mqtt://broker:1883/mqtt"));
    assertPlanRejected(plain, "p-plain-path");
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
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
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
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
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
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
    }
  }

  @Test
  void corruptGraphIsRejectedAsFatalTemplateError() throws Exception {
    // The graph-integrity failure (here an edge to an unknown node) surfaces from parse() as an
    // IllegalStateException; the planner must re-wrap it into a typed FatalAdapterException rather
    // than let an unchecked exception escape the saga.
    Map<String, Object> ghostEdgeGraph =
        map(
            """
            { "nodes": [ { "id": "n-start", "type": "start", "data": {} } ],
              "edges": [ { "id": "e1", "source": "n-start", "target": "n-missing" } ] }
            """);
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-corrupt", ghostEdgeGraph, sqlSourceBasic(), postgisSinkWithPk())));
      assertEquals(AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
    }
  }
}
