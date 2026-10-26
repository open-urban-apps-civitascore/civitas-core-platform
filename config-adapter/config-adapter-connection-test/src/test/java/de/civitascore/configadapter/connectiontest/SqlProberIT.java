/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connectiontest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Reason;
import de.civitascore.configadapter.testsupport.TestContainerImages;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** The SQL prober against a real PostgreSQL. */
class SqlProberIT {

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse(TestContainerImages.POSTGRES))
          .withDatabaseName("source")
          .withUsername("owner")
          .withPassword("owner-secret");

  static {
    POSTGRES.start();
  }

  /** Accepts every host: the container runs on the loopback interface the guard refuses. */
  private static final DestinationCheck ANY_HOST = host -> {};

  private final SqlProber prober = new SqlProber(ANY_HOST, Duration.ofSeconds(3));

  @BeforeAll
  static void createSource() throws SQLException {
    try (Connection owner = ownerConnection();
        Statement sql = owner.createStatement()) {
      sql.execute("CREATE TABLE readings (id int PRIMARY KEY, station text, value numeric)");
      sql.execute(
          "INSERT INTO readings SELECT i, 'station ' || i, i * 1.5 FROM generate_series(1, 10) i");
      sql.execute("CREATE SCHEMA \"Weather\"");
      sql.execute("CREATE TABLE \"Weather\".\"Daily\" (\"Day\" date, note text)");
      sql.execute(
          "INSERT INTO \"Weather\".\"Daily\" VALUES ('2026-10-01', 'dry, \"sunny\"'),"
              + " ('2026-10-02', E'line\\nbreak')");
      sql.execute("CREATE TABLE wide (id int, blob text)");
      // Five rows of 300,000 characters each, far more than a preview holds.
      sql.execute("INSERT INTO wide SELECT i, repeat('x', 300000) FROM generate_series(1, 5) i");
      sql.execute("CREATE TABLE notes (id int, note text)");
      sql.execute("INSERT INTO notes VALUES (1, NULL), (2, '')");
      sql.execute("CREATE TABLE audit (entry text)");
      sql.execute(
          "CREATE FUNCTION write_audit() RETURNS int LANGUAGE sql VOLATILE AS"
              + " $$ INSERT INTO audit VALUES ('written'); SELECT 1 $$");
      sql.execute("CREATE VIEW writing_view AS SELECT write_audit() AS result");
      sql.execute("CREATE VIEW slow_view AS SELECT 1 AS result FROM pg_sleep(30)");
      sql.execute("CREATE SEQUENCE counter");
      sql.execute("CREATE USER reader PASSWORD 'reader-secret'");
      sql.execute("GRANT SELECT ON readings TO reader");
    }
  }

  @Test
  void readsAtMostFiveRowsOfTheConfiguredColumnsAsCsv() throws Exception {
    ProbeResult result =
        probe(connector("readings", List.of("id", "station"), "reader", "reader-secret"));

    assertTrue(result.success(), result.message());
    assertEquals(
        "id,station\r\n1,station 1\r\n2,station 2\r\n3,station 3\r\n4,station 4\r\n5,station 5\r\n",
        result.preview());
    assertEquals("Connected and read 5 row(s) from public.readings.", result.message());
  }

  @Test
  void readsWideRowsAsTheirCutStartAndSaysThePreviewIsCut() throws Exception {
    ProbeResult result = probe(connector("wide", List.of("id", "blob"), "owner", "owner-secret"));

    assertTrue(result.success(), result.message());
    assertEquals(5, result.rowCount());
    assertTrue(result.truncated());
    // 8 KB of preview and a 1 KB margin for masking, however much the rows hold.
    assertTrue(result.preview().length() <= 9216, "kept " + result.preview().length());
    assertTrue(result.preview().startsWith("id,blob\r\n1,xxx"), result.preview().substring(0, 20));
  }

  @Test
  void namesTheQuotingWhenAMixedCaseColumnIsNotFound() {
    ConnectionTestFailure failure =
        assertThrows(
            ConnectionTestFailure.class,
            () ->
                probe(connector("\"Weather\".\"Daily\"", List.of("Day"), "owner", "owner-secret")));

    assertTrue(failure.getMessage().contains("write \"Day\""), failure.getMessage());
  }

  @Test
  void readsAllColumnsForAStarOrNoColumn() throws Exception {
    ProbeResult star = probe(connector("readings", List.of("*"), "reader", "reader-secret"));
    ProbeResult none = probe(connector("readings", List.of(), "reader", "reader-secret"));

    assertTrue(star.preview().startsWith("id,station,value\r\n1,station 1,1.5\r\n"));
    assertEquals(star.preview(), none.preview());
  }

  @Test
  void resolvesNamesAsTheSqlParserDoes() throws Exception {
    ProbeResult unquoted =
        probe(connector("PUBLIC.Readings", List.of("ID"), "owner", "owner-secret"));
    ProbeResult quoted =
        probe(
            connector(
                "\"Weather\".\"Daily\"", List.of("\"Day\"", "note"), "owner", "owner-secret"));

    assertTrue(unquoted.success(), unquoted.message());
    assertTrue(quoted.success(), quoted.message());
    assertEquals(
        "Day,note\r\n2026-10-01,\"dry, \"\"sunny\"\"\"\r\n2026-10-02,\"line\nbreak\"\r\n",
        quoted.preview());
  }

  @Test
  void tellsAnEmptyStringFromNull() throws Exception {
    ProbeResult result = probe(connector("notes", List.of(), "owner", "owner-secret"));

    assertEquals("id,note\r\n1,\r\n2,\"\"\r\n", result.preview());
  }

  @Test
  void appliesTheWhereClauseWhenTheRequestAllowsIt() throws Exception {
    Map<String, Object> connector = connector("readings", List.of("id"), "owner", "owner-secret");
    connector.put("where", "id > 8");

    ProbeResult result = probeWithWhere(connector);

    assertTrue(result.success(), result.message());
    assertEquals(2, result.rowCount());
    assertFalse(result.preview().contains("\r\n1\r\n"), result.preview());
    assertFalse(result.message().contains("where clause"), result.message());
  }

  @Test
  void ignoresTheWhereClauseWhenTheRequestDoesNotAllowIt() throws Exception {
    Map<String, Object> connector = connector("readings", List.of("id"), "owner", "owner-secret");
    connector.put("where", "id > 8 OR write_audit() = 1");

    ProbeResult result = probe(connector);

    assertTrue(result.success(), result.message());
    assertTrue(result.message().endsWith("The where clause is not part of the test."));
    assertTrue(result.preview().contains("\r\n1\r\n"), "the filter is not applied");
    assertEquals(0, auditEntries());
  }

  @Test
  void aWhereClauseThatWritesFailsInTheReadOnlyTransaction() throws Exception {
    Map<String, Object> connector = connector("readings", List.of("id"), "owner", "owner-secret");
    connector.put("where", "write_audit() = 1");

    ConnectionTestFailure failure =
        assertThrows(ConnectionTestFailure.class, () -> probeWithWhere(connector));

    assertTrue(failure.getMessage().contains("read-only transaction"), failure.getMessage());
    assertEquals(0, auditEntries());
  }

  @Test
  void aRowValueInTheDatabaseAnswerReachesTheUserButNotTheLog() {
    Map<String, Object> connector = connector("readings", List.of("id"), "owner", "owner-secret");
    connector.put("where", "id = 3 AND CAST(station AS int) > 0");

    ConnectionTestFailure failure =
        assertThrows(ConnectionTestFailure.class, () -> probeWithWhere(connector));

    assertEquals(
        "ERROR: invalid input syntax for type integer: \"station 3\"", failure.getMessage());
    assertEquals("The database answered with SQLSTATE 22P02.", failure.logText());
  }

  @Test
  void injectedTableOrColumnTextIsOnlyLookedUp() throws Exception {
    ConnectionTestFailure table =
        assertThrows(
            ConnectionTestFailure.class,
            () ->
                probe(
                    connector(
                        "readings; DROP TABLE readings", List.of("id"), "owner", "owner-secret")));
    ConnectionTestFailure column =
        assertThrows(
            ConnectionTestFailure.class,
            () ->
                probe(
                    connector(
                        "readings",
                        List.of("id FROM readings; DROP TABLE readings; --"),
                        "owner",
                        "owner-secret")));

    // PostgreSQL 16 answers a name that is not valid syntax with "not found" rather than an error
    assertTrue(table.getMessage().contains("does not exist"), table.getMessage());
    assertTrue(column.getMessage().contains("not a valid column name"), column.getMessage());
    assertEquals(10, rowCount("readings"));
  }

  @Test
  void aViewThatWritesFailsInTheReadOnlyTransaction() throws Exception {
    ConnectionTestFailure failure =
        assertThrows(
            ConnectionTestFailure.class,
            () -> probe(connector("writing_view", List.of(), "owner", "owner-secret")));

    assertTrue(failure.getMessage().contains("read-only transaction"), failure.getMessage());
    assertEquals(0, auditEntries());
  }

  @Test
  void reportsAMissingTableOrColumnOrANonTable() {
    ConnectionTestFailure table =
        assertThrows(
            ConnectionTestFailure.class,
            () -> probe(connector("nope", List.of(), "owner", "owner-secret")));
    ConnectionTestFailure column =
        assertThrows(
            ConnectionTestFailure.class,
            () -> probe(connector("readings", List.of("nope"), "owner", "owner-secret")));
    ConnectionTestFailure sequence =
        assertThrows(
            ConnectionTestFailure.class,
            () -> probe(connector("counter", List.of(), "owner", "owner-secret")));

    assertEquals(
        "The table 'nope' does not exist or is not visible to the user.", table.getMessage());
    assertEquals("The column 'nope' does not exist in public.readings.", column.getMessage());
    assertEquals("'counter' is not a table or view.", sequence.getMessage());
  }

  @Test
  void reportsTheDatabaseMessageForAWrongPasswordOrAMissingPrivilege() {
    ConnectionTestFailure password =
        assertThrows(
            ConnectionTestFailure.class,
            () -> probe(connector("readings", List.of(), "reader", "wrong-secret")));
    ConnectionTestFailure privilege =
        assertThrows(
            ConnectionTestFailure.class,
            () -> probe(connector("audit", List.of(), "reader", "reader-secret")));

    assertTrue(
        password.getMessage().contains("password authentication failed"), password.getMessage());
    assertFalse(password.getMessage().contains("wrong-secret"));
    assertTrue(privilege.getMessage().contains("permission denied"), privilege.getMessage());
  }

  @Test
  void namesARefusedConnectionWithoutTheDriverText() throws Exception {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    Map<String, Object> connector = connector("readings", List.of(), "reader", "reader-secret");
    connector.put("dsn", "postgres://localhost:" + closedPort + "/source");

    ConnectionTestFailure failure =
        assertThrows(ConnectionTestFailure.class, () -> probe(connector));

    assertEquals("The database did not answer: The connection was refused.", failure.getMessage());
  }

  @Test
  void aQueryThatHangsEndsAtTheClientTimeout() {
    long begin = System.nanoTime();

    ConnectionTestFailure failure =
        assertThrows(
            ConnectionTestFailure.class,
            () -> probe(connector("slow_view", List.of(), "owner", "owner-secret")));

    long seconds = Duration.ofNanos(System.nanoTime() - begin).toSeconds();
    assertTrue(seconds < 10, "ended after " + seconds + " s: " + failure.getMessage());
    assertEquals(Reason.TIMEOUT, failure.reason(), failure.getMessage());
  }

  @Test
  void anAbortFromOutsideEndsTheTestAndClosesTheConnection() throws Exception {
    Cancellation cancellation = new Cancellation();
    SqlProber patient = new SqlProber(ANY_HOST, Duration.ofSeconds(60));
    CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS).execute(cancellation::cancel);
    long begin = System.nanoTime();

    assertThrows(
        ConnectionTestFailure.class,
        () ->
            patient.probe(
                connector("slow_view", List.of(), "owner", "owner-secret"),
                null,
                false,
                cancellation));

    long seconds = Duration.ofNanos(System.nanoTime() - begin).toSeconds();
    assertTrue(seconds < 5, "ended after " + seconds + " s");
  }

  @Test
  void aDsnCannotLiftTheClientTimeouts() throws Exception {
    try (ServerSocket tarpit = new ServerSocket(0)) {
      // Accepts the connection and never answers, like a host behind a dropping firewall.
      Thread.ofVirtual()
          .start(
              () -> {
                try (Socket held = tarpit.accept()) {
                  held.getInputStream().transferTo(OutputStream.nullOutputStream());
                } catch (IOException e) {
                  // the test is over
                }
              });
      Map<String, Object> connector = connector("readings", List.of(), "owner", "owner-secret");
      connector.put(
          "dsn",
          "postgres://localhost:"
              + tarpit.getLocalPort()
              + "/source?connectTimeout=0&socketTimeout=0&loginTimeout=0");
      long begin = System.nanoTime();

      // Preemptive, so that a regression fails the test instead of hanging the build.
      ConnectionTestFailure failure =
          assertTimeoutPreemptively(
              Duration.ofSeconds(20),
              () -> assertThrows(ConnectionTestFailure.class, () -> probe(connector)));

      long seconds = Duration.ofNanos(System.nanoTime() - begin).toSeconds();
      assertTrue(seconds < 8, "ended after " + seconds + " s");
      assertEquals(Reason.TIMEOUT, failure.reason(), failure.getMessage());
    }
  }

  @Test
  void aRefusedDestinationIsNeverContacted() throws Exception {
    try (ServerSocketChannel listener = loopbackListener()) {
      Map<String, Object> connector = connector("readings", List.of(), "owner", "owner-secret");
      connector.put("dsn", "postgres://" + address(listener) + "/source");
      SqlProber guarded =
          new SqlProber(
              new DestinationGuard(List.of(), InetAddress::getAllByName), Duration.ofSeconds(3));

      ConnectionTestFailure failure =
          assertThrows(
              ConnectionTestFailure.class,
              () -> guarded.probe(connector, null, false, new Cancellation()));

      assertEquals(Reason.INVALID_CONNECTOR, failure.reason(), failure.getMessage());
      assertNull(listener.accept(), "no connection was opened");
    }
  }

  @Test
  void everyHostOfAFailOverDsnIsCheckedBeforeTheFirstIsContacted() throws Exception {
    try (ServerSocketChannel first = loopbackListener()) {
      List<String> checked = new ArrayList<>();
      SqlProber guarded =
          new SqlProber(
              host -> {
                checked.add(host);
                if ("refused.example".equals(host)) {
                  throw ConnectionTestFailure.invalid("refused " + host);
                }
              },
              Duration.ofSeconds(3));
      Map<String, Object> connector = connector("readings", List.of(), "owner", "owner-secret");
      connector.put("dsn", "postgres://" + address(first) + ",refused.example:5432/source");

      ConnectionTestFailure failure =
          assertThrows(
              ConnectionTestFailure.class,
              () -> guarded.probe(connector, null, false, new Cancellation()));

      assertEquals("refused refused.example", failure.getMessage());
      assertEquals(List.of(loopback(), "refused.example"), checked);
      assertNull(first.accept(), "the allowed first host was not contacted");
    }
  }

  private ProbeResult probe(Map<String, Object> connector) throws ConnectionTestFailure {
    return prober.probe(connector, null, false, new Cancellation());
  }

  private ProbeResult probeWithWhere(Map<String, Object> connector) throws ConnectionTestFailure {
    return prober.probe(connector, null, true, new Cancellation());
  }

  private static Map<String, Object> connector(
      String table, List<String> columns, String user, String password) {
    Map<String, Object> connector = new HashMap<>();
    connector.put("connectionType", "sql");
    connector.put("driver", "postgres");
    connector.put(
        "dsn",
        "postgres://" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/source");
    connector.put("table", table);
    connector.put("columns", columns);
    connector.put("user", user);
    connector.put("password", password);
    return connector;
  }

  /** A non-blocking listener, whose {@code accept()} returns null when nobody connected. */
  private static ServerSocketChannel loopbackListener() throws IOException {
    ServerSocketChannel listener = ServerSocketChannel.open();
    listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    listener.configureBlocking(false);
    return listener;
  }

  private static String address(ServerSocketChannel listener) throws IOException {
    return loopback() + ":" + ((InetSocketAddress) listener.getLocalAddress()).getPort();
  }

  private static String loopback() {
    return InetAddress.getLoopbackAddress().getHostAddress();
  }

  private static Connection ownerConnection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static int auditEntries() throws SQLException {
    return rowCount("audit");
  }

  private static int rowCount(String table) throws SQLException {
    try (Connection owner = ownerConnection();
        Statement sql = owner.createStatement();
        ResultSet result = sql.executeQuery("SELECT count(*) FROM " + table)) {
      result.next();
      return result.getInt(1);
    }
  }
}
