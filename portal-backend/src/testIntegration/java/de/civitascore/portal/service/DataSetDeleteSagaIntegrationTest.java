package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.InfraTestDataFactory;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.repository.DataSetRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Saga integration test for the DELETE trigger of a provisioned dataset. A provisioned dataset
 * carries a data-holding sink that must be torn down asynchronously, so {@code deleteById} does not
 * remove the entity directly — it marks it pending DELETE and publishes the trigger to Kafka. This
 * requires a real broker, hence the {@code saga} profile; the plain controller integration context
 * has no Kafka.
 *
 * <p>Scope is the trigger contract only (entity kept, pending DELETE, publish succeeds); the
 * orchestrator run itself is covered by the E2E saga tests.
 */
@TestPropertySource(
    properties = {"kafka.enabled=true", "spring.kafka.listener.missing-topics-fatal=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(InfraTestDataFactory.class)
class DataSetDeleteSagaIntegrationTest extends AbstractSagaIntegrationTest {

  @Autowired private DataSetService dataSetService;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private InfraTestDataFactory data;

  @DynamicPropertySource
  static void configureKafka(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @AfterEach
  void cleanDb() {
    data.cleanAll();
  }

  @Test
  void deleteProvisionedDataSet_marksPendingDeleteAndKeepsEntity() {
    DataSet dataSet = data.createDataSet("Delete Saga Dataset");
    dataSet.setDataSetStatus(DataSetStatus.READY);
    dataSet.setProvisioned(true);
    dataSet.setProjectId("proj-test");
    dataSet = dataSetRepository.save(dataSet);
    UUID dataSetId = dataSet.getId();

    dataSetService.deleteById(dataSetId);

    DataSet persisted = dataSetRepository.findById(dataSetId).orElseThrow();
    assertThat(persisted.getPendingSagaType()).isEqualTo(PendingSagaType.DELETE);
  }
}
