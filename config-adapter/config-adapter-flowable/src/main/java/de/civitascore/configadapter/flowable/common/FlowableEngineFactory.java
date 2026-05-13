/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.common;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.StandaloneProcessEngineConfiguration;

/**
 * Factory for creating a standalone Flowable {@link ProcessEngine} without Spring Boot. Configures
 * the engine with a provided {@link DataSource}, auto-DDL, and async execution for crash recovery.
 */
public final class FlowableEngineFactory {

  private FlowableEngineFactory() {}

  /**
   * Creates a {@link ProcessEngine} backed by the given DataSource.
   *
   * @param dataSource the JDBC DataSource (e.g. HikariCP-wrapped PostgreSQL)
   * @param beans beans to make available to JavaDelegates via the engine configuration
   * @return a configured and started ProcessEngine
   */
  public static ProcessEngine create(DataSource dataSource, Map<String, Object> beans) {
    StandaloneProcessEngineConfiguration config = new StandaloneProcessEngineConfiguration();
    config.setDataSource(dataSource);
    config.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
    config.setAsyncExecutorActivate(true);
    applyBeans(config, beans);
    return config.buildProcessEngine();
  }

  /**
   * Creates a {@link ProcessEngine} backed by the given DataSource with no additional beans.
   *
   * @param dataSource the JDBC DataSource
   * @return a configured and started ProcessEngine
   */
  public static ProcessEngine create(DataSource dataSource) {
    return create(dataSource, Map.of());
  }

  /**
   * Creates a {@link ProcessEngine} with an in-memory H2 database. Intended for unit and process
   * tests.
   *
   * @param beans beans to make available to JavaDelegates
   * @return a test ProcessEngine
   */
  public static ProcessEngine createWithH2(Map<String, Object> beans) {
    String uniqueId = UUID.randomUUID().toString().substring(0, 8);
    StandaloneProcessEngineConfiguration config = new StandaloneProcessEngineConfiguration();
    config.setEngineName("flowable-test-" + uniqueId);
    config.setJdbcUrl("jdbc:h2:mem:flowable-test-" + uniqueId + ";DB_CLOSE_DELAY=-1");
    config.setJdbcDriver("org.h2.Driver");
    config.setJdbcUsername("sa");
    config.setJdbcPassword("");
    config.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
    config.setAsyncExecutorActivate(false);
    applyBeans(config, beans);
    return config.buildProcessEngine();
  }

  /**
   * Creates a {@link ProcessEngine} with an in-memory H2 database and no additional beans. Intended
   * for unit and process tests.
   *
   * @return a test ProcessEngine
   */
  public static ProcessEngine createWithH2() {
    return createWithH2(Map.of());
  }

  private static void applyBeans(
      StandaloneProcessEngineConfiguration config, Map<String, Object> beans) {
    if (beans != null && !beans.isEmpty()) {
      Map<Object, Object> engineBeans = new HashMap<>(beans);
      config.setBeans(engineBeans);
    }
  }
}
