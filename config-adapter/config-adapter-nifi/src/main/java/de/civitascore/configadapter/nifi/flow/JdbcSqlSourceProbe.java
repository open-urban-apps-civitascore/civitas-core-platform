/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.flow.FlowDeploymentPlanner.SqlSourceProbe;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Validates a SQL source by opening (and immediately closing) a short-lived JDBC connection with
 * the resolved credentials. A failure here means the deploy fails loudly with a clear message,
 * instead of NiFi accepting the flow and then silently producing no data because the source is
 * unreachable or the credentials are wrong.
 */
public class JdbcSqlSourceProbe implements SqlSourceProbe {

  private static final int LOGIN_TIMEOUT_SECONDS = 5;

  @Override
  public void probe(String jdbcUrl, String user, String password) throws FatalAdapterException {
    try {
      // explicit registration is robust even if driver auto-discovery is stripped by shading
      Class.forName("org.postgresql.Driver");
    } catch (ClassNotFoundException e) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, e, "PostgreSQL JDBC driver not on the classpath");
    }
    DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
    Properties props = new Properties();
    if (user != null && !user.isBlank()) {
      props.setProperty("user", user);
    }
    if (password != null && !password.isBlank()) {
      props.setProperty("password", password);
    }
    try (Connection ignored = DriverManager.getConnection(jdbcUrl, props)) {
      // a successful open+close proves reachability and that the credentials are accepted
    } catch (SQLException e) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          e,
          "SQL source is not reachable with the configured connection: " + e.getMessage());
    }
  }
}
