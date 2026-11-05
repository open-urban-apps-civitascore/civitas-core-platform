package de.civitascore.modelforge.persistence.postgres;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import de.civitascore.modelforge.contract.RegistryConflictException;
import de.civitascore.modelforge.contract.RegistryUnavailableException;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import de.civitascore.modelforge.core.port.ArtifactSearchCriteria;
import de.civitascore.modelforge.core.port.ArtifactSearchResult;
import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.core.port.XsdSchemaConverter;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.urn.UrnService;
import de.civitascore.modelforge.util.JsonSchema;
import de.civitascore.modelforge.util.NameMatch;
import de.civitascore.modelforge.util.XmlInputGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * PostgreSQL-backed {@link ArtifactRegistry}: the durable store for all CORE artifacts.
 *
 * <p>Identity is format-agnostic: the {@code logical_urn} never carries the format. Each
 * {@code model_forge.artifact_version} records its authored {@code primary_format}; the format-specific
 * content lives in {@code model_forge.artifact_representation}. Whether an model_forge.artifact is JSON Schema or XSD
 * is therefore a property of its stored representations, read from the DB — never inferred
 * from the URN. Versioning is backend-owned: the first version of a new model_forge.artifact is
 * {@code 1.0.0}, or the next major after the highest version its URN ever had; every update bumps by
 * the supplied {@link VersionBump} from the version the caller named as its base, or from the highest
 * version ever assigned when it named none or asked for a major. A number this registry assigns is
 * never assigned again, also after its version or the whole artifact is deleted
 * ({@code model_forge.version_high_water}); an explicit version is taken as given. Identical authored
 * content in the same format is idempotent
 * (matched by {@code content_hash}). The COMPLETE reference graph — including cycles — is persisted.
 *
 * <p>Every write runs in a single transaction (model_forge.artifact upsert → version insert →
 * representation insert → reference replace → current-version update → namespace-index update).
 * Storage failures surface as {@link RegistryUnavailableException}.
 */
public class PostgresArtifactRegistryClient implements ArtifactRegistry {

    private static final Logger log = LoggerFactory.getLogger(PostgresArtifactRegistryClient.class);

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final UrnService urns;

    private final ArtifactRepository               artifacts;
    private final ArtifactVersionRepository        versions;
    private final ArtifactRepresentationRepository representations;
    private final ArtifactReferenceRepository      references;
    private final XsdNamespaceRepository           namespaces;
    private final VersionHighWaterRepository       highWater;
    private final XsdSchemaSupport                 xsd;

    public PostgresArtifactRegistryClient(JdbcClient jdbc,
                                          PlatformTransactionManager txManager,
                                          ObjectMapper mapper,
                                          UrnService urns,
                                          XsdSchemaConverter xsdConverter) {
        this.jdbc            = jdbc;
        this.tx              = new TransactionTemplate(txManager);
        this.mapper          = mapper;
        this.urns            = urns;
        this.artifacts       = new ArtifactRepository(jdbc);
        this.versions        = new ArtifactVersionRepository(jdbc);
        this.representations = new ArtifactRepresentationRepository(jdbc);
        this.references      = new ArtifactReferenceRepository(jdbc);
        this.namespaces      = new XsdNamespaceRepository(jdbc);
        this.highWater       = new VersionHighWaterRepository(jdbc);
        this.xsd             = new XsdSchemaSupport(xsdConverter, mapper);
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Override
    public String storeElement(JsonNode schema, Set<String> refs, Set<String> associationTargets,
                                String explicitVersion, VersionBump bump, String bumpFromVersion) {
        String urn = schemaUrn(schema);
        List<ReferenceRow> rows = new ArrayList<>(ReferenceExtraction.schemaRefs(refs));
        rows.addAll(ReferenceExtraction.associationRefs(associationTargets));
        return writeArtifact(urn, RegistryMapping.TYPE_ELEMENT, RegistryMapping.FORMAT_JSONSCHEMA,
            RegistryMapping.CONTENT_TYPE_JSON,
            writeJson(schema), null, rows, bump, explicitVersion, bumpFromVersion, null);
    }

    @Override
    public String storeMapping(String id, JsonNode m, VersionBump bump) {
        JsonNode payload = m;
        return writeArtifact(id, RegistryMapping.TYPE_MAPPING, RegistryMapping.FORMAT_CORE_JSON,
            RegistryMapping.CONTENT_TYPE_JSON,
            writeJson(payload), null, ReferenceExtraction.mappingRefs(payload), bump, null, null, null);
    }

    @Override
    public String storePipeline(String id, JsonNode p, VersionBump bump) {
        JsonNode payload = p;
        return writeArtifact(id, RegistryMapping.TYPE_PIPELINE, RegistryMapping.FORMAT_CORE_JSON,
            RegistryMapping.CONTENT_TYPE_JSON,
            writeJson(payload), null, ReferenceExtraction.pipelineRefs(payload), bump, null, null, null);
    }

    @Override
    public String storeDataSource(String id, JsonNode s, VersionBump bump) {
        JsonNode payload = s;
        return writeArtifact(id, RegistryMapping.TYPE_DATASOURCE, RegistryMapping.FORMAT_CORE_JSON,
            RegistryMapping.CONTENT_TYPE_JSON, writeJson(payload), null, ReferenceExtraction.dataSourceRefs(payload), bump, null, null, null);
    }

    @Override
    public String storeDataSink(String id, JsonNode s, VersionBump bump) {
        JsonNode payload = s;
        return writeArtifact(id, RegistryMapping.TYPE_DATASINK, RegistryMapping.FORMAT_CORE_JSON,
            RegistryMapping.CONTENT_TYPE_JSON, writeJson(payload), null, ReferenceExtraction.dataSinkRefs(payload), bump, null, null, null);
    }

    @Override
    public String storeDashboard(String id, JsonNode dashboard, VersionBump bump) {
        return writeArtifact(
            id,
            RegistryMapping.TYPE_DASHBOARD,
            RegistryMapping.FORMAT_CORE_JSON,
            RegistryMapping.CONTENT_TYPE_JSON,
            writeJson(dashboard),
            null,
            List.of(),
            bump,
            null,
            null,
            null);
    }

    @Override
    public String storeDataSet(String urn, JsonNode manifest, VersionBump bump) {
        return writeArtifact(UrnParser.logicalUrn(urn), RegistryMapping.TYPE_DATASET, RegistryMapping.FORMAT_CORE_JSON,
            RegistryMapping.CONTENT_TYPE_JSON, writeJson(manifest), null, ReferenceExtraction.dataSetRefs(manifest), bump, null, null, null);
    }

    @Override
    public String storeDataStructure(String urn, JsonNode manifest, VersionBump bump, String bumpFromVersion) {
        return writeArtifact(UrnParser.logicalUrn(urn), RegistryMapping.TYPE_DATASTRUCTURE, RegistryMapping.FORMAT_CORE_JSON,
            RegistryMapping.CONTENT_TYPE_JSON, writeJson(manifest), null, ReferenceExtraction.dataStructureRefs(manifest), bump,
            null, bumpFromVersion, null);
    }

    @Override
    public String storeXsdElement(String urn, String xsdContent, Set<String> refs, String explicitVersion,
                                   VersionBump bump) {
        XmlInputGuard.rejectUnsafeXml(xsdContent);
        xsd.evict(urn);
        return writeArtifact(UrnParser.logicalUrn(urn), RegistryMapping.TYPE_ELEMENT, RegistryMapping.FORMAT_XSD,
            RegistryMapping.CONTENT_TYPE_XML, null, xsdContent, ReferenceExtraction.xsdImportRefs(refs), bump,
            explicitVersion, null,
            (artifactId, versionId, now) -> {
                // Drop this model_forge.artifact's previously-indexed namespace(s) first so an in-place
                // targetNamespace change/removal cannot leave a stale namespace → model_forge.artifact row.
                namespaces.deleteByArtifactId(artifactId);
                xsd.extractTargetNamespace(xsdContent)
                    .ifPresent(ns -> namespaces.upsert(ns, artifactId, versionId, now));
            });
    }

    /**
     * Copies the source version's rows to a new version and advances the artifact to it. Every
     * representation is copied, not only the primary one, and the References are copied as rows
     * rather than re-extracted from content — a Reference the content does not spell out survives
     * that way by construction.
     */
    @Override
    public String copyVersion(String sourcePin, String countAbove, Map<String, String> retargets) {
        // Reject control characters before the URN reaches an exception message a caller may log.
        UrnParser.requireNoControlChars(sourcePin);
        String logical = UrnParser.logicalUrn(sourcePin);
        String sourceVersion = UrnParser.versionFromUrn(sourcePin);
        try {
            return inTxResult(() -> {
                // Same lock the store methods take, so a concurrent save cannot read this current_version
                // and collide with the version assigned here.
                lockArtifactWrite(logical);
                ArtifactRow artifact = artifacts.findByLogicalUrn(logical)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown artifact: " + logical));
                ArtifactVersionRow source = versions.find(artifact.id(), sourceVersion)
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown version " + sourceVersion + " of " + logical));
                String newVersion = SemVer.next(
                    higher(highestAssigned(logical, artifact.currentVersion()), countAbove), VersionBump.MAJOR);
                Instant now = Instant.now();
                UUID versionId = UUID.randomUUID();
                insertVersion(logical, new ArtifactVersionRow(versionId, artifact.id(), newVersion,
                    source.primaryFormat(), source.title(), source.description(), now, source.createdBy()));
                for (String format : representations.listFormats(source.id())) {
                    representations.find(source.id(), format).ifPresent(rep ->
                        representations.insert(retargeted(rep, versionId, retargets, now)));
                }
                references.replaceForVersion(versionId, references.rowsForVersion(source.id()).stream()
                    .map(r -> retargets.containsKey(r.targetUrn())
                        ? new ReferenceRow(retargets.get(r.targetUrn()), r.referenceType(), r.referenceName(), r.sortOrder())
                        : r)
                    .toList());
                artifacts.updateCurrentVersion(artifact.id(), newVersion, now);
                String pin = UrnParser.withVersion(logical, newVersion);
                // Back-fill pinned references that named this exact (logical, version) before it existed.
                references.linkDanglingVersions(pin, versionId);
                // The namespace index addresses a version, so leaving it on the superseded one would
                // make a namespace lookup resolve to a stale version of the artifact.
                if (RegistryMapping.FORMAT_XSD.equals(source.primaryFormat())) {
                    namespaces.repointVersion(artifact.id(), versionId, now);
                    xsd.evict(logical);
                }
                log.info("Copied {} v{} to v{}", logical, sourceVersion, newVersion);
                return pin;
            });
        } catch (DataAccessException e) {
            throw translate(e, "Registry version copy failed for " + logical);
        }
    }

    private static String higher(String version, String other) {
        return other != null && SemVer.compare(other, version) > 0 ? other : version;
    }

    /** The representation for the copy, its JSON content's {@code $ref} values retargeted. */
    private ArtifactRepresentationRow retargeted(ArtifactRepresentationRow rep, UUID versionId,
                                                 Map<String, String> retargets, Instant now) {
        String json = rep.contentJson();
        String hash = rep.contentHash();
        if (json != null && !retargets.isEmpty()) {
            JsonNode content = readJson(json);
            if (retargetRefs(content, retargets)) {
                json = writeJson(content);
                hash = sha256(json);
            }
        }
        return new ArtifactRepresentationRow(UUID.randomUUID(), versionId, rep.format(), rep.contentType(),
            json, rep.contentText(), hash, rep.generation(), now);
    }

    private static boolean retargetRefs(JsonNode node, Map<String, String> retargets) {
        boolean changed = false;
        String ref = node.path("$ref").asText(null);
        if (node instanceof ObjectNode object && ref != null && retargets.containsKey(ref)) {
            object.put("$ref", retargets.get(ref));
            changed = true;
        }
        if (node.isObject() || node.isArray()) {
            for (JsonNode child : node) changed |= retargetRefs(child, retargets);
        }
        return changed;
    }

    @Override
    public void deleteArtifact(String urn) {
        UrnParser.requireNoControlChars(urn);
        String logical = UrnParser.logicalUrn(urn);
        try {
            boolean removed = inTxResult(() -> artifacts.deleteByLogicalUrn(logical));
            if (removed) log.info("Deleted model_forge.artifact {} from registry", logical);
            else log.debug("Artifact {} not present — delete is a no-op", logical);
        } catch (DataAccessException e) {
            throw translate(e, "Could not delete model_forge.artifact " + logical);
        }
    }

    @Override
    public List<String> versionsOf(String urn) {
        UrnParser.requireNoControlChars(urn);
        String logical = UrnParser.logicalUrn(urn);
        return read(() -> artifacts.findByLogicalUrn(logical)
            .map(artifact -> versions.listVersions(artifact.id()).stream().sorted(SemVer::compare).toList())
            .orElseGet(List::of));
    }

    @Override
    public List<String> versionOwners(Collection<String> pinnedUrns) {
        pinnedUrns.forEach(UrnParser::requireNoControlChars);
        return pinnedUrns.isEmpty() ? List.of() : read(() -> references.ownersOfVersions(pinnedUrns));
    }

    @Override
    public List<String> exclusivelyOwnedMemberVersions(Collection<String> ownerPins) {
        ownerPins.forEach(UrnParser::requireNoControlChars);
        return ownerPins.isEmpty() ? List.of() : read(() -> references.exclusivelyOwnedMemberVersions(ownerPins));
    }

    @Override
    public void deleteVersions(String urn, Collection<String> deleted) {
        UrnParser.requireNoControlChars(urn);
        String logical = UrnParser.logicalUrn(urn);
        if (deleted.isEmpty()) {
            return;
        }
        try {
            inTxResult(() -> {
                lockArtifactWrite(logical);
                ArtifactRow artifact = artifacts.findByLogicalUrn(logical)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown artifact: " + logical));
                String current = versions.listVersions(artifact.id()).stream()
                    .filter(version -> !deleted.contains(version))
                    .max(SemVer::compare)
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Deleting every version of " + logical + " deletes the artifact"));
                versions.delete(artifact.id(), deleted);
                Instant now = Instant.now();
                artifacts.updateCurrentVersion(artifact.id(), current, now);
                // The namespace index addresses a version, so it follows the current one as a bump does.
                ArtifactVersionRow currentRow = versions.find(artifact.id(), current).orElseThrow();
                if (RegistryMapping.FORMAT_XSD.equals(currentRow.primaryFormat())) {
                    namespaces.repointVersion(artifact.id(), currentRow.id(), now);
                }
                xsd.evict(logical);
                log.info("Deleted {} v{}; current is v{}", logical, deleted, current);
                return null;
            });
        } catch (DataAccessException e) {
            throw translate(e, "Could not delete versions of " + logical);
        }
    }

    @Override
    public List<String> blockingDependents(String urn) {
        UrnParser.requireNoControlChars(urn);
        String logical = UrnParser.logicalUrn(urn);
        try {
            return references.blockingDependents(logical);
        } catch (DataAccessException e) {
            throw translate(e, "Could not read dependents of " + logical);
        }
    }

    @Override
    public List<String> nonDataSetBlockingDependents(String urn) {
        return nonDataSetBlockingDependents(urn, null);
    }

    @Override
    public List<String> nonDataSetBlockingDependents(String urn, String targetVersion) {
        UrnParser.requireNoControlChars(urn);
        String logical = UrnParser.logicalUrn(urn);
        try {
            return references.nonDataSetBlockingDependents(logical, pinnedVersion(targetVersion));
        } catch (DataAccessException e) {
            throw translate(e, "Could not read dependents of " + logical);
        }
    }

    @Override
    public List<String> versionDeletionBlockers(Collection<String> removedPins, Collection<String> siblingUrns) {
        removedPins.forEach(UrnParser::requireNoControlChars);
        siblingUrns.forEach(UrnParser::requireNoControlChars);
        return removedPins.isEmpty() ? List.of()
            : read(() -> references.versionDeletionBlockers(removedPins, siblingUrns));
    }

    @Override
    public List<String> dataSetMemberships(String urn) {
        UrnParser.requireNoControlChars(urn);
        String logical = UrnParser.logicalUrn(urn);
        try {
            return references.dataSetMemberships(logical);
        } catch (DataAccessException e) {
            throw translate(e, "Could not read DataSet memberships of " + logical);
        }
    }

    /**
     * The concrete version a caller named, or null for the {@code latest} token — which follows
     * whichever version is current and so constrains the artifact as a whole. Stored reference URNs
     * carry the token verbatim, so it reaches these queries as if it were a SemVer.
     */
    private static String pinnedVersion(String version) {
        return UrnParser.LATEST.equals(version) ? null : version;
    }

    @Override
    public List<String> ownedMemberUrns(String urn) {
        return ownedMemberUrns(urn, null);
    }

    @Override
    public List<String> ownedMemberUrns(String urn, String ownerVersion) {
        UrnParser.requireNoControlChars(urn);
        String logical = UrnParser.logicalUrn(urn);
        try {
            return references.ownedMemberUrns(logical, pinnedVersion(ownerVersion));
        } catch (DataAccessException e) {
            throw translate(e, "Could not read owned members of " + logical);
        }
    }

    @Override
    public List<String> dataStructureMembersOfAnyVersion(String urn) {
        UrnParser.requireNoControlChars(urn);
        String logical = UrnParser.logicalUrn(urn);
        try {
            return references.dataStructureMembersOfAnyVersion(logical);
        } catch (DataAccessException e) {
            throw translate(e, "Could not read the members of " + logical);
        }
    }

    @Override
    public List<String> dataStructuresListing(String elementUrn) {
        UrnParser.requireNoControlChars(elementUrn);
        String logical = UrnParser.logicalUrn(elementUrn);
        try {
            return references.dataStructuresListing(logical);
        } catch (DataAccessException e) {
            throw translate(e, "Could not read the Data Structures listing " + logical);
        }
    }

    @Override
    public Set<String> dataStructureMemberKeys(String dataStructureUrn) {
        UrnParser.requireNoControlChars(dataStructureUrn);
        String logical = UrnParser.logicalUrn(dataStructureUrn);
        try {
            return new LinkedHashSet<>(representations.dataStructureMemberKeys(logical));
        } catch (DataAccessException e) {
            throw translate(e, "Could not read the member keys of " + logical);
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    public Optional<JsonNode> fetch(String urn) {
        return read(() -> resolveVersion(urn)
            .flatMap(this::authoredRepresentation)
            .map(ArtifactRepresentationRow::contentJson)
            .filter(json -> json != null && !json.isBlank())
            .map(this::readJson));
    }

    @Override
    public List<String> fetchArtifactRefUrns(String urn) {
        return read(() -> resolveVersion(urn)
            .map(v -> references.targetUrns(v.id()))
            .orElseGet(List::of));
    }

    @Override
    public Map<String, List<String>> referencesByType(String urn) {
        return read(() -> resolveVersion(urn)
            .map(v -> {
                // rowsForVersion is document-ordered (sort_order); insertion order of both the
                // type keys and the per-type target lists preserves it.
                Map<String, List<String>> byType = new LinkedHashMap<>();
                for (ReferenceRow row : references.rowsForVersion(v.id())) {
                    byType.computeIfAbsent(row.referenceType(), t -> new ArrayList<>())
                        .add(row.targetUrn());
                }
                return byType;
            })
            .orElseGet(LinkedHashMap::new));
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @Override
    public Map<String, List<String>> referenceEdgesByVersion() {
        return read(references::edgesByVersion);
    }

    // ── Versioning ────────────────────────────────────────────────────────────

    // ── Search (M4) ──────────────────────────────────────────────────────────────

    /**
     * Upper bound on the structural match set a free-text ({@code q}) search loads into memory
     * for relevance scoring. Bounds the fetch so a {@code ?q=} query scales with this cap, not
     * with table size; ranking is therefore computed over (at most) the first {@code SCAN_CAP}
     * structural matches by name.
     */
    private static final int SEARCH_SCAN_CAP = 5_000;


    @Override
    public List<ArtifactSearchResult> searchArtifacts(ArtifactSearchCriteria criteria) {
        // Structural filters (type/format/property/ref) run in SQL; a free-text q is
        // relevance-scored in Java (NameMatch) so exact > prefix > substring and optional fuzzy
        // matching are preserved — one search surface for every artifact type. Only the supplied
        // criteria add a clause (avoids null-parameter type issues, keeps the query minimal).
        StringBuilder sql = new StringBuilder("""
            select a.logical_urn, av.version, a.artifact_type,
                   coalesce(av.title, a.title, a.name) as title, av.primary_format
              from model_forge.artifact a
              join model_forge.artifact_version av on av.artifact_id = a.id and av.version = a.current_version
              join model_forge.artifact_representation ar on ar.version_id = av.id and ar.format = av.primary_format
             where 1 = 1
            """);
        Map<String, Object> params = new HashMap<>();
        if (notBlank(criteria.type())) {
            sql.append(" and a.artifact_type = :type");
            params.put("type", criteria.type().trim().toLowerCase(Locale.ROOT));
        }
        if (notBlank(criteria.format())) {
            String fmt = normalizeFormat(criteria.format());
            if (RegistryMapping.FORMAT_JSONSCHEMA.equals(fmt)) {
                // JSON Schema is available for native jsonschema versions AND derivable from XSD,
                // so format=json-schema matches both stored and derivable representations.
                sql.append(" and av.primary_format in (:fmtJson, :fmtXsd)");
                params.put("fmtJson", RegistryMapping.FORMAT_JSONSCHEMA);
                params.put("fmtXsd", RegistryMapping.FORMAT_XSD);
            } else {
                sql.append(" and av.primary_format = :format");
                params.put("format", fmt);
            }
        }
        if (notBlank(criteria.property())) {
            sql.append(" and jsonb_exists(ar.content_jsonb -> 'properties', :property)");
            params.put("property", criteria.property().trim());
        }
        if (notBlank(criteria.ref())) {
            sql.append(" and exists (select 1 from model_forge.artifact_reference r"
                + " where r.from_version_id = av.id and r.target_urn = :ref)");
            params.put("ref", criteria.ref().trim());
        }

        boolean scored = notBlank(criteria.q());
        // The caller owns the [1, MAX] clamp; guard only against negatives.
        int limit = Math.max(0, criteria.limit());
        int offset = Math.max(0, criteria.offset());
        // a.name is the display name and is NOT unique (only logical_urn is); append the
        // unique key as a tiebreak so offset/limit paging is stable (no skipped/duplicated rows).
        if (!scored) {
            // No free-text term: order and page in SQL.
            sql.append(" order by a.name asc, a.logical_urn asc limit :limit offset :offset");
            params.put("limit", limit);
            params.put("offset", offset);
        } else {
            // Free-text q is scored in memory; bound the scan so a ?q= query cannot materialise an
            // unbounded set — relevance ranking is computed over (at most) the first SCAN_CAP
            // structural matches by name.
            sql.append(" order by a.name asc, a.logical_urn asc limit :scanCap");
            params.put("scanCap", SEARCH_SCAN_CAP);
        }

        List<ArtifactSearchResult> rows = read(() ->
            jdbc.sql(sql.toString()).params(params).query((rs, n) -> new ArtifactSearchResult(
                UrnParser.withVersion(rs.getString("logical_urn"), rs.getString("version")),
                rs.getString("logical_urn"),
                rs.getString("artifact_type"),
                rs.getString("title"),
                rs.getString("version"),
                rs.getString("primary_format"),
                0)).list());
        if (!scored) {
            return rows;
        }
        if (rows.size() >= SEARCH_SCAN_CAP) {
            // The scan cap was hit: relevance ranking is computed over a truncated candidate set,
            // so a high-relevance match beyond it is not scored. No longer silent — the API contract
            // documents this; narrow the structural filters (type/format) for completeness.
            log.warn("Search q hit the {}-row scan cap; relevance ranking is over a truncated candidate set",
                SEARCH_SCAN_CAP);
        }
        // Relevance-score the structural matches against q (title, which falls back to the display
        // name), keep the matches, rank by score (ties by title) and page in memory.
        String q = criteria.q().trim();
        return rows.stream()
            .map(r -> withScore(r, NameMatch.score(r.title(), q, criteria.fuzzy())))
            .filter(r -> r.score() > 0)
            .sorted(Comparator.comparingInt(ArtifactSearchResult::score).reversed()
                .thenComparing(ArtifactSearchResult::title))
            .skip(offset)
            .limit(limit)
            .toList();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Returns a copy of {@code r} with its relevance {@code score} set. */
    private static ArtifactSearchResult withScore(ArtifactSearchResult r, int score) {
        return new ArtifactSearchResult(r.id(), r.logicalId(), r.type(), r.title(), r.version(),
            r.format(), score);
    }

    @Override
    public List<Map<String, String>> mappingsForElement(String dsUrn, String role) {
        // role=source → this Element is a mapping's SOURCE, so each hit pairs the mapping with
        // its target; role=target → it is the TARGET, paired with the mapping's source.
        boolean asSource = "source".equalsIgnoreCase(role);
        String matchType = asSource ? ReferenceExtraction.MAPPING_SOURCE : ReferenceExtraction.MAPPING_TARGET;
        String otherType = asSource ? ReferenceExtraction.MAPPING_TARGET : ReferenceExtraction.MAPPING_SOURCE;
        String otherKey  = asSource ? "target" : "source";
        return read(() -> artifacts.findByLogicalUrn(UrnParser.logicalUrn(dsUrn))
            .map(ds -> references.mappingsByRole(ds.id(), matchType, otherType).stream()
                .map(r -> mappingRoleEntry(r, otherKey))
                .toList())
            .orElseGet(List::of));
    }

    private static Map<String, String> mappingRoleEntry(
            ArtifactReferenceRepository.MappingRoleRef r, String otherKey) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("mapping", r.mappingUrn());
        if (r.otherUrn() != null) entry.put(otherKey, r.otherUrn());
        return entry;
    }

    // ── Formats / cross-format ──────────────────────────────────────────────────

    @Override
    public Optional<JsonNode> fetchElementOrXsd(String urn) {
        return read(() -> resolveVersion(urn).flatMap(v -> {
            Optional<JsonNode> json = representations.find(v.id(), RegistryMapping.FORMAT_JSONSCHEMA)
                .map(ArtifactRepresentationRow::contentJson)
                .filter(c -> c != null && !c.isBlank())
                .map(this::readJson);
            if (json.isPresent()) return json;
            Optional<String> xsdText = representations.find(v.id(), RegistryMapping.FORMAT_XSD)
                .map(ArtifactRepresentationRow::contentText)
                .filter(c -> c != null && !c.isBlank());
            // Key the conversion cache by the concrete resolved version (not the possibly
            // logical/:latest request URN) so a multi-version XSD never returns another version.
            if (xsdText.isPresent()) {
                String versioned = UrnParser.withVersion(UrnParser.logicalUrn(urn), v.version());
                String title = artifacts.findByLogicalUrn(UrnParser.logicalUrn(urn))
                    .map(ArtifactRow::name).orElse(null);
                return xsd.toJsonSchema(versioned, title, () -> xsdText);
            }
            // core-json artifacts (mappings, pipelines, …): return their authored JSON as-is.
            return authoredRepresentation(v)
                .map(ArtifactRepresentationRow::contentJson)
                .filter(c -> c != null && !c.isBlank())
                .map(this::readJson);
        }));
    }

    @Override
    public void rebuildNamespaceIndex() {
        xsd.clearCache();
        try {
            List<String> xsdUrns = artifacts.listLogicalUrnsWithRepresentationFormat(RegistryMapping.TYPE_ELEMENT, RegistryMapping.FORMAT_XSD);
            // Rebuild atomically in one transaction: clear the whole index first so namespaces
            // that no longer exist (a removed/changed targetNamespace) cannot linger, then
            // re-index every current XSD. A single transaction avoids a window where a lookup
            // would see a half-rebuilt (or empty) index.
            int indexed = inTxResult(() -> {
                namespaces.deleteAll();
                int count = 0;
                for (String urn : xsdUrns) {
                    Optional<ResolvedXsd> resolved = resolveXsd(urn);
                    if (resolved.isEmpty()) continue;
                    ResolvedXsd r = resolved.get();
                    Optional<String> ns = xsd.extractTargetNamespace(r.content());
                    if (ns.isPresent()) {
                        namespaces.upsert(ns.get(), r.artifactId(), r.versionId(), Instant.now());
                        count++;
                    }
                }
                return count;
            });
            log.info("XSD namespace index reconciled: {} namespace(s)", indexed);
        } catch (DataAccessException e) {
            throw translate(e, "Could not rebuild the XSD namespace index");
        }
    }

    private Optional<String> findXsdByNamespace(String ns) {
        return read(() -> namespaces.findLogicalUrnByNamespace(ns));
    }

    @Override
    public Set<String> extractImportRefs(String xsdContent) {
        return xsd.extractImportRefs(xsdContent, this::findXsdByNamespace);
    }

    /**
     * How many artifacts one bulk-existence statement asks about. A collection parameter expands to
     * one bind per element and the PostgreSQL wire protocol caps a statement at 65535 binds, so a
     * large input is answered in fixed-size chunks. Fixed rather than input-sized, so full chunks
     * reuse one prepared statement.
     */
    private static final int EXISTENCE_BATCH_SIZE = 500;

    @Override
    public Set<String> heldUrns(Collection<String> urns) {
        if (urns == null || urns.isEmpty()) {
            return Set.of();
        }
        // Keyed by the caller's own spelling, so a repeated URN does not consume a bind slot twice.
        List<String> requested = urns.stream()
            .filter(urn -> urn != null && !urn.isBlank())
            .distinct()
            .toList();
        if (requested.isEmpty()) {
            return Set.of();
        }
        // One read for the whole answer: a chunk failing mid-way must not surface as a smaller
        // held set, which would read as "these are missing".
        return read(() -> {
            Map<String, ArtifactVersions> known = new HashMap<>();
            List<String> logicals = requested.stream().map(UrnParser::logicalUrn).distinct().toList();
            for (int from = 0; from < logicals.size(); from += EXISTENCE_BATCH_SIZE) {
                List<String> batch =
                    logicals.subList(from, Math.min(from + EXISTENCE_BATCH_SIZE, logicals.size()));
                for (ArtifactVersionIdentity row : artifacts.findVersionIdentities(batch)) {
                    known
                        .computeIfAbsent(row.logicalUrn(), urn -> new ArtifactVersions(row.currentVersion()))
                        .versions()
                        .add(row.version());
                }
            }
            Set<String> held = new LinkedHashSet<>();
            for (String urn : requested) {
                if (isHeld(urn, known.get(UrnParser.logicalUrn(urn)))) {
                    held.add(urn);
                }
            }
            return held;
        });
    }

    /**
     * The same decision {@link #resolveVersion} makes, against already-read rows: a pinned URN needs
     * that version, a logical or {@code :latest} URN needs the artifact to have a current one.
     */
    private static boolean isHeld(String urn, ArtifactVersions stored) {
        if (stored == null) {
            return false;
        }
        String version = UrnParser.versionFromUrn(urn);
        String target = (version == null || UrnParser.LATEST.equals(version))
            ? stored.currentVersion() : version;
        return target != null && stored.versions().contains(target);
    }

    /** The versions one artifact holds, and the one a version-free reference to it resolves to. */
    private record ArtifactVersions(String currentVersion, Set<String> versions) {
        ArtifactVersions(String currentVersion) {
            this(currentVersion, new HashSet<>());
        }
    }

    @Override
    public Optional<String> resolveReference(String refUrn) {
        // The concrete versioned target URN a reference currently points at: a pinned reference
        // resolves to itself (when that version exists); a :latest or logical reference resolves
        // to the target's current (highest-SemVer) version. Empty for an unresolvable pin (dangling).
        return read(() -> resolveVersion(refUrn)
            .map(v -> UrnParser.withVersion(UrnParser.logicalUrn(refUrn), v.version())));
    }

    /** Maps a public format hint (json-schema | json | xsd) onto the stored representation format. */
    private static String normalizeFormat(String requested) {
        return switch (requested.trim().toLowerCase(Locale.ROOT)) {
            case "xsd"                               -> RegistryMapping.FORMAT_XSD;
            case "json-schema", "json", "jsonschema" -> RegistryMapping.FORMAT_JSONSCHEMA;
            default                                  -> requested.trim().toLowerCase(Locale.ROOT);
        };
    }

    // ── Internals ───────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface PostInsert { void run(UUID artifactId, UUID versionId, Instant now); }

    /**
     * The single transactional write path. Upserts the model_forge.artifact, inserts a new version
     * (unless the incoming authored content is byte-identical to the compared version's
     * representation in the same format — idempotent; the compared version is the latest one in
     * the named base's major for a non-MAJOR bump with a base, otherwise the current version),
     * stores the version's authored representation, replaces its reference edges, advances the
     * current-version pointer, and runs an optional post-insert hook (e.g. the XSD namespace upsert).
     *
     * @param explicitVersion when non-blank, stored verbatim instead of MF's usual version
     *     authority (see the class documentation) —
     *     the escape hatch for imports (XÖV/XSD) that must preserve an upstream version identity.
     *     Accepted as given, with no ordering check against the current version; a version that
     *     collides with an existing one for this artifact surfaces as the usual integrity-violation
     *     error rather than being silently coerced.
     * @return the concrete versioned URN (pin) the write resolved to: the newly assigned version,
     *     or the compared version on an idempotent write. The version is
     *     assigned inside this write, so the pin needs no read-back — it is correct even when the
     *     surrounding host transaction has not committed yet.
     */
    private String writeArtifact(String urn, String artifactType, String format, String contentType,
                                 String contentJson, String contentText, List<ReferenceRow> refs,
                                 VersionBump bump, String explicitVersion, String bumpFromVersion,
                                 PostInsert postInsert) {
        // Reject control characters (CR/LF, etc.) in the client-supplied URN before it is persisted
        // or written to a plain-text log line — closes a CWE-117 log-forging/injection vector.
        UrnParser.requireNoControlChars(urn);
        // Legacy ':xsd:' identity URNs are no longer minted; reject any client-supplied one
        // (JSON $id, XSD artifactId, or a PUT id) so no new legacy identities can enter the store.
        if ("xsd".equals(UrnParser.artifactTypeFromUrn(urn))) {
            throw new IllegalArgumentException(
                "Legacy ':xsd:' URNs are no longer accepted. Elements are format-agnostic; "
                + "use a ':element:' URN (the format is recorded as a stored representation).");
        }
        // ':latest' is a reference token, not a writable identity — artifacts are always authored
        // at a concrete SemVer version.
        if (UrnParser.isLatest(urn)) {
            throw new IllegalArgumentException(
                "':latest' is a reference token, not a writable version. "
                + "Author a concrete version (e.g. ':1.0.0').");
        }
        UrnParser.requireUrn(urn, "Artifact id");
        String logical   = UrnParser.logicalUrn(urn);
        String title     = titleOf(contentJson);
        String canonical = contentText != null ? contentText : contentJson;
        String hash      = sha256(canonical);
        try {
            return inTxResult(() -> {
                // Serialise all writers for this logical URN (create + update, across instances)
                // for the rest of the transaction, so concurrent writes bump SemVer sequentially
                // instead of reading the same current_version and colliding on the unique
                // constraint — which would otherwise surface as a misleading 502.
                lockArtifactWrite(logical);
                Optional<ArtifactRow> existing = artifacts.findByLogicalUrn(logical);
                Instant now = Instant.now();
                UUID artifactId;
                String newVersion;
                boolean hasExplicitVersion = explicitVersion != null && !explicitVersion.isBlank();
                if (existing.isEmpty()) {
                    artifactId = UUID.randomUUID();
                    // An artifact recreated under a deleted one's URN continues after its numbers.
                    newVersion = hasExplicitVersion ? explicitVersion : highWater.find(logical)
                        .map(highest -> SemVer.next(highest, VersionBump.MAJOR))
                        .orElse(SemVer.INITIAL);
                    // The URN carries no name; until a title or nameArtifact supplies one, the
                    // UUID stands in, because the column is the sort key of every listing.
                    String name = title != null ? title : UrnParser.idFromUrn(logical).toString();
                    artifacts.insert(new ArtifactRow(artifactId, logical, artifactType, name,
                        null, null, newVersion, now, now));
                    // A reference to this URN may already have been recorded before the
                    // target was imported — resolve those dangling edges now.
                    references.linkDanglingTargets(logical, artifactId);
                } else {
                    ArtifactRow row = existing.get();
                    artifactId = row.id();
                    if (title != null && !title.equals(row.name())) {
                        artifacts.updateName(logical, title);
                    }
                    boolean revisesBase = bump != VersionBump.MAJOR
                        && bumpFromVersion != null && !bumpFromVersion.isBlank();
                    String latest = revisesBase
                        ? latestInLine(artifactId, bumpFromVersion) : row.currentVersion();
                    Optional<ArtifactVersionRow> current = latest == null
                        ? Optional.empty() : versions.find(artifactId, latest);
                    // Idempotent only when the authored content of the latest version, in the
                    // SAME format, is byte-identical. A different format is always a new version.
                    if (current.isPresent() && format.equals(current.get().primaryFormat())) {
                        Optional<ArtifactRepresentationRow> rep =
                            representations.find(current.get().id(), format);
                        if (rep.isPresent() && hash.equals(rep.get().contentHash())) {
                            // Identical authored content — no new version; the pin is the existing one.
                            return UrnParser.withVersion(logical, latest);
                        }
                    }
                    // An explicit version is adopted verbatim, with no ordering check against the
                    // current version — a version that collides with an existing one for this
                    // artifact surfaces as the usual integrity-violation error on insert below,
                    // and takes precedence over a named base.
                    // A named base is the version being revised, so an artifact whose versions form
                    // independent lines keeps the bump inside the revised line. A major opens a new
                    // line, which no base can place below the highest.
                    String base = revisesBase ? bumpFromVersion : highestAssigned(logical, row.currentVersion());
                    newVersion = hasExplicitVersion ? explicitVersion : SemVer.next(base, bump);
                }
                UUID versionId = UUID.randomUUID();
                insertVersion(logical, new ArtifactVersionRow(versionId, artifactId, newVersion, format,
                    null, null, now, null));
                representations.insert(new ArtifactRepresentationRow(UUID.randomUUID(), versionId, format,
                    contentType, contentJson, contentText, hash, "stored", now));
                references.replaceForVersion(versionId, refs);
                // current_version is the HIGHEST version, not the last written: logical and
                // ':latest' reads resolve through it, and a bump with no named base counts from it.
                // Revising an older version must therefore not pull it back.
                if (existing.isEmpty() || SemVer.compare(newVersion, existing.get().currentVersion()) > 0) {
                    artifacts.updateCurrentVersion(artifactId, newVersion, now);
                }
                // Back-fill pinned references that targeted this exact (logical, version) before it existed.
                references.linkDanglingVersions(UrnParser.withVersion(logical, newVersion), versionId);
                if (postInsert != null) postInsert.run(artifactId, versionId, now);
                log.info("Stored {} v{} ({}, {} ref(s))", logical, newVersion, format, refs.size());
                return UrnParser.withVersion(logical, newVersion);
            });
        } catch (DataAccessException e) {
            throw translate(e, "Registry store failed for " + logical);
        }
    }

    /** Inserts the version row and marks its number as taken for good. */
    private void insertVersion(String logicalUrn, ArtifactVersionRow version) {
        versions.insert(version);
        highWater.raise(logicalUrn, version.version());
    }

    /** The highest version in the major of {@code base}, or null when that line holds none. */
    private String latestInLine(UUID artifactId, String base) {
        return versions.listVersions(artifactId).stream()
            .filter(version -> UrnParser.sameMajor(version, base))
            .max(SemVer::compare)
            .orElse(null);
    }

    /** The highest version the artifact ever had, deleted ones included. */
    private String highestAssigned(String logicalUrn, String currentVersion) {
        return SemVer.higher(currentVersion, highWater.find(logicalUrn).orElse(null));
    }

    /**
     * Takes a transaction-scoped PostgreSQL advisory lock keyed on the logical URN. Serialises
     * concurrent writers for the same artifact (across application instances, unlike a JVM lock)
     * and releases automatically at transaction end. Distinct URNs may share a hash bucket — that
     * only causes brief, harmless false contention, never an incorrect result.
     */
    private void lockArtifactWrite(String logicalUrn) {
        jdbc.sql("select true from (select pg_advisory_xact_lock(hashtext(:urn))) locked")
            .param("urn", logicalUrn)
            .query(Boolean.class)
            .optional();
    }

    @Override
    public void lockForWrite(String logicalUrn) {
        UrnParser.requireNoControlChars(logicalUrn);
        String logical = UrnParser.logicalUrn(logicalUrn);
        try {
            lockArtifactWrite(logical);
        } catch (DataAccessException e) {
            throw translate(e, "Could not lock " + logical);
        }
    }

    private record ResolvedXsd(UUID artifactId, UUID versionId, String content) {}

    /**
     * Resolve a (versioned, logical or {@code :latest}) URN to its target version row.
     * {@code :latest} and a missing version segment both read the model_forge.artifact's
     * {@code current_version} — the highest-SemVer version, since versioning is monotonic.
     */
    private Optional<ArtifactVersionRow> resolveVersion(String urn) {
        String logical = UrnParser.logicalUrn(urn);
        return artifacts.findByLogicalUrn(logical).flatMap(a -> {
            String version = UrnParser.versionFromUrn(urn);
            String target  = (version == null || UrnParser.LATEST.equals(version))
                ? a.currentVersion() : version;
            return target == null ? Optional.empty() : versions.find(a.id(), target);
        });
    }

    /** The authored ({@code primary_format}) representation of a version. */
    private Optional<ArtifactRepresentationRow> authoredRepresentation(ArtifactVersionRow v) {
        return representations.find(v.id(), v.primaryFormat());
    }

    private Optional<ResolvedXsd> resolveXsd(String urn) {
        return resolveVersion(urn).flatMap(v ->
            representations.find(v.id(), RegistryMapping.FORMAT_XSD)
                .map(ArtifactRepresentationRow::contentText)
                .filter(text -> text != null && !text.isBlank())
                .map(text -> new ResolvedXsd(v.artifactId(), v.id(), text)));
    }

    @Override
    public void nameArtifact(String urn, String name) {
        UrnParser.requireNoControlChars(urn);
        if (name == null || name.isBlank()) return;
        try {
            inTxResult(() -> {
                artifacts.updateName(UrnParser.logicalUrn(urn), name);
                return null;
            });
        } catch (DataAccessException e) {
            throw translate(e, "Could not name model_forge.artifact " + UrnParser.logicalUrn(urn));
        }
    }

    @Override
    public Map<String, String> displayNames(Collection<String> logicalUrns) {
        if (logicalUrns == null || logicalUrns.isEmpty()) return Map.of();
        return read(() -> artifacts.namesByLogicalUrn(Set.copyOf(logicalUrns)));
    }

    // ── URN / content helpers ───────────────────────────────────────────────────

    private String schemaUrn(JsonNode schema) {
        String id = schema.path("$id").asText(null);
        return UrnParser.isUrn(id) ? id : urns.mintElement();
    }

    /** The display name a write carries: the JSON document's title. */
    private String titleOf(String contentJson) {
        return contentJson == null ? null : JsonSchema.usableTitle(readJson(contentJson));
    }

    private String writeJson(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise model_forge.artifact content", e);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RegistryUnavailableException("Registry returned unparseable content", e);
        }
    }

    private static String sha256(String content) {
        if (content == null) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                                    .append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e); // never happens on a JRE
        }
    }

    private <T> T inTxResult(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }

    /**
     * Spans one transaction across several writes. The per-write {@link #inTxResult} calls inside
     * {@code work} use the default {@code REQUIRED} propagation, so they join this transaction
     * instead of committing individually — which is what makes a multi-write import atomic.
     */
    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return inTxResult(work);
    }

    @Override
    public void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // Spring rethrows from the first failing callback and skips the rest.
                try {
                    action.run();
                } catch (RuntimeException e) {
                    log.error("An after-commit action failed; the committed change stands, but this "
                        + "application's in-memory dependency graph is stale until it is rebuilt. Restart "
                        + "the application to rebuild it; in the Model Forge admin UI, opening the Graph "
                        + "page also rebuilds it", e);
                }
            }
        });
    }

    /**
     * Translates a Spring {@link DataAccessException} into the application exception that yields a
     * faithful HTTP status: a data-integrity violation (e.g. a unique/constraint breach) conflicts
     * with stored data → {@link RegistryConflictException} (409); a SQL grammar/programming error is
     * a server defect → {@link IllegalStateException} (logged 500); everything else (connectivity,
     * timeouts, pool exhaustion) is a transient outage -> {@link RegistryUnavailableException}. The
     * conflict message is safe and generic — never the raw DB text — so registry internals are not
     * leaked through it; the raw text stays in the cause for the server log.
     */
    private static RuntimeException translate(DataAccessException e, String context) {
        if (e instanceof DataIntegrityViolationException) {
            return new RegistryConflictException("The request conflicts with existing registry data", e);
        }
        if (e instanceof BadSqlGrammarException) {
            return new IllegalStateException(context, e);
        }
        return new RegistryUnavailableException(context, e);
    }

    /** Run a read query, translating storage faults via {@link #translate} (outages → 502). */
    private <T> T read(Supplier<T> query) {
        try {
            return query.get();
        } catch (DataAccessException e) {
            throw translate(e, "Artifact registry is unavailable");
        }
    }
}
