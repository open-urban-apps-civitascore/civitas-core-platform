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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.messaging.EventPublisher;
import de.civitascore.configadapter.model.Config;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.ConfigValue;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.Payload;
import de.civitascore.configadapter.model.idm.RealmConfig;
import de.civitascore.configadapter.model.postgis.ColumnConfig;
import de.civitascore.configadapter.model.postgis.ColumnType;
import de.civitascore.configadapter.model.postgis.PostgisConfigValue;
import de.civitascore.configadapter.model.postgis.TableConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PostgisAdapterTest {

  private static final String TABLE_CREATED_TOPIC = "de.civitascore.data.table.created";
  private static final String TABLE_DELETED_TOPIC = "de.civitascore.data.table.deleted";
  private static final String RESULT_TOPIC = "test-result-topic";

  private PostgisAdapter adapter;
  private AdapterConfig mockConfig;
  private ConnectionProvider mockConnectionProvider;
  private Connection mockConnection;
  private Statement mockStatement;
  private EventPublisher mockPublisher;

  @BeforeEach
  void setUp() throws SQLException {
    adapter = new PostgisAdapter();
    mockConfig = mock(AdapterConfig.class);
    mockConnectionProvider = mock(ConnectionProvider.class);
    mockConnection = mock(Connection.class);
    mockStatement = mock(Statement.class);
    mockPublisher = mock(EventPublisher.class);

    when(mockConnectionProvider.getConnection()).thenReturn(mockConnection);
    when(mockConnection.createStatement()).thenReturn(mockStatement);

    adapter.setConnectionProvider(mockConnectionProvider);
  }

  @Test
  void adapterNameIsPostgis() {
    assertEquals("postgis", adapter.getName());
  }

  @Test
  void resultTypeIsPostgisProcessingResult() {
    assertEquals(PostgisConfigValue.POSTGIS_RESULT_TYPE, callGetResultType(adapter));
  }

  @Nested
  class Initialization {

    @Test
    void subscribesToConfiguredTopics() {
      stubBaseConfig();

      adapter.initialize(mockConfig);

      List<String> topics = adapter.getSubscribedTopics();
      assertEquals(1, topics.size());
      assertTrue(topics.contains(TABLE_CREATED_TOPIC));
    }

    @Test
    void missingJdbcUrlIsRejected() {
      when(mockConfig.getProperty("postgis.topics")).thenReturn(TABLE_CREATED_TOPIC);
      when(mockConfig.getProperty("postgis.jdbc.user")).thenReturn("user");

      assertThrows(IllegalStateException.class, () -> adapter.initialize(mockConfig));
    }

    @Test
    void missingJdbcUserIsRejected() {
      when(mockConfig.getProperty("postgis.topics")).thenReturn(TABLE_CREATED_TOPIC);
      when(mockConfig.getProperty("postgis.jdbc.url")).thenReturn("jdbc:postgresql://h/db");

      assertThrows(IllegalStateException.class, () -> adapter.initialize(mockConfig));
    }
  }

  @Nested
  class CreateTable {

    @BeforeEach
    void initialiseAdapter() {
      stubBaseConfig();
      adapter.initialize(mockConfig);
      adapter.setEventPublisher(mockPublisher);
    }

    @Test
    void successfulCreateExecutesDdlAndCommits() throws Exception {
      adapter.processConfigEvent(TABLE_CREATED_TOPIC, createTableEvent(Operation.CREATE));

      verify(mockStatement, atLeastOnce()).execute(anyString());
      verify(mockConnection).commit();
      verify(mockConnection, never()).rollback();
    }

    @Test
    void successfulCreatePublishesSuccessResult() throws Exception {
      adapter.processConfigEvent(TABLE_CREATED_TOPIC, createTableEvent(Operation.CREATE));

      ArgumentCaptor<ConfigResultEvent> resultCaptor =
          ArgumentCaptor.forClass(ConfigResultEvent.class);
      verify(mockPublisher).publish(eq(RESULT_TOPIC), resultCaptor.capture());
      ConfigResultEvent result = resultCaptor.getValue();
      assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
      assertEquals(Operation.CREATE, result.operation());
    }

    @Test
    void duplicateTableSqlStateIsAbsorbedAsSuccess() throws Exception {
      when(mockStatement.execute(anyString()))
          .thenThrow(new SQLException("relation exists", "42P07"));

      adapter.processConfigEvent(TABLE_CREATED_TOPIC, createTableEvent(Operation.CREATE));

      verify(mockConnection).commit();
      verify(mockPublisher).publish(eq(RESULT_TOPIC), any(ConfigResultEvent.class));
    }

    @Test
    void duplicateSchemaSqlStateIsAbsorbedAsSuccess() throws Exception {
      TableConfig tableWithSchema = buildSimpleTable();
      tableWithSchema.setSchema("iot");
      when(mockStatement.execute(anyString()))
          .thenThrow(new SQLException("schema exists", "42P06"))
          .thenReturn(false);

      adapter.processConfigEvent(
          TABLE_CREATED_TOPIC, createEvent(Operation.CREATE, tableWithSchema));

      verify(mockConnection).commit();
    }

    @Test
    void connectivitySqlStateProducesRetryableException() throws Exception {
      when(mockConnectionProvider.getConnection())
          .thenThrow(new SQLException("conn lost", "08006"));

      ConfigEvent event = createTableEvent(Operation.CREATE);

      assertThrows(
          RetryableAdapterException.class,
          () -> adapter.processConfigEvent(TABLE_CREATED_TOPIC, event));
    }

    @Test
    void nonIdempotentSqlExceptionProducesFatalAdapterException() throws Exception {
      when(mockStatement.execute(anyString())).thenThrow(new SQLException("syntax error", "42601"));

      ConfigEvent event = createTableEvent(Operation.CREATE);

      assertThrows(
          FatalAdapterException.class,
          () -> adapter.processConfigEvent(TABLE_CREATED_TOPIC, event));
    }

    @Test
    void nonIdempotentSqlExceptionRollsBackTransaction() throws Exception {
      when(mockStatement.execute(anyString())).thenThrow(new SQLException("syntax error", "42601"));

      try {
        adapter.processConfigEvent(TABLE_CREATED_TOPIC, createTableEvent(Operation.CREATE));
      } catch (FatalAdapterException expected) {
        // ignored — we only care about rollback behaviour here
      }

      verify(mockConnection).rollback();
      verify(mockConnection, never()).commit();
    }
  }

  @Nested
  class DeleteTable {

    @BeforeEach
    void initialiseAdapter() {
      stubBaseConfig();
      when(mockConfig.getProperty("postgis.topics")).thenReturn(TABLE_DELETED_TOPIC);
      adapter.initialize(mockConfig);
      adapter.setEventPublisher(mockPublisher);
    }

    @Test
    void successfulDeleteExecutesDropAndCommits() throws Exception {
      adapter.processConfigEvent(TABLE_DELETED_TOPIC, createTableEvent(Operation.DELETE));

      verify(mockStatement).execute(anyString());
      verify(mockConnection).commit();
    }

    @Test
    void undefinedTableSqlStateIsAbsorbedAsSuccess() throws Exception {
      when(mockStatement.execute(anyString()))
          .thenThrow(new SQLException("no such table", "42P01"));

      adapter.processConfigEvent(TABLE_DELETED_TOPIC, createTableEvent(Operation.DELETE));

      verify(mockConnection).commit();
      verify(mockPublisher).publish(eq(RESULT_TOPIC), any(ConfigResultEvent.class));
    }
  }

  @Nested
  class UnsupportedOrInvalidEvents {

    @BeforeEach
    void initialiseAdapter() {
      stubBaseConfig();
      adapter.initialize(mockConfig);
      adapter.setEventPublisher(mockPublisher);
    }

    @Test
    void updateOperationProducesFatalUnsupportedException() {
      ConfigEvent event = createTableEvent(Operation.UPDATE);

      FatalAdapterException thrown =
          assertThrows(
              FatalAdapterException.class,
              () -> adapter.processConfigEvent(TABLE_CREATED_TOPIC, event));

      assertTrue(thrown.getFullErrorIdentifier().contains("UNSUPPORTED_OPERATION"));
    }

    @Test
    void payloadOfWrongTypeProducesFatalInvalidPayload() {
      RealmConfig wrongTypePayload = new RealmConfig();
      ConfigEvent event = buildEvent(Operation.CREATE, wrongTypePayload);

      FatalAdapterException thrown =
          assertThrows(
              FatalAdapterException.class,
              () -> adapter.processConfigEvent(TABLE_CREATED_TOPIC, event));

      assertTrue(thrown.getFullErrorIdentifier().contains("INVALID_PAYLOAD"));
    }
  }

  @Test
  void connectionIsClosedAfterSuccessfulProcessing() throws Exception {
    stubBaseConfig();
    adapter.initialize(mockConfig);
    adapter.setEventPublisher(mockPublisher);

    adapter.processConfigEvent(TABLE_CREATED_TOPIC, createTableEvent(Operation.CREATE));

    verify(mockConnection).close();
    verify(mockStatement, times(1)).close();
  }

  private void stubBaseConfig() {
    when(mockConfig.getProperty("postgis.topics")).thenReturn(TABLE_CREATED_TOPIC);
    when(mockConfig.getProperty("postgis.jdbc.url")).thenReturn("jdbc:postgresql://h/db");
    when(mockConfig.getProperty("postgis.jdbc.user")).thenReturn("user");
    when(mockConfig.getProperty("postgis.jdbc.password", "")).thenReturn("pw");
    when(mockConfig.getProperty("postgis.jdbc.maxPoolSize", "5")).thenReturn("5");
    when(mockConfig.getProperty("postgis.jdbc.connectionTimeoutMs", "5000")).thenReturn("5000");
  }

  private ConfigEvent createTableEvent(Operation operation) {
    return createEvent(operation, buildSimpleTable());
  }

  private ConfigEvent createEvent(Operation operation, ConfigValue value) {
    return buildEvent(operation, value);
  }

  private ConfigEvent buildEvent(Operation operation, ConfigValue value) {
    Metadata metadata =
        new Metadata("msg-1", OffsetDateTime.now(), "test-source", "corr-1", "1", RESULT_TOPIC);
    Payload payload = new Payload("postgis", "iot/widgets", operation, new Config(null, value));
    return new ConfigEvent(metadata, payload);
  }

  private TableConfig buildSimpleTable() {
    TableConfig table = new TableConfig();
    table.setName("widgets");
    table.setColumns(
        List.of(new ColumnConfig("id", ColumnType.BIGINT, null, null, null, false, null)));
    return table;
  }

  private static String callGetResultType(PostgisAdapter adapter) {
    try {
      var method = PostgisAdapter.class.getDeclaredMethod("getResultType");
      method.setAccessible(true);
      Object result = method.invoke(adapter);
      assertNotNull(result);
      return result.toString();
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }
}
