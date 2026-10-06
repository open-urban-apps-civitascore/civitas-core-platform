package db.migration;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Rewrites every CORE URN from {@code ...:<domain>:<name>:<disambiguator>} to {@code
 * ...:<domain>:<uuid>}, in the portal tables and in the Model Forge registry, keeping scope, owner,
 * type, domain and any version or {@code :latest} suffix.
 *
 * <p>Each artifact gets the UUID the code of this release gives it: the portal entity's id, the
 * member id of a Data structure's {@code $defs} entry, or the name-based id of a FROST port or an
 * XRepository import. An artifact with none of these gets a name-based id of its old URN, so equal
 * old URNs still name one artifact. The derivations are copies, because a later change to the code
 * that mints ids must not change what this migration wrote.
 */
@Slf4j
public class V1_2_26__rekey_core_urns extends BaseJavaMigration {

  private static final String SEGMENT = "[A-Za-z0-9._-]+";
  private static final String TYPE =
      "element|datastructure|mapping|pipeline|datasource|datasink|dataset";
  private static final Pattern OLD_URN =
      Pattern.compile(
          "urn:core:(?<scope>%1$s):(?<owner>%1$s):(?<type>%2$s):(?<domain>%1$s):(?<name>%1$s)"
                  .formatted(SEGMENT, TYPE)
              + ":(?<disambiguator>[0-9a-z]{10})(?![0-9a-z])");

  private static final UUID CORE_NAMESPACE =
      UUID.fromString("5a0d5e1c-7b7e-4c43-9a8e-1f3f6c2b9d40");
  private static final char[] BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();
  private static final int DISAMBIGUATOR_LENGTH = 10;

  private record HostColumn(String table, String idColumn, String urnColumn) {}

  private static final List<HostColumn> HOST_COLUMNS =
      List.of(
          new HostColumn("data_structures", "id", "model_logical_urn"),
          new HostColumn("data_structure_versions", "data_structure_id", "model_urn"),
          new HostColumn("pipelines", "id", "model_logical_urn"),
          new HostColumn("pipelines", "id", "model_urn"),
          new HostColumn("data_sinks", "id", "configuration_logical_urn"),
          new HostColumn("data_sinks", "id", "configuration_urn"),
          new HostColumn("data_sources", "id", "configuration_logical_urn"),
          new HostColumn("data_sources", "id", "configuration_urn"),
          new HostColumn("datasets", "id", "manifest_logical_urn"));

  private static final List<String> PORTAL_URN_COLUMNS =
      List.of(
          "data_structures.model_logical_urn",
          "data_structure_versions.model_urn",
          "pipelines.model_logical_urn",
          "pipelines.model_urn",
          "data_sinks.configuration_logical_urn",
          "data_sinks.configuration_urn",
          "data_sources.configuration_logical_urn",
          "data_sources.configuration_urn",
          "datasets.manifest_logical_urn",
          "published_structures.logical_urn",
          "published_structures.versioned_urn");

  private final Map<String, UUID> ids = new HashMap<>();
  private int withoutDerivableId;

  @Override
  public void migrate(Context context) throws SQLException {
    Connection connection = context.getConnection();
    JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    boolean hasRegistry =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT to_regclass('model_forge.artifact') IS NOT NULL", Boolean.class));

    collectHostIds(jdbc);
    if (hasRegistry) {
      collectMemberIds(jdbc);
    }

    int rows = 0;
    for (String column : PORTAL_URN_COLUMNS) {
      String[] tableAndColumn = column.split("\\.");
      rows += rewriteTextColumn(jdbc, tableAndColumn[0], "id", tableAndColumn[1]);
    }
    rows += rewriteImportedStructures(jdbc, connection);
    if (hasRegistry) {
      rows += rewriteTextColumn(jdbc, "model_forge.artifact", "id", "logical_urn");
      rows += rewriteTextColumn(jdbc, "model_forge.artifact_reference", "id", "target_urn");
      rows += rewriteContent(jdbc);
    }
    log.info(
        "Rewrote CORE URNs to the UUID form in {} rows; {} artifacts had no derivable id",
        rows,
        withoutDerivableId);
  }

  private void collectHostIds(JdbcTemplate jdbc) {
    for (HostColumn column : HOST_COLUMNS) {
      jdbc.query(
          "SELECT %s, %s FROM %s WHERE %s IS NOT NULL ORDER BY created_at"
              .formatted(column.idColumn(), column.urnColumn(), column.table(), column.urnColumn()),
          rs -> {
            Matcher urn = OLD_URN.matcher(rs.getString(2));
            if (urn.lookingAt()) {
              ids.putIfAbsent(identity(urn), rs.getObject(1, UUID.class));
            }
          });
    }
  }

  /**
   * An Element that a Data structure lists under {@code $defs} gets the member id of that Data
   * structure and the {@code $defs} key. Should two Data structures list the same Element, the
   * older listing wins.
   */
  private void collectMemberIds(JdbcTemplate jdbc) {
    jdbc.query(
        """
        SELECT a.logical_urn, d.key, d.value ->> '$ref'
        FROM model_forge.artifact a
          JOIN model_forge.artifact_version v ON v.artifact_id = a.id
          JOIN model_forge.artifact_representation r ON r.version_id = v.id
          CROSS JOIN LATERAL jsonb_each(r.content_jsonb -> '$defs') d
        WHERE a.artifact_type = 'datastructure'
          AND jsonb_typeof(r.content_jsonb -> '$defs') = 'object'
        ORDER BY v.created_at, a.logical_urn, d.key
        """,
        rs -> {
          Matcher parent = OLD_URN.matcher(rs.getString(1));
          Matcher member = OLD_URN.matcher(Objects.requireNonNullElse(rs.getString(3), ""));
          if (parent.lookingAt() && member.lookingAt() && member.group("type").equals("element")) {
            // A type converted from an XSD has a name-derived disambiguator, and the converter
            // derives its UUID from the URN prefix and the type name.
            UUID id =
                hasNameDerivedDisambiguator(member)
                    ? deriveId(elementPrefix(member) + ":" + member.group("name"))
                    : memberId(idOf(parent), rs.getString(2));
            ids.putIfAbsent(identity(member), id);
          }
        });
  }

  private int rewriteTextColumn(JdbcTemplate jdbc, String table, String idColumn, String column) {
    List<Object[]> updates = new ArrayList<>();
    jdbc.query(
        "SELECT %s, %s FROM %s WHERE %s LIKE '%%urn:core:%%'"
            .formatted(idColumn, column, table, column),
        rs -> {
          String old = rs.getString(2);
          String rewritten = rewrite(old);
          if (!rewritten.equals(old)) {
            updates.add(new Object[] {rewritten, rs.getObject(1)});
          }
        });
    jdbc.batchUpdate(
        "UPDATE %s SET %s = ? WHERE %s = ?".formatted(table, column, idColumn), updates);
    return updates.size();
  }

  private int rewriteImportedStructures(JdbcTemplate jdbc, Connection connection) {
    List<Object[]> updates = new ArrayList<>();
    jdbc.query(
        "SELECT id, imported_structure_urns FROM data_structure_versions"
            + " WHERE imported_structure_urns IS NOT NULL",
        rs -> {
          Array array = rs.getArray(2);
          String[] urns = (String[]) array.getArray();
          String[] rewritten = new String[urns.length];
          for (int i = 0; i < urns.length; i++) {
            rewritten[i] = urns[i] == null ? null : rewrite(urns[i]);
          }
          if (!Arrays.equals(urns, rewritten)) {
            updates.add(
                new Object[] {connection.createArrayOf("text", rewritten), rs.getObject(1)});
          }
        });
    jdbc.batchUpdate(
        "UPDATE data_structure_versions SET imported_structure_urns = ? WHERE id = ?", updates);
    return updates.size();
  }

  /**
   * Model Forge treats content with an unchanged hash as an unchanged version. The original hash
   * cannot be rebuilt, since it covers the client's key order and jsonb keeps none. The new hash
   * covers the rewritten text, so the first save of an unchanged model may still add a version.
   */
  private int rewriteContent(JdbcTemplate jdbc) {
    List<Object[]> updates = new ArrayList<>();
    jdbc.query(
        """
        SELECT id, content_jsonb::text, content_text
        FROM model_forge.artifact_representation
        WHERE content_jsonb::text LIKE '%urn:core:%' OR content_text LIKE '%urn:core:%'
        """,
        rs -> {
          String json = rs.getString(2);
          String text = rs.getString(3);
          String newJson = json == null ? null : rewrite(json);
          String newText = text == null ? null : rewrite(text);
          if (!Objects.equals(json, newJson) || !Objects.equals(text, newText)) {
            String hash = sha256Hex(newText != null ? newText : newJson);
            updates.add(new Object[] {newJson, newText, hash, rs.getObject(1)});
          }
        });
    jdbc.batchUpdate(
        "UPDATE model_forge.artifact_representation"
            + " SET content_jsonb = ?::jsonb, content_text = ?, content_hash = ? WHERE id = ?",
        updates);
    return updates.size();
  }

  private String rewrite(String text) {
    return OLD_URN.matcher(text).replaceAll(urn -> Matcher.quoteReplacement(newLogicalUrn(urn)));
  }

  private String newLogicalUrn(MatchResult urn) {
    return "urn:core:%s:%s:%s:%s:%s"
        .formatted(
            urn.group("scope"),
            urn.group("owner"),
            urn.group("type"),
            urn.group("domain"),
            idOf(urn));
  }

  private UUID idOf(MatchResult urn) {
    return ids.computeIfAbsent(identity(urn), key -> fallbackId(urn, key));
  }

  /**
   * The type is not part of the identity: a Data structure and the Element it was stored as share
   * their name and disambiguator, and they share their UUID.
   */
  private static String identity(MatchResult urn) {
    return "urn:core:%s:%s:%s:%s:%s"
        .formatted(
            urn.group("scope"),
            urn.group("owner"),
            urn.group("domain"),
            urn.group("name"),
            urn.group("disambiguator"));
  }

  private UUID fallbackId(MatchResult urn, String identity) {
    String type = urn.group("type");
    String domain = urn.group("domain");
    String name = urn.group("name");
    if (type.equals("datastructure") && domain.equals("frost")) {
      return deriveId("frost-port:" + name);
    }
    if (type.equals("element") && hasNameDerivedDisambiguator(urn)) {
      return deriveId("xrepository:" + domain + ":" + name);
    }
    withoutDerivableId++;
    UUID id = deriveId("legacy:" + identity);
    log.info("{} has no derivable id and becomes {}", identity, id);
    return id;
  }

  /** XRepository imports derived the disambiguator from the name alone; every other was random. */
  private static boolean hasNameDerivedDisambiguator(MatchResult urn) {
    return urn.group("disambiguator").equals(oldDerivedDisambiguator(urn.group("name")));
  }

  private static String elementPrefix(MatchResult urn) {
    return "urn:core:%s:%s:element:%s"
        .formatted(urn.group("scope"), urn.group("owner"), urn.group("domain"));
  }

  private static UUID deriveId(String key) {
    return nameBasedId(CORE_NAMESPACE, key);
  }

  private static UUID memberId(UUID parent, String key) {
    return nameBasedId(parent, key);
  }

  /** A name-based UUIDv8 with SHA-256, as in RFC 9562 Appendix B.2. */
  private static UUID nameBasedId(UUID namespace, String name) {
    byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
    byte[] input =
        ByteBuffer.allocate(16 + nameBytes.length)
            .putLong(namespace.getMostSignificantBits())
            .putLong(namespace.getLeastSignificantBits())
            .put(nameBytes)
            .array();
    ByteBuffer hash = ByteBuffer.wrap(sha256(input));
    long msb = (hash.getLong() & ~0xF000L) | 0x8000L;
    long lsb = (hash.getLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
    return new UUID(msb, lsb);
  }

  private static String oldDerivedDisambiguator(String key) {
    byte[] hash = sha256(key.getBytes(StandardCharsets.UTF_8));
    char[] out = new char[DISAMBIGUATOR_LENGTH];
    for (int i = 0; i < out.length; i++) {
      out[i] = BASE36[(hash[i] & 0xFF) % BASE36.length];
    }
    return new String(out);
  }

  private static String sha256Hex(String content) {
    return HexFormat.of().formatHex(sha256(content.getBytes(StandardCharsets.UTF_8)));
  }

  private static byte[] sha256(byte[] input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
