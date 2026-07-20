/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.common.kafka;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlowableTriggerConsumerTest {

  @Mock private RuntimeService runtimeService;
  @Mock private ProcessInstance processInstance;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private FlowableTriggerConsumer consumer;

  @BeforeEach
  void setUp() {
    var historyService = mock(org.flowable.engine.HistoryService.class);
    consumer = new FlowableTriggerConsumer(runtimeService, historyService);

    var query = mock(org.flowable.engine.runtime.ProcessInstanceQuery.class);
    lenient().when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
    lenient().when(query.processInstanceBusinessKey(anyString())).thenReturn(query);
    lenient().when(query.count()).thenReturn(0L);

    var histQuery = mock(org.flowable.engine.history.HistoricProcessInstanceQuery.class);
    lenient().when(historyService.createHistoricProcessInstanceQuery()).thenReturn(histQuery);
    lenient().when(histQuery.processInstanceBusinessKey(anyString())).thenReturn(histQuery);
    lenient().when(histQuery.finished()).thenReturn(histQuery);
    lenient().when(histQuery.count()).thenReturn(0L);

    lenient()
        .when(runtimeService.startProcessInstanceByKey(anyString(), anyString(), anyMap()))
        .thenReturn(processInstance);
    lenient().when(processInstance.getId()).thenReturn("pi-123");
  }

  @Test
  void shouldStartDatasetCreateProcess() throws Exception {
    byte[] trigger =
        objectMapper.writeValueAsBytes(
            Map.of(
                "sagaType", "DATASET_CREATE",
                "datasetId", "ds-456",
                "datasetName", "Test",
                "hasPipelines", false));

    TriggerTestSupport.processTrigger(consumer, trigger);

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService)
        .startProcessInstanceByKey(keyCaptor.capture(), anyString(), varsCaptor.capture());

    assertEquals("dataset-create", keyCaptor.getValue());
    assertEquals("ds-456", varsCaptor.getValue().get("datasetId"));
    assertEquals("Test", varsCaptor.getValue().get("datasetName"));
  }

  @Test
  void shouldStartDatasetUpdateProcess() throws Exception {
    byte[] trigger =
        objectMapper.writeValueAsBytes(
            Map.of("sagaType", "DATASET_UPDATE", "datasetId", "ds-456", "hasPipelines", true));

    TriggerTestSupport.processTrigger(consumer, trigger);

    verify(runtimeService).startProcessInstanceByKey(eq("dataset-update"), anyString(), anyMap());
  }

  @Test
  void shouldStartDatasetDeleteProcess() throws Exception {
    byte[] trigger =
        objectMapper.writeValueAsBytes(
            Map.of("sagaType", "DATASET_DELETE", "datasetId", "ds-456", "hasPipelines", false));

    TriggerTestSupport.processTrigger(consumer, trigger);

    verify(runtimeService).startProcessInstanceByKey(eq("dataset-delete"), anyString(), anyMap());
  }

  @Test
  void shouldStartDatasetUnreleaseProcessAndDeriveHasPipelines() throws Exception {
    // A typo in SagaType.DATASET_UNRELEASE's key would deploy fine but silently drop every
    // unrelease trigger — pin the enum-key-to-process-key mapping here. Also assert hasPipelines is
    // derived from pipelineIds, since the unrelease process gates DELETE_PIPELINES on it.
    byte[] trigger =
        objectMapper.writeValueAsBytes(
            Map.of(
                "sagaType",
                "DATASET_UNRELEASE",
                "datasetId",
                "ds-789",
                "pipelineIds",
                List.of("pipe-1")));

    TriggerTestSupport.processTrigger(consumer, trigger);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService)
        .startProcessInstanceByKey(eq("dataset-unrelease"), anyString(), varsCaptor.capture());
    assertEquals(true, varsCaptor.getValue().get("hasPipelines"));
  }

  @Test
  void shouldGenerateSagaIdIfNotProvided() throws Exception {
    byte[] trigger =
        objectMapper.writeValueAsBytes(
            Map.of("sagaType", "DATASET_CREATE", "datasetId", "ds-456", "hasPipelines", false));

    TriggerTestSupport.processTrigger(consumer, trigger);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService)
        .startProcessInstanceByKey(anyString(), anyString(), varsCaptor.capture());

    assertNotNull(varsCaptor.getValue().get("sagaId"), "sagaId should be generated if missing");
  }

  @Test
  void shouldIgnoreMalformedTrigger() throws Exception {
    byte[] trigger = objectMapper.writeValueAsBytes(Map.of("foo", "bar"));

    TriggerTestSupport.processTrigger(consumer, trigger);

    verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), anyMap());
  }

  @Test
  void shouldIgnoreWrongFieldTypes() throws Exception {
    byte[] trigger = objectMapper.writeValueAsBytes(Map.of("sagaType", 123, "datasetId", "ds-456"));

    TriggerTestSupport.processTrigger(consumer, trigger);

    verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), anyMap());
  }

  @Test
  void shouldIgnoreUnknownSagaType() throws Exception {
    byte[] trigger =
        objectMapper.writeValueAsBytes(Map.of("sagaType", "UNKNOWN_TYPE", "datasetId", "ds-456"));

    TriggerTestSupport.processTrigger(consumer, trigger);

    verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), anyMap());
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldSeekBackToFailedRecordOnTransientError() throws Exception {
    byte[] triggerJson =
        objectMapper.writeValueAsBytes(Map.of("sagaType", "DATASET_CREATE", "datasetId", "ds-1"));

    TopicPartition tp = new TopicPartition(FlowableTriggerConsumer.TRIGGER_TOPIC, 0);
    ConsumerRecord<String, byte[]> record =
        new ConsumerRecord<>(FlowableTriggerConsumer.TRIGGER_TOPIC, 0, 42L, null, triggerJson);
    ConsumerRecords<String, byte[]> records = new ConsumerRecords<>(Map.of(tp, List.of(record)));

    KafkaConsumer<String, byte[]> mockKafkaConsumer = mock(KafkaConsumer.class);
    lenient()
        .when(mockKafkaConsumer.poll(any(Duration.class)))
        .thenReturn(records)
        .thenReturn(new ConsumerRecords<>(Map.of()));

    // Transient error on process start
    lenient()
        .when(runtimeService.startProcessInstanceByKey(anyString(), anyString(), anyMap()))
        .thenThrow(new RuntimeException("DB unavailable"));

    consumer.start(mockKafkaConsumer);

    await()
        .atMost(2, TimeUnit.SECONDS)
        .untilAsserted(() -> verify(mockKafkaConsumer).seek(tp, 42L));

    consumer.stop();

    verify(mockKafkaConsumer, never()).commitSync(anyMap());
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldSeekAllPartitionsOnTransientBatchError() throws Exception {
    byte[] triggerJson =
        objectMapper.writeValueAsBytes(Map.of("sagaType", "DATASET_CREATE", "datasetId", "ds-1"));

    TopicPartition tp0 = new TopicPartition(FlowableTriggerConsumer.TRIGGER_TOPIC, 0);
    TopicPartition tp1 = new TopicPartition(FlowableTriggerConsumer.TRIGGER_TOPIC, 1);
    ConsumerRecord<String, byte[]> record0 =
        new ConsumerRecord<>(FlowableTriggerConsumer.TRIGGER_TOPIC, 0, 42L, null, triggerJson);
    ConsumerRecord<String, byte[]> record1 =
        new ConsumerRecord<>(FlowableTriggerConsumer.TRIGGER_TOPIC, 1, 7L, null, triggerJson);

    ConsumerRecords<String, byte[]> records =
        new ConsumerRecords<>(Map.of(tp0, List.of(record0), tp1, List.of(record1)));

    KafkaConsumer<String, byte[]> mockKafkaConsumer = mock(KafkaConsumer.class);
    lenient()
        .when(mockKafkaConsumer.poll(any(Duration.class)))
        .thenReturn(records)
        .thenReturn(new ConsumerRecords<>(Map.of()));

    // Transient error on process start — first record fails, second never reached
    lenient()
        .when(runtimeService.startProcessInstanceByKey(anyString(), anyString(), anyMap()))
        .thenThrow(new RuntimeException("DB unavailable"));

    consumer.start(mockKafkaConsumer);

    await()
        .atMost(2, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              verify(mockKafkaConsumer).seek(tp0, 42L);
              verify(mockKafkaConsumer).seek(tp1, 7L);
            });

    consumer.stop();

    verify(mockKafkaConsumer, never()).commitSync(anyMap());
  }
}
