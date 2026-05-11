package de.civitascore.portal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.NamedApi;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("NamedApi Persistence Tests (#1311)")
class NamedApiPersistenceIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private EntityManager entityManager;

  @AfterEach
  void cleanup() {
    dataSetRepository.deleteAllInBatch();
  }

  private DataSet newDataSet() {
    DataSet ds = new DataSet();
    ds.setName("dataset-" + UUID.randomUUID().toString().substring(0, 8));
    return ds;
  }

  private NamedApi namedApi(String slug) {
    NamedApi api = new NamedApi();
    api.setName("API " + slug);
    api.setSlug(slug);
    api.setStandard("STA");
    return api;
  }

  @Test
  @DisplayName("DB UNIQUE(dataset_id, slug) rejects duplicate slug within a dataset")
  void duplicateSlugWithinDatasetIsRejected() {
    DataSet dataSet = newDataSet();
    dataSet.setNamedApis(List.of(namedApi("traffic"), namedApi("traffic")));

    assertThatThrownBy(() -> dataSetRepository.saveAndFlush(dataSet))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_named_api_dataset_slug");
  }

  @Test
  @DisplayName("Same slug is allowed across different datasets")
  void sameSlugAcrossDatasetsIsAllowed() {
    DataSet first = newDataSet();
    first.setNamedApis(List.of(namedApi("traffic")));
    dataSetRepository.saveAndFlush(first);

    DataSet second = newDataSet();
    second.setNamedApis(List.of(namedApi("traffic")));

    assertThat(dataSetRepository.saveAndFlush(second)).isNotNull();
  }

  @Test
  @DisplayName(
      "findById eagerly initializes namedApis (no LazyInitializationException after clear)")
  void findByIdEagerlyLoadsNamedApis() {
    DataSet dataSet = newDataSet();
    dataSet.setNamedApis(List.of(namedApi("traffic"), namedApi("weather")));
    UUID id = dataSetRepository.saveAndFlush(dataSet).getId();

    entityManager.clear();

    DataSet loaded = dataSetRepository.findById(id).orElseThrow();
    assertThat(Hibernate.isInitialized(loaded.getNamedApis())).isTrue();
    assertThat(loaded.getNamedApis())
        .extracting(NamedApi::getSlug)
        .containsExactlyInAnyOrder("traffic", "weather");
  }
}
