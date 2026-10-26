package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.InfraTestDataFactory;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.security.AllowedScopesFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Release and unrelease publish a saga trigger, so a test of their answer needs the broker of the
 * saga profile.
 */
@TestPropertySource(
    properties = {"kafka.enabled=true", "spring.kafka.listener.missing-topics-fatal=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(InfraTestDataFactory.class)
class DataSetReleaseSagaIntegrationTest extends AbstractSagaIntegrationTest {

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
  void releaseDataSet_answers202Accepted() {
    DataSet dataSet = dataSetIn(DataSetStatus.READY);

    assertThat(post("/datasets/" + dataSet.getId() + "/release").getStatusCode())
        .isEqualTo(HttpStatus.ACCEPTED);
  }

  @Test
  void unreleaseDataSet_answers202Accepted() {
    DataSet dataSet = dataSetIn(DataSetStatus.AVAILABLE);

    assertThat(post("/datasets/" + dataSet.getId() + "/unrelease").getStatusCode())
        .isEqualTo(HttpStatus.ACCEPTED);
  }

  private DataSet dataSetIn(DataSetStatus status) {
    DataSet dataSet = data.createDataSet("Release Saga Dataset");
    dataSet.setDataSetStatus(status);
    return dataSetRepository.save(dataSet);
  }

  private ResponseEntity<String> post(String path) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());
    headers.set(AllowedScopesFilter.HEADER_NAME, "*");
    return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(headers), String.class);
  }
}
