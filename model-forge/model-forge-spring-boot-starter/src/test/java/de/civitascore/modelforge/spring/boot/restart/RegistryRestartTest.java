package de.civitascore.modelforge.spring.boot.restart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the registry accepted is still there after the application is genuinely stopped and started.
 *
 * <p>The application runs as a separate process, twice, against one database that outlives both.
 * A second application context inside this JVM would not do: it would still see anything the first
 * left in memory, so it could pass while the state was never durable. Each run below is its own
 * process, so only what reached the database survives into the next one.
 *
 * <p>The second run also suppresses the startup graph rebuild. That rebuild re-derives the XSD
 * namespace index from the stored schemas, so a restart test that left it enabled would pass
 * whether or not the index is durable — it would be measuring the rescan, not the storage.
 */
@Testcontainers(disabledWithoutDocker = true)
class RegistryRestartTest {

    /**
     * Started once and deliberately never stopped between runs — it is the state that has to
     * outlive the application. Not annotated {@code @Container}, so the extension does not manage
     * (and stop) it around each test.
     */
    private static final PostgreSQLContainer POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer("postgres:15");
        POSTGRES.start();
    }

    @Test
    @DisplayName("An artifact written by one process resolves in the next, without a startup rescan")
    void artifactsSurviveAnActualRestart() throws Exception {
        ProbeRun write = run("write", false);
        assertThat(write.exitCode())
            .as("the writing run must succeed:%n%s", write.output())
            .isZero();
        assertThat(write.output()).contains(RestartProbe.WROTE_PREFIX);

        // A different process, the same database, and no startup rebuild to repopulate the index.
        ProbeRun verify = run("verify", true);

        assertThat(verify.exitCode())
            .as(
                "the artifact and its namespace must resolve in a freshly started process:%n%s",
                verify.output())
            .isZero();
        assertThat(verify.output()).contains("PROBE-RESOLVED");
    }

    @Test
    @DisplayName("A namespace never written does not resolve, so the check can actually fail")
    void verifyFailsWhenNothingWasWritten() throws Exception {
        // Guards the test above: run verify against an empty schema and confirm it reports failure.
        // Without this, a verify that trivially succeeded would look like durability.
        ProbeRun verify = runAgainst(freshDatabaseUrl(), "verify", true);

        assertThat(verify.exitCode())
            .as("verify must fail when the registry is empty:%n%s", verify.output())
            .isNotZero();
        assertThat(verify.output()).contains("resolved to nothing");
    }

    private ProbeRun run(String mode, boolean suppressWarmup) throws Exception {
        return runAgainst(POSTGRES.getJdbcUrl(), mode, suppressWarmup);
    }

    /** Launches the probe as its own JVM and returns its exit code with everything it printed. */
    private ProbeRun runAgainst(String jdbcUrl, String mode, boolean suppressWarmup) throws Exception {
        List<String> command = new ArrayList<>(List.of(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", System.getProperty("java.class.path"),
            RestartProbe.class.getName(),
            mode,
            "--spring.datasource.url=" + jdbcUrl,
            "--spring.datasource.username=" + POSTGRES.getUsername(),
            "--spring.datasource.password=" + POSTGRES.getPassword(),
            "--spring.main.banner-mode=off",
            "--logging.level.root=WARN"));
        if (suppressWarmup) {
            command.add("--spring.profiles.active=no-warmup");
        }

        File log = Files.createTempFile("model-forge-restart-probe", ".log").toFile();
        Process process = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(log)
            .start();
        boolean finished = process.waitFor(3, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new AssertionError("the probe process did not finish within three minutes");
        }
        String output = Files.readString(log.toPath());
        Files.deleteIfExists(log.toPath());
        return new ProbeRun(process.exitValue(), output);
    }

    /** A separate, empty database on the same server, for the negative control. */
    private String freshDatabaseUrl() throws Exception {
        String name = "empty_" + System.nanoTime();
        try (var connection = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("create database " + name);
        }
        return POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
    }

    private record ProbeRun(int exitCode, String output) {}
}
