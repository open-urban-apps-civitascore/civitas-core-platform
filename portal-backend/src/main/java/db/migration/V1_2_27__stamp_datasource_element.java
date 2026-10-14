package db.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Names the Data structure version of every Data source in the {@code element} member of each
 * stored version of its configuration document, and records the {@code datasource-element} edge
 * that Model Forge derives from that member.
 *
 * <p>The versions are stamped in place instead of stored anew, because a new version would change
 * the Data source's {@code configurationUrn} while every Pipeline keeps its pin to the old one. The
 * URN derivations are copies of Model Forge's, because a later change there must not change what
 * this migration wrote.
 */
@Slf4j
public class V1_2_27__stamp_datasource_element extends BaseJavaMigration {

  private static final Pattern VERSIONED_URN = Pattern.compile("^(.*):(\\d+\\.\\d+\\.\\d+)$");
  private static final String REFERENCE_TYPE = "datasource-element";

  private record Stamp(Object representationId, Object versionId, String element) {}

  @Override
  public void migrate(Context context) {
    JdbcTemplate jdbc =
        new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));
    boolean hasRegistry =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT to_regclass('model_forge.artifact') IS NOT NULL", Boolean.class));
    if (!hasRegistry) {
      return;
    }

    List<Stamp> stamps =
        jdbc.query(
            """
            SELECT r.id, v.id, dsv.model_urn
            FROM data_sources ds
              JOIN data_structure_versions dsv ON dsv.id = ds.data_structure_version_id
              JOIN model_forge.artifact a ON a.logical_urn = ds.configuration_logical_urn
              JOIN model_forge.artifact_version v ON v.artifact_id = a.id
              JOIN model_forge.artifact_representation r ON r.version_id = v.id
            WHERE dsv.model_urn IS NOT NULL
              AND jsonb_typeof(r.content_jsonb) = 'object'
              AND r.content_jsonb -> 'element' IS NULL
            """,
            (rs, row) -> new Stamp(rs.getObject(1), rs.getObject(2), rs.getString(3)));

    List<Object[]> contentUpdates = new ArrayList<>();
    List<Object[]> references = new ArrayList<>();
    for (Stamp stamp : stamps) {
      contentUpdates.add(new Object[] {stamp.element(), stamp.representationId()});
      Matcher versioned = VERSIONED_URN.matcher(stamp.element());
      String logicalUrn = versioned.matches() ? versioned.group(1) : stamp.element();
      String version = versioned.matches() ? versioned.group(2) : null;
      references.add(
          new Object[] {
            UUID.randomUUID(),
            stamp.versionId(),
            stamp.element(),
            logicalUrn,
            logicalUrn,
            version,
            REFERENCE_TYPE,
            stamp.versionId(),
            REFERENCE_TYPE
          });
    }

    jdbc.batchUpdate(
        """
        UPDATE model_forge.artifact_representation
        SET content_jsonb = content_jsonb || jsonb_build_object('element', ?::text)
        WHERE id = ?::uuid
        """,
        contentUpdates);
    jdbc.batchUpdate(
        """
        UPDATE model_forge.artifact_representation
        SET content_hash = encode(sha256(convert_to(content_jsonb::text, 'UTF8')), 'hex')
        WHERE id = ?::uuid
        """,
        stamps.stream().map(stamp -> new Object[] {stamp.representationId()}).toList());
    jdbc.batchUpdate(
        """
        INSERT INTO model_forge.artifact_reference
          (id, from_version_id, target_urn, target_artifact_id, target_version_id,
           reference_type, reference_name, sort_order, created_at)
        SELECT ?::uuid, ?::uuid, ?,
          (SELECT id FROM model_forge.artifact WHERE logical_urn = ?),
          (SELECT av.id FROM model_forge.artifact_version av
             JOIN model_forge.artifact a ON a.id = av.artifact_id
           WHERE a.logical_urn = ? AND av.version = ?),
          ?, NULL, 0, now()
        WHERE NOT EXISTS (
          SELECT 1 FROM model_forge.artifact_reference
          WHERE from_version_id = ?::uuid AND reference_type = ?)
        """,
        references);
    log.info("Stamped the Data structure version into {} Data source versions", stamps.size());
  }
}
