package db.migration;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.owasp.encoder.Encode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Removes references from Data structure models to models of other Data structures, which a caller
 * could store without the right to read them. References to own models and to published structures
 * stay.
 *
 * <p>Own is decided as at write time, by the Data structure that minted an Element. The derivation
 * is a copy, so a later change to the code that mints ids does not change this migration. Released
 * versions are rewritten in place, so every removal is logged.
 */
@Slf4j
public class V1_2_28__strip_foreign_model_references extends BaseJavaMigration {

  private static final Pattern CORE_URN =
      Pattern.compile(
          "urn:core:[^:]+:[^:]+:(?<type>[^:]+):[^:]+:"
              + "(?<id>[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?::[^:]+)?");
  private static final Pattern CATEGORY_MARKER = Pattern.compile("urn:core:type:[A-Za-z0-9._-]+");
  private static final int TYPE_SEGMENT = 4;
  private static final int LOGICAL_SEGMENTS = 7;

  private final ObjectMapper mapper = new ObjectMapper();
  private final Set<String> held = new HashSet<>();
  private final Set<String> published = new HashSet<>();
  private final Map<String, Set<String>> keysOf = new HashMap<>();
  private final Map<String, Set<String>> listedBy = new HashMap<>();
  private final Map<String, Set<String>> listers = new HashMap<>();
  private final Map<String, Optional<String>> minters = new HashMap<>();
  private int removedReferences;
  private int rewrittenRows;
  private int deletedEdges;

  private record Host(UUID id, String grouping, String root, Set<String> derived) {}

  private record Row(UUID id, UUID versionId, String version, String content) {}

  @Override
  public void migrate(Context context) throws SQLException {
    JdbcTemplate jdbc =
        new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));
    if (!Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT to_regclass('model_forge.artifact') IS NOT NULL", Boolean.class))) {
      log.info("No Model Forge registry, so no model references to strip");
      return;
    }
    index(jdbc);
    List<Host> hosts = new ArrayList<>();
    jdbc.query(
        "SELECT id, model_logical_urn FROM data_structures WHERE model_logical_urn IS NOT NULL",
        rs -> {
          Host host = host(rs.getString(2));
          if (host != null) {
            hosts.add(host);
          }
        });
    for (Host host : hosts) {
      strip(jdbc, host);
    }
    log.info(
        "Removed {} foreign model references from {} stored representations and {} reference"
            + " edges of {} data structures",
        removedReferences,
        rewrittenRows,
        deletedEdges,
        hosts.size());
  }

  /** Every artifact, every published structure, and what each grouping lists in any version. */
  private void index(JdbcTemplate jdbc) {
    jdbc.query(
        "SELECT logical_urn FROM model_forge.artifact",
        rs -> {
          held.add(rs.getString(1));
        });
    jdbc.query(
        "SELECT logical_urn FROM published_structures",
        rs -> {
          published.add(rs.getString(1));
        });
    jdbc.query(
        """
        SELECT a.logical_urn, d.key, d.value ->> '$ref'
        FROM model_forge.artifact a
          JOIN model_forge.artifact_version v ON v.artifact_id = a.id
          JOIN model_forge.artifact_representation r ON r.version_id = v.id
          CROSS JOIN LATERAL jsonb_each(
            CASE WHEN jsonb_typeof(r.content_jsonb -> '$defs') = 'object'
                 THEN r.content_jsonb -> '$defs' ELSE '{}'::jsonb END) d
        WHERE a.artifact_type = 'datastructure'
        """,
        rs -> {
          String grouping = rs.getString(1);
          keysOf.computeIfAbsent(grouping, g -> new HashSet<>()).add(rs.getString(2));
          String member = rs.getString(3);
          if (member != null && CORE_URN.matcher(member).matches()) {
            String element = logical(member);
            listedBy.computeIfAbsent(grouping, g -> new HashSet<>()).add(element);
            listers.computeIfAbsent(element, e -> new LinkedHashSet<>()).add(grouping);
          }
        });
  }

  private Host host(String modelLogicalUrn) {
    Matcher urn = CORE_URN.matcher(modelLogicalUrn);
    if (!urn.matches()) {
      return null;
    }
    UUID id = UUID.fromString(urn.group("id"));
    String grouping = withType(logical(modelLogicalUrn), "datastructure");
    String root = withType(grouping, "element");
    Set<String> derived = new HashSet<>(Set.of(grouping, root));
    for (String key : keysOf.getOrDefault(grouping, Set.of())) {
      derived.add(withId(root, memberId(id, key)));
    }
    return new Host(id, grouping, root, derived);
  }

  private void strip(JdbcTemplate jdbc, Host host) {
    Set<String> artifacts = new LinkedHashSet<>();
    if (held.contains(host.grouping())) {
      artifacts.add(host.grouping());
    }
    if (held.contains(host.root())) {
      artifacts.add(host.root());
    }
    for (String member : listedBy.getOrDefault(host.grouping(), Set.of())) {
      if (host.grouping().equals(mintedBy(member))) {
        artifacts.add(member);
      }
    }
    Set<String> changedPins = new LinkedHashSet<>();
    for (String artifact : artifacts) {
      for (Row row : rows(jdbc, artifact)) {
        if (stripRow(jdbc, host, artifact, row)) {
          changedPins.add(artifact + ":" + row.version());
        }
        sweepEdges(jdbc, host, row.versionId());
      }
    }
    if (!changedPins.isEmpty()) {
      warnAboutReleasedVersions(jdbc, host, changedPins);
    }
  }

  private List<Row> rows(JdbcTemplate jdbc, String artifact) {
    return jdbc.query(
        """
        SELECT r.id, r.version_id, v.version, r.content_jsonb::text
        FROM model_forge.artifact a
          JOIN model_forge.artifact_version v ON v.artifact_id = a.id
          JOIN model_forge.artifact_representation r ON r.version_id = v.id
        WHERE a.logical_urn = ? AND r.content_jsonb IS NOT NULL
        """,
        (rs, i) ->
            new Row(
                rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class),
                rs.getString(3),
                rs.getString(4)),
        artifact);
  }

  private boolean stripRow(JdbcTemplate jdbc, Host host, String artifact, Row row) {
    JsonNode document = mapper.readTree(row.content());
    List<String> removed = new ArrayList<>();
    if (document instanceof ObjectNode object && "datastructure".equals(type(artifact))) {
      stripBareMembers(object, host, removed);
    }
    stripNode(document, host, removed);
    if (removed.isEmpty()) {
      return false;
    }
    String json = mapper.writeValueAsString(document);
    jdbc.update(
        """
        UPDATE model_forge.artifact_representation
        SET content_jsonb = ?::jsonb,
            content_text = CASE WHEN content_text IS NULL THEN NULL ELSE (?::jsonb)::text END,
            content_hash = encode(sha256(convert_to((?::jsonb)::text, 'UTF8')), 'hex')
        WHERE id = ?
        """,
        json,
        json,
        json,
        row.id());
    rewrittenRows++;
    removedReferences += removed.size();
    for (String reference : removed) {
      log.info(
          "Removed foreign model reference {} from {} version {} of data structure {}",
          Encode.forJava(reference),
          Encode.forJava(artifact),
          Encode.forJava(row.version()),
          host.id());
    }
    return true;
  }

  /** A grouping's member must be an Element of the grouping, so a foreign member is dropped. */
  private void stripBareMembers(ObjectNode document, Host host, List<String> removed) {
    if (!(document.get("$defs") instanceof ObjectNode defs)) {
      return;
    }
    List<String> foreignKeys = new ArrayList<>();
    for (Map.Entry<String, JsonNode> member : defs.properties()) {
      JsonNode value = member.getValue();
      if (value.isObject() && value.size() == 1 && isForeign(text(value.get("$ref")), host)) {
        foreignKeys.add(member.getKey());
      }
    }
    for (String key : foreignKeys) {
      removed.add(text(defs.get(key).get("$ref")));
      defs.remove(key);
    }
  }

  private void stripNode(JsonNode node, Host host, List<String> removed) {
    if (node instanceof ObjectNode object) {
      String ref = text(object.get("$ref"));
      if (isForeign(ref, host)) {
        removed.add(ref);
        object.remove("$ref");
      }
      String link =
          object.get("x-core-ref") instanceof ObjectNode coreRef ? text(coreRef.get("type")) : null;
      if (link != null && !CATEGORY_MARKER.matcher(link).matches() && isForeign(link, host)) {
        removed.add(link);
        object.remove("x-core-ref");
      }
      for (JsonNode child : object.values()) {
        stripNode(child, host, removed);
      }
    } else if (node instanceof ArrayNode array) {
      for (JsonNode child : array.values()) {
        stripNode(child, host, removed);
      }
    }
  }

  /** Removes the edges of foreign references. */
  private void sweepEdges(JdbcTemplate jdbc, Host host, UUID versionId) {
    List<UUID> foreign = new ArrayList<>();
    jdbc.query(
        """
        SELECT id, target_urn FROM model_forge.artifact_reference
        WHERE from_version_id = ?
          AND reference_type IN ('schema-ref', 'association-ref', 'datastructure-ref')
        """,
        rs -> {
          if (isForeign(rs.getString(2), host)) {
            foreign.add(rs.getObject(1, UUID.class));
          }
        },
        versionId);
    for (UUID edge : foreign) {
      deletedEdges += jdbc.update("DELETE FROM model_forge.artifact_reference WHERE id = ?", edge);
    }
  }

  /** A released version changed when its model, or an Element its model refers to, changed. */
  private void warnAboutReleasedVersions(JdbcTemplate jdbc, Host host, Set<String> changedPins) {
    Set<String> changed = new LinkedHashSet<>(changedPins);
    changedPins.forEach(pin -> changed.add(pin.substring(0, pin.lastIndexOf(':'))));
    List<Object> parameters = new ArrayList<>(List.of(host.id()));
    parameters.addAll(changed);
    parameters.addAll(changed);
    List<String> released =
        jdbc.queryForList(
            """
            SELECT dsv.version FROM data_structure_versions dsv
            WHERE dsv.data_structure_id = ? AND dsv.data_structure_version_status <> 'DRAFT'
              AND (dsv.model_urn IN (%1$s)
                OR EXISTS (SELECT 1 FROM model_forge.artifact a
                  JOIN model_forge.artifact_version v ON v.artifact_id = a.id
                  JOIN model_forge.artifact_reference e ON e.from_version_id = v.id
                  WHERE a.logical_urn || ':' || v.version = dsv.model_urn
                    AND e.target_urn IN (%1$s)))
            ORDER BY dsv.version
            """
                .formatted(String.join(", ", Collections.nCopies(changed.size(), "?"))),
            String.class,
            parameters.toArray());
    if (!released.isEmpty()) {
      log.warn(
          "Data structure {} lost foreign model references in released versions {}",
          host.id(),
          released);
    }
  }

  private boolean isForeign(String urn, Host host) {
    if (urn == null || !CORE_URN.matcher(urn).matches()) {
      return false;
    }
    String target = logical(urn);
    if (host.derived().contains(target)) {
      return false;
    }
    String minter = mintedBy(target);
    if (published.contains(target) || (minter != null && published.contains(minter))) {
      return false;
    }
    return minter != null
        ? !minter.equals(host.grouping())
        : !listedBy.getOrDefault(host.grouping(), Set.of()).contains(target);
  }

  private String mintedBy(String target) {
    return minters.computeIfAbsent(target, this::minterOf).orElse(null);
  }

  private Optional<String> minterOf(String target) {
    String type = type(target);
    if ("datastructure".equals(type)) {
      return held.contains(target) ? Optional.of(target) : Optional.empty();
    }
    if (!"element".equals(type)) {
      return Optional.empty();
    }
    String sameId = withType(target, "datastructure");
    if (held.contains(sameId)) {
      return Optional.of(sameId);
    }
    UUID elementId = idOf(target);
    for (String lister : listers.getOrDefault(target, Set.of())) {
      UUID listerId = idOf(lister);
      for (String key : keysOf.getOrDefault(lister, Set.of())) {
        if (memberId(listerId, key).equals(elementId)) {
          return Optional.of(lister);
        }
      }
    }
    return Optional.empty();
  }

  private static String text(JsonNode node) {
    return node != null && node.isString() ? node.asString() : null;
  }

  private static String logical(String urn) {
    String[] segments = urn.split(":");
    return segments.length <= LOGICAL_SEGMENTS
        ? urn
        : String.join(":", List.of(segments).subList(0, LOGICAL_SEGMENTS));
  }

  private static String type(String urn) {
    return urn.split(":")[TYPE_SEGMENT];
  }

  private static UUID idOf(String urn) {
    return UUID.fromString(urn.split(":")[LOGICAL_SEGMENTS - 1]);
  }

  private static String withType(String logicalUrn, String type) {
    String[] segments = logicalUrn.split(":");
    segments[TYPE_SEGMENT] = type;
    return String.join(":", segments);
  }

  private static String withId(String logicalUrn, UUID id) {
    return logicalUrn.substring(0, logicalUrn.lastIndexOf(':') + 1) + id;
  }

  /** A name-based UUIDv8 with SHA-256 (RFC 9562 Appendix B.2), as members are minted. */
  private static UUID memberId(UUID parent, String key) {
    byte[] nameBytes = key.getBytes(StandardCharsets.UTF_8);
    byte[] input =
        ByteBuffer.allocate(16 + nameBytes.length)
            .putLong(parent.getMostSignificantBits())
            .putLong(parent.getLeastSignificantBits())
            .put(nameBytes)
            .array();
    ByteBuffer hash = ByteBuffer.wrap(sha256(input));
    long msb = (hash.getLong() & ~0xF000L) | 0x8000L;
    long lsb = (hash.getLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
    return new UUID(msb, lsb);
  }

  private static byte[] sha256(byte[] input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
