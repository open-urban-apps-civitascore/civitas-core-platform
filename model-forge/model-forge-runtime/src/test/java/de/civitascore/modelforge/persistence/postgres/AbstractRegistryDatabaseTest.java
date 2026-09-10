package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.urn.UrnService;
import de.civitascore.modelforge.xsd.XsdToJsonSchemaConverter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives the real {@link PostgresArtifactRegistryClient} against a Testcontainers Postgres with the
 * production migration applied, so the write path is exercised rather than mocked.
 *
 * <p>No Spring context: the client's collaborators are constructed directly, which keeps these tests
 * about the registry contract instead of about autoconfiguration. The container is started once for
 * the JVM (not per class) and each test starts from empty tables.
 */
@Testcontainers(disabledWithoutDocker = true)
abstract class AbstractRegistryDatabaseTest {

    /** Mirrors the schema, history table and locations the Spring Boot starter configures. */
    private static final String SCHEMA = "model_forge";
    private static final String HISTORY_TABLE = "model_forge_schema_history";
    private static final String MIGRATIONS = "classpath:db/model-forge/migration";

    /** Every table the registry writes, in a fixed order so snapshots compare deterministically. */
    protected static final List<String> REGISTRY_TABLES = List.of(
        "artifact", "artifact_version", "artifact_representation", "artifact_reference", "xsd_namespace");

    private static final PostgreSQLContainer POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer("postgres:15").withReuse(true);
        POSTGRES.start();
    }

    protected ObjectMapper mapper;
    protected JdbcClient jdbc;
    protected UrnService urns;
    protected PostgresArtifactRegistryClient registry;

    @BeforeEach
    void openRegistry() {
        DataSource dataSource = dataSource();
        migrate(dataSource);
        mapper = new ObjectMapper();
        jdbc = JdbcClient.create(dataSource);
        urns = new UrnService("platform", "civitas", "common", "1.0.0");
        registry = new PostgresArtifactRegistryClient(
            jdbc,
            new DataSourceTransactionManager(dataSource),
            mapper,
            urns,
            new XsdToJsonSchemaConverter(mapper));
        truncateRegistry();
    }

    private static DataSource dataSource() {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }

    private static void migrate(DataSource dataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .schemas(SCHEMA)
            .defaultSchema(SCHEMA)
            .table(HISTORY_TABLE)
            .locations(MIGRATIONS)
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .load()
            .migrate();
    }

    private void truncateRegistry() {
        jdbc.sql("truncate table "
            + REGISTRY_TABLES.stream().map(t -> SCHEMA + "." + t).reduce((a, b) -> a + ", " + b).orElseThrow()
            + " cascade").update();
    }

    // ── Stored-state snapshots ───────────────────────────────────────────────────

    /**
     * Every row of every registry table, keyed by table name.
     *
     * <p>Row <em>contents</em> rather than counts, because the criteria this backs ("changes
     * nothing", "does not overwrite an earlier version") are violated by an in-place update that
     * leaves the count identical.
     */
    protected Map<String, List<Map<String, Object>>> snapshot() {
        var state = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : REGISTRY_TABLES) {
            state.put(table, rows(table));
        }
        return state;
    }

    /** Rows of one registry table in a stable order. */
    protected List<Map<String, Object>> rows(String table) {
        String orderBy = "xsd_namespace".equals(table) ? "namespace" : "id";
        return jdbc.sql("select * from " + SCHEMA + "." + table + " order by " + orderBy).query().listOfRows();
    }

    /** The authored content of one concrete version, as stored. */
    protected String storedContent(String logicalUrn, String version) {
        return jdbc.sql("""
                select coalesce(r.content_jsonb::text, r.content_text)
                  from model_forge.artifact_representation r
                  join model_forge.artifact_version v on v.id = r.version_id
                  join model_forge.artifact a on a.id = v.artifact_id
                 where a.logical_urn = :urn and v.version = :version and r.generation = 'stored'
                """)
            .param("urn", logicalUrn)
            .param("version", version)
            .query(String.class)
            .single();
    }

    /** Versions recorded for a logical artifact, newest last. */
    protected List<String> versionsOf(String logicalUrn) {
        return jdbc.sql("""
                select v.version
                  from model_forge.artifact_version v
                  join model_forge.artifact a on a.id = v.artifact_id
                 where a.logical_urn = :urn
                 order by v.created_at, v.version
                """)
            .param("urn", logicalUrn)
            .query(String.class)
            .list();
    }
}
