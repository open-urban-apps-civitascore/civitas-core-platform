package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Catalog;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Distribution;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.repository.CatalogRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.InvalidInputException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("DataSet Service Integration Tests")
class DataSetServiceIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private DataSetService dataSetService;
  @Autowired private PortalTestDataFactory portalData;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private CatalogRepository catalogRepository;

  @AfterEach
  void cleanup() {
    portalData.cleanAll();
  }

  /** Helper method to create a sample styles map for Pipeline. */
  private Map<String, Object> createSampleStyles() {
    Map<String, Object> styles = new HashMap<>();
    styles.put("nodes", List.of(Map.of("id", "1", "type", "input")));
    styles.put("edges", List.of());
    Map<String, Object> viewport = new HashMap<>();
    viewport.put("x", 0);
    viewport.put("y", 0);
    viewport.put("zoom", 1);
    styles.put("viewport", viewport);
    return styles;
  }

  /** Helper method to create a sample model map for Pipeline. */
  private Map<String, Object> createSampleModel() {
    Map<String, Object> model = new HashMap<>();
    model.put("input", Map.of("type", "kafka"));
    model.put("pipeline", List.of(Map.of("processor", "transform")));
    model.put("output", Map.of("type", "frost"));
    return model;
  }

  @Test
  @Transactional
  @DisplayName("Should retrieve dataset with all relationships")
  void getDataSetWithRelationships() {
    DataSet dataSet = createInitialDataSet();
    UUID dataSetId = dataSet.getId();

    Pipeline pipeline1 = createPipelineForDataSet(dataSet, "Pipeline 1");
    Pipeline pipeline2 = createPipelineForDataSet(dataSet, "Pipeline 2");
    dataSet.getPipelines().addAll(Set.of(pipeline1, pipeline2));

    Distribution distribution1 = createDistributionForDataSet(dataSet, "/api/v1/traffic");
    Distribution distribution2 = createDistributionForDataSet(dataSet, "/api/v1/weather");
    dataSet.getDistributions().addAll(Set.of(distribution1, distribution2));

    Catalog catalog1 = createInitialCatalog("Catalog 1");
    Catalog catalog2 = createInitialCatalog("Catalog 2");
    catalog1.getDataSets().add(dataSet);
    catalog2.getDataSets().add(dataSet);
    catalogRepository.save(catalog1);
    catalogRepository.save(catalog2);
    dataSet.getCatalogs().addAll(Set.of(catalog1, catalog2));

    Assignment assignment1 = createAssignmentForDataSet(dataSet, "Test Assignment 1");
    Assignment assignment2 = createAssignmentForDataSet(dataSet, "Test Assignment 2");
    dataSet.getAssignments().addAll(Set.of(assignment1, assignment2));

    dataSet = dataSetRepository.save(dataSet);

    Optional<DataSet> retrievedDataSetOpt = dataSetService.findById(dataSet.getId());

    assertThat(retrievedDataSetOpt).isPresent();
    DataSet retrievedDataSet = retrievedDataSetOpt.get();

    assertThat(retrievedDataSet.getId()).isEqualTo(dataSet.getId());
    assertThat(retrievedDataSet.getName()).isEqualTo(dataSet.getName());
    assertThat(retrievedDataSet.getDescription()).isEqualTo(dataSet.getDescription());
    assertThat(retrievedDataSet.getDataSetStatus()).isEqualTo(DataSetStatus.DRAFT);

    assertThat(retrievedDataSet.getPipelines())
        .isNotNull()
        .hasSize(2)
        .extracting(Pipeline::getName)
        .containsExactlyInAnyOrder(pipeline1.getName(), pipeline2.getName());

    retrievedDataSet
        .getPipelines()
        .forEach(
            pipeline -> {
              assertThat(pipeline.getDataSet()).isNotNull();
              assertThat(pipeline.getDataSet().getId()).isEqualTo(dataSetId);
            });

    assertThat(retrievedDataSet.getDistributions())
        .isNotNull()
        .hasSize(2)
        .extracting(Distribution::getAccessUrl)
        .containsExactlyInAnyOrder(distribution1.getAccessUrl(), distribution2.getAccessUrl());

    retrievedDataSet
        .getDistributions()
        .forEach(
            distribution -> {
              assertThat(distribution.getDataSet()).isNotNull();
              assertThat(distribution.getDataSet().getId()).isEqualTo(dataSetId);
            });

    assertThat(retrievedDataSet.getCatalogs())
        .isNotNull()
        .hasSize(2)
        .extracting(Catalog::getName)
        .containsExactlyInAnyOrder(catalog1.getName(), catalog2.getName());

    assertThat(retrievedDataSet.getAssignments())
        .isNotNull()
        .hasSize(2)
        .extracting(assignment -> assignment.getRole().getName())
        .containsExactlyInAnyOrder(
            assignment1.getRole().getName(), assignment2.getRole().getName());

    retrievedDataSet
        .getAssignments()
        .forEach(
            assignment -> {
              assertThat(assignment.getDataset()).isNotNull();
              assertThat(assignment.getDataset().getId()).isEqualTo(dataSetId);
            });
  }

  /**************
   * Helper methods to create test data with relationships
   *************/

  private DataSet createInitialDataSet() {
    return portalData.dataSet(b -> b.description("Test dataset for relationship testing"));
  }

  private Pipeline createPipelineForDataSet(DataSet dataSet, String name) {
    DataSource ds1 = portalData.dataSource();
    DataSource ds2 = portalData.dataSource();
    DataSource ds3 = portalData.dataSource();

    return portalData.pipeline(
        dataSet,
        b ->
            b.name(name + "_" + System.currentTimeMillis())
                .description("Test pipeline for " + name)
                .styles(createSampleStyles())
                .dataSources(new HashSet<>(Set.of(ds1, ds2, ds3)))
                .persistences(Collections.singletonList(12345L))
                .model(createSampleModel()));
  }

  private Distribution createDistributionForDataSet(DataSet dataSet, String apiPath) {
    return portalData.distribution(
        b -> b.accessUrl("http://localhost:8080" + apiPath).dataSet(dataSet));
  }

  private Catalog createInitialCatalog(String name) {
    return portalData.catalog(b -> b.name(name + "_" + System.currentTimeMillis()));
  }

  private Assignment createAssignmentForDataSet(DataSet dataSet, String roleName) {
    Group group = portalData.group(b -> b.description("Test group for " + roleName));
    Role role =
        portalData.role(b -> b.description("Test role for " + roleName).roleType(RoleType.DATA));

    return portalData.assignment(group, role, dataSet);
  }

  @Nested
  @DisplayName("Stage DataSet Tests")
  class StageDataSetTests {
    @Test
    @Transactional
    @DisplayName("Should mark dataset as ready with pipelines")
    void shouldStageDataSetWithPipelines() {
      DataSet dataSet = createInitialDataSet();
      Pipeline pipeline = createPipelineForDataSet(dataSet, "Pipeline for Stage");
      dataSet.getPipelines().add(pipeline);
      dataSet = dataSetRepository.save(dataSet);

      DataSet readyDataSet = dataSetService.stage(dataSet.getId());

      assertThat(readyDataSet.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
    }

    @Test
    @Transactional
    @DisplayName("Should throw InvalidInputException when marking already ready dataset as ready")
    void shouldThrowExceptionWhenMarkingReadyAlreadyReadyDataSet() {
      DataSet dataSet = createInitialDataSet();
      Pipeline pipeline = createPipelineForDataSet(dataSet, "Pipeline for Stage");
      dataSet.getPipelines().add(pipeline);
      dataSet = dataSetRepository.save(dataSet);

      DataSet readyDataSet = dataSetService.stage(dataSet.getId());
      UUID readyDataSetId = readyDataSet.getId();

      assertThatThrownBy(() -> dataSetService.stage(readyDataSetId))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Only DRAFT datasets can be staged");
    }

    @Test
    @Transactional
    @DisplayName(
        "Should throw InvalidInputException when marking dataset as ready without pipelines")
    void shouldThrowExceptionWhenMarkingReadyDataSetWithoutPipelines() {
      DataSet dataSet = createInitialDataSet();

      assertThatThrownBy(() -> dataSetService.stage(dataSet.getId()))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("DataSet must contain at least one Pipeline before staging");
    }
  }
}
