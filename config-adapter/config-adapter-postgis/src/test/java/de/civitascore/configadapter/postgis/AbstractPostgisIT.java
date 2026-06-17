/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.postgis;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for PostGIS integration tests. Uses the singleton container pattern so the PostGIS
 * container starts once per JVM and is shared across all subclasses.
 */
@SuppressWarnings("resource")
abstract class AbstractPostgisIT {

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres");

  protected static final PostgreSQLContainer POSTGIS;

  static {
    POSTGIS =
        new PostgreSQLContainer(POSTGIS_IMAGE)
            .withDatabaseName("civitas")
            .withUsername("civitas")
            .withPassword("civitas");
    POSTGIS.start();

    Runtime.getRuntime().addShutdownHook(new Thread(POSTGIS::stop));
  }

  protected static String jdbcUrl() {
    return POSTGIS.getJdbcUrl();
  }

  protected static String username() {
    return POSTGIS.getUsername();
  }

  protected static String password() {
    return POSTGIS.getPassword();
  }

  protected static boolean tableExists(String schema, String name) throws SQLException {
    String predicate =
        schema == null
            ? "table_schema = 'public' AND table_name = '" + name + "'"
            : "table_schema = '" + schema + "' AND table_name = '" + name + "'";
    try (Connection connection = DriverManager.getConnection(jdbcUrl(), username(), password());
        Statement stmt = connection.createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT count(*) FROM information_schema.tables WHERE " + predicate)) {
      return rs.next() && rs.getInt(1) > 0;
    }
  }

  protected static void executeSql(String sql) throws SQLException {
    try (Connection connection = DriverManager.getConnection(jdbcUrl(), username(), password());
        Statement stmt = connection.createStatement()) {
      stmt.execute(sql);
    }
  }
}
