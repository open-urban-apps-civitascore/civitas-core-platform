/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.sink;

import static de.civitascore.configadapter.nifi.flow.stage.BindingSupport.putIfPresent;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.DataStructureSchema;
import de.civitascore.configadapter.model.dataset.WorkspaceNames;
import de.civitascore.configadapter.nifi.flow.PlatformSinkConfig;
import de.civitascore.configadapter.nifi.flow.SinkResolutionContext;
import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.flow.stage.BuildContext;
import de.civitascore.configadapter.nifi.flow.stage.Fragment;
import de.civitascore.configadapter.nifi.flow.stage.MappingSupport;
import de.civitascore.configadapter.nifi.flow.stage.PayloadForm;
import de.civitascore.configadapter.nifi.flow.stage.PlanContext;
import de.civitascore.configadapter.nifi.flow.stage.Processor;
import de.civitascore.configadapter.nifi.flow.stage.SinkStage;
import de.civitascore.configadapter.nifi.mapping.GeometryEncoding;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PostGIS sink: a single terminal PutDatabaseRecord writing over the platform-managed DB connection
 * pool. Consumes records, so convert and record mapping run in front of it; geometry values arrive
 * as WKT the server parses on insert.
 */
public final class PostgisSinkStage implements SinkStage<PostgisSinkSpec> {

  /** Friendly name the REST client matches for the post-upload sensitive-property push. */
  private static final String DBCP = "PostGISConnectionPool";

  /** PutDatabaseRecord: success stays terminated (the record landed). */
  private static final List<String> FAILURE_RELATIONSHIPS = List.of("failure", "retry");

  private final PlatformSinkConfig platformSink;

  public PostgisSinkStage(PlatformSinkConfig platformSink) {
    this.platformSink = platformSink;
  }

  @Override
  public SinkType type() {
    return SinkType.POSTGIS;
  }

  @Override
  public Set<PayloadForm> acceptedInputs(boolean mappedUpstream) {
    return Set.of(PayloadForm.RECORDS);
  }

  @Override
  public GeometryEncoding geometryEncoding() {
    return GeometryEncoding.WKT;
  }

  @Override
  public MappingSupport mappingSupport() {
    return MappingSupport.RECORD_PATH;
  }

  @Override
  public Class<PostgisSinkSpec> specType() {
    return PostgisSinkSpec.class;
  }

  @Override
  @SuppressWarnings("unchecked")
  public PostgisSinkSpec parseSpec(Map<String, Object> datasink, SinkResolutionContext ctx)
      throws FatalAdapterException {
    String tableName = null;
    if (datasink.get("configuration") instanceof Map<?, ?> config) {
      tableName = asString(((Map<String, Object>) config).get("tableName"));
    }
    // The write targets the per-DataSet schema, derived from the trigger's datasetId (same
    // WorkspaceNames rule PostGIS and GeoServer use) — the schema the table was created in, not the
    // connection search_path. Null only for a non-dataset caller, then the write resolves via
    // search_path.
    String datasetId = ctx.datasetId();
    String schemaName =
        datasetId == null || datasetId.isBlank() ? null : WorkspaceNames.fromDatasetId(datasetId);
    try {
      return new PostgisSinkSpec(tableName, schemaName, resolvePrimaryKey(datasink));
    } catch (IllegalArgumentException e) {
      // e.g. a missing tableName; keep the raw detail internal and publish only the safe external
      // message for the error code.
      throw new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, e, e.getMessage());
    }
  }

  /**
   * The sink's primary-key columns via the shared {@link DataStructureSchema#resolvePrimaryKey}
   * "explicit wins, else marker" rule, so the PutDatabaseRecord UPSERT {@code Update Keys} are
   * identical to the PostGIS table's PRIMARY KEY (single source of truth, no divergence).
   */
  @SuppressWarnings("unchecked")
  private static List<String> resolvePrimaryKey(Map<String, Object> datasink)
      throws FatalAdapterException {
    Object explicit =
        datasink.get("configuration") instanceof Map<?, ?> config
            ? ((Map<String, Object>) config).get("primaryKey")
            : null;
    Map<String, Object> schema =
        datasink.get("dataStructure") instanceof Map<?, ?> ds ? (Map<String, Object>) ds : null;
    try {
      return DataStructureSchema.resolvePrimaryKey(explicit, schema);
    } catch (IllegalArgumentException e) {
      throw new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, e, e.getMessage());
    }
  }

  private static String asString(Object value) {
    return value instanceof String text ? text : null;
  }

  @Override
  public void bind(PostgisSinkSpec sink, PlanContext out) throws FatalAdapterException {
    // PostgisSinkSpec guarantees a non-blank tableName (the invalid state is rejected at
    // construction), so PutDatabaseRecord always has a target here.
    out.putSinkProperty("Table Name", sink.tableName());
    // Target the per-DataSet schema; a null (unset) schema leaves "Schema Name" out so the write
    // falls back to the connection search_path.
    if (sink.schemaName() != null) {
      out.putSinkProperty("Schema Name", sink.schemaName());
    }
    // With a primary key (the data structure's x-core-primaryKey marker), write UPSERT keyed on
    // it so a cron-recurring source that re-reads rows updates instead of duplicating them. NiFi
    // does not derive the conflict key from the table PK — it must be given via Update Keys.
    // The PutDatabaseRecord fragment quotes identifiers and does NOT translate field names: the
    // PostGIS table is created with quoted (case-preserving) identifiers, so an UPSERT of a
    // camelCase key would otherwise emit an unquoted "ON CONFLICT (stationId)" that PostgreSQL
    // folds to "stationid" and rejects as a missing column.
    if (!sink.primaryKeyColumns().isEmpty()) {
      out.putSinkProperty("Statement Type", "UPSERT");
      out.putSinkProperty("Update Keys", String.join(",", sink.primaryKeyColumns()));
      // UPSERT needs the PostgreSQL DatabaseAdapter to emit ON CONFLICT; the default "Generic"
      // adapter throws "UPSERT not supported" and routes every record to failure.
      out.putSinkProperty("Database Type", "PostgreSQL");
    }
    bindPlatformDbcp(out);
  }

  @Override
  public void registerControllerServices(BuildContext ctx) throws FatalAdapterException {
    ctx.addControllerService(Fragment.DBCP_CONNECTION_POOL, DBCP);
  }

  /**
   * Wires the terminal sink, then routes the upstream {@code failure} relationships AND the sink's
   * own write failures to a LogMessage sink (WARN + bulletin) instead of auto-terminating them.
   * Otherwise a malformed message, an unmappable record, or a failed write would be dropped
   * silently — invisible, undiagnosable data loss, which is exactly what must not happen at the
   * sink. The graph shape is intentionally stable: swapping LogMessage for a durable/recoverable
   * dead-letter sink is a later, isolated change.
   *
   * <p>The source is not wired here: ConsumeMQTT emits only {@code Message} and
   * QueryDatabaseTableRecord only {@code success} — neither has a parse-failure relationship to
   * route. A source <em>runtime</em> failure (DB unreachable after deploy, query error) surfaces as
   * a NiFi processor bulletin, not as an error-sink record; deploy-time config errors are caught
   * earlier by the connectivity probe and the bind-time guards.
   */
  @Override
  public void build(
      BuildContext ctx, Processor upstreamTail, List<Processor> upstreamFailureSources)
      throws FatalAdapterException {
    // A pre-region plan the sink cannot consume must fail the build: dropping it silently would
    // swallow transform output the user configured.
    if (ctx.spec().sinkPreRegion() != null) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, "POSTGIS sink cannot consume a pre-region plan");
    }
    Processor sink = ctx.loadProcessor(Fragment.PUT_DATABASE_RECORD, null);
    ctx.spec().sinkProperties().forEach((key, value) -> BuildContext.setProp(sink, key, value));
    ctx.addProcessor(sink);
    ctx.addChainConnection(upstreamTail, sink);

    Processor errorSink = ctx.loadProcessor(Fragment.LOG_MESSAGE, null);
    ctx.addProcessor(errorSink);
    for (Processor failureSource : upstreamFailureSources) {
      ctx.addConnection(failureSource, errorSink, "failure");
    }
    // The sink fragment auto-terminates its failure relationships by default; un-terminate them
    // (NiFi forbids a relationship being both auto-terminated and connected) and route them to the
    // same error sink so a failed write is logged, not lost.
    for (String relationship : FAILURE_RELATIONSHIPS) {
      BuildContext.removeAutoTerminated(sink, relationship);
      ctx.addConnection(sink, errorSink, relationship);
    }
  }

  private void bindPlatformDbcp(PlanContext out) throws FatalAdapterException {
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
        dbcp::put, "Database Connection URL", withStringtypeUnspecified(platformSink.postgisUrl()));
    putIfPresent(dbcp::put, "Database User", platformSink.postgisUser());
    dbcp.forEach((key, value) -> out.putControllerServiceProperty(DBCP, key, value));
    if (platformSink.postgisPassword() != null) {
      out.putSensitive(DBCP, "Password", platformSink.postgisPassword());
    }
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
