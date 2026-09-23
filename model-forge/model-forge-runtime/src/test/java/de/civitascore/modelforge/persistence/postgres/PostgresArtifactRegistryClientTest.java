package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.core.port.ArtifactSearchCriteria;
import de.civitascore.modelforge.contract.RegistryUnavailableException;
import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.core.port.XsdSchemaConverter;
import de.civitascore.modelforge.urn.UrnService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * No-Docker slice test for the registry's failure-translation contract: a storage outage
 * (a Spring {@link org.springframework.dao.DataAccessException}) must become a 502
 * {@link RegistryUnavailableException} on every code path — a read (never a false "not found"), a search
 * (never an empty result), and a transactional write. It pins the {@code read()} wrapper's and the
 * write path's error translation without a database, using generic/transient subtypes
 * (resource-failure / query-timeout) rather than the 4xx-mapped integrity/grammar ones.
 *
 * <p>The registry write path has no real-database test: the only Testcontainers test in the reactor
 * boots the admin-ui, and it does not cover this client's happy path.
 */
class PostgresArtifactRegistryClientTest {

    /** A search with no filters — every {@code searchArtifacts} call still issues the base query. */
    private static final ArtifactSearchCriteria ANY_SEARCH =
        new ArtifactSearchCriteria(null, false, null, null, null, null, 50, 0);

    private PostgresArtifactRegistryClient clientWithFailingDb() {
        return clientWithFailingDb(new DataAccessResourceFailureException("registry down"));
    }

    /**
     * A client whose every {@code jdbc.sql(...)} call throws the given storage failure.
     *
     * <p>The transaction manager is a plain mock: {@link org.springframework.transaction.support.TransactionTemplate}
     * obtains a (null) status, runs the callback, and on a thrown {@link DataAccessException} asks the
     * manager to roll back and rethrows it — so the write path's {@code catch (DataAccessException)}
     * still sees the original failure and translates it.
     */
    private PostgresArtifactRegistryClient clientWithFailingDb(DataAccessException failure) {
        JdbcClient jdbc = mock(JdbcClient.class);
        when(jdbc.sql(anyString())).thenThrow(failure);
        return new PostgresArtifactRegistryClient(
            jdbc, mock(PlatformTransactionManager.class), new ObjectMapper(),
            mock(UrnService.class), mock(XsdSchemaConverter.class));
    }

    @Test
    void readDuringStorageOutage_surfacesAsRegistryUnavailableException() {
        var client = clientWithFailingDb();
        // referenceEdgesByVersion() and searchArtifacts() both run through the read() wrapper, which
        // must translate the DataAccessException to a 502 RegistryUnavailableException rather than
        // letting it leak or collapsing it into an empty/"not found" result. An empty result here
        // would build a graph with no edges, and a dependency check over one objects to nothing.
        assertThatThrownBy(client::referenceEdgesByVersion)
            .isInstanceOf(RegistryUnavailableException.class);
    }

    @Test
    void searchDuringStorageOutage_surfacesAsRegistryUnavailableException() {
        // A transient/generic storage failure on the search query must become a 502 RegistryUnavailableException,
        // never a misleading empty result set. QueryTimeoutException is a generic transient subtype
        // (not a 4xx-mapped DataIntegrityViolation/BadSqlGrammar).
        var client = clientWithFailingDb(new QueryTimeoutException("statement timed out"));
        assertThatThrownBy(() -> client.searchArtifacts(ANY_SEARCH))
            .isInstanceOf(RegistryUnavailableException.class);
    }

    @Test
    void writeDuringStorageOutage_surfacesAsRegistryUnavailableException() {
        // The transactional write path (here storeElement → writeArtifact) must likewise
        // translate a transient storage failure thrown inside the transaction into a 502
        // RegistryUnavailableException rather than letting the raw DataAccessException escape.
        var client = clientWithFailingDb(new QueryTimeoutException("statement timed out"));
        var schema = new ObjectMapper().createObjectNode()
            .put("$id", "urn:core:platform:civitas:element:common:Outage:kmbccayu3w:1.0.0")
            .put("type", "object");
        assertThatThrownBy(() -> client.storeElement("Outage", schema, Set.of()))
            .isInstanceOf(RegistryUnavailableException.class);
    }

    @Test
    void writeViolatingIntegrity_surfacesAsIllegalArgumentWithoutLeakingDbText() {
        // A DataIntegrityViolationException is the caller's fault → 400 IllegalArgumentException.
        // The message must be the safe, generic text — never the raw DB error — so registry
        // internals (constraint names, SQL state, table layout) are not leaked to the client.
        String rawDbDetail = "duplicate key value violates unique constraint \"artifact_version_pkey\"";
        var client = clientWithFailingDb(new DataIntegrityViolationException(rawDbDetail));
        var schema = new ObjectMapper().createObjectNode()
            .put("$id", "urn:core:platform:civitas:element:common:Dup:sj4jrj0ynz:1.0.0")
            .put("type", "object");

        assertThatThrownBy(() -> client.storeElement("Dup", schema, Set.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("conflicts with existing registry data")
            .hasMessageNotContaining(rawDbDetail)
            .hasMessageNotContaining("constraint");
    }

    @Test
    void writeWithSqlGrammarError_surfacesAsIllegalState() {
        // A SQL grammar/programming error is a server defect → 500 IllegalStateException,
        // not a 4xx (caller can't fix it) and not a 502 (the database is up, the query is wrong).
        var grammar = new BadSqlGrammarException(
            "store", "insert into artifact_versionn ...",
            new SQLException("relation \"artifact_versionn\" does not exist", "42P01"));
        var client = clientWithFailingDb(grammar);
        var schema = new ObjectMapper().createObjectNode()
            .put("$id", "urn:core:platform:civitas:element:common:Grammar:j1qtfwb6ay:1.0.0")
            .put("type", "object");

        assertThatThrownBy(() -> client.storeElement("Grammar", schema, Set.of()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void versionBumpDuringStorageOutage_surfacesAsRegistryUnavailableException() {
        // bumpVersion opens its own transaction rather than going through writeArtifact, so it needs
        // the same translation: a transient storage failure inside it must become a 502, not escape raw.
        var client = clientWithFailingDb(new QueryTimeoutException("statement timed out"));
        assertThatThrownBy(() -> client.bumpVersion(
                "urn:core:platform:civitas:datastructure:common:Outage:kmbccayu3w", VersionBump.MINOR))
            .isInstanceOf(RegistryUnavailableException.class);
    }

    @Test
    void versionBumpViolatingIntegrity_surfacesAsIllegalArgumentWithoutLeakingDbText() {
        // Two bumps racing onto the same (artifact_id, version) is the caller's fault → 400, with the
        // safe generic message rather than the raw constraint text.
        String rawDbDetail = "duplicate key value violates unique constraint \"artifact_version_artifact_id_version_key\"";
        var client = clientWithFailingDb(new DataIntegrityViolationException(rawDbDetail));

        assertThatThrownBy(() -> client.bumpVersion(
                "urn:core:platform:civitas:datastructure:common:Dup:sj4jrj0ynz", VersionBump.MAJOR))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("conflicts with existing registry data")
            .hasMessageNotContaining(rawDbDetail);
    }
}
