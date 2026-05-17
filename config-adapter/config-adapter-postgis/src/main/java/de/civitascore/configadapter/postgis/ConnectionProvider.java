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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * Wraps a {@link HikariDataSource} configured from adapter properties. Kept as a thin abstraction
 * so tests can substitute a {@link DataSource} backed by Testcontainers.
 */
public final class ConnectionProvider implements AutoCloseable {

  private final HikariDataSource dataSource;

  public ConnectionProvider(
      String jdbcUrl, String user, String password, int maxPoolSize, long connectionTimeoutMs) {
    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setJdbcUrl(jdbcUrl);
    hikariConfig.setUsername(user);
    hikariConfig.setPassword(password);
    hikariConfig.setMaximumPoolSize(maxPoolSize);
    hikariConfig.setConnectionTimeout(connectionTimeoutMs);
    hikariConfig.setPoolName("postgis-adapter-pool");
    this.dataSource = new HikariDataSource(hikariConfig);
  }

  public Connection getConnection() throws SQLException {
    return dataSource.getConnection();
  }

  public DataSource getDataSource() {
    return dataSource;
  }

  @Override
  public void close() {
    dataSource.close();
  }
}
