package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Catalog;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Distribution;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.CatalogRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DistributionRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.util.InvalidInputException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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

  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private PipelineRepository pipelineRepository;
  @Autowired private DistributionRepository distributionRepository;
  @Autowired private CatalogRepository catalogRepository;
  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private DataSourceRepository dataSourceRepository;

  @AfterEach
  void cleanup() {
    assignmentRepository.deleteAll();
    pipelineRepository.deleteAll();
    distributionRepository.deleteAll();
    catalogRepository.deleteAll();
    dataSetRepository.deleteAll();
    roleRepository.deleteAll();
    groupRepository.deleteAll();
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
    assertThat(retrievedDataSet.getPersistenceId()).isEqualTo(12345L);

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
    DataSet dataSet = new DataSet();
    dataSet.setName("test_dataset_" + System.currentTimeMillis());
    dataSet.setDescription("Test dataset for relationship testing");
    dataSet.setPersistenceId(12345L);
    dataSet.setIdentifier("test-identifier-001");
    dataSet.setVersion("1.0.0");
    dataSet.setExternalId("ext-dataset-" + System.currentTimeMillis());
    dataSet.setFormat("JSON");
    return dataSetRepository.save(dataSet);
  }

  private Pipeline createPipelineForDataSet(DataSet dataSet, String name) {
    DataSource dataSource1 = new DataSource();
    dataSource1.setName("test_ds_1_" + System.currentTimeMillis());
    dataSource1 = dataSourceRepository.save(dataSource1);

    DataSource dataSource2 = new DataSource();
    dataSource2.setName("test_ds_2_" + System.currentTimeMillis());
    dataSource2 = dataSourceRepository.save(dataSource2);

    DataSource dataSource3 = new DataSource();
    dataSource3.setName("test_ds_3_" + System.currentTimeMillis());
    dataSource3 = dataSourceRepository.save(dataSource3);

    Pipeline pipeline = new Pipeline();
    pipeline.setName(name + "_" + System.currentTimeMillis());
    pipeline.setDescription("Test pipeline for " + name);
    pipeline.setDataSet(dataSet);
    pipeline.setStyles(createSampleStyles());
    pipeline.getDataSources().add(dataSource1);
    pipeline.getDataSources().add(dataSource2);
    pipeline.getDataSources().add(dataSource3);
    pipeline.setApis(Arrays.asList("/api/v1/traffic", "/api/v1/weather"));
    pipeline.setPersistences(Collections.singletonList(12345L));
    pipeline.setModel(createSampleModel());
    return pipelineRepository.save(pipeline);
  }

  private Distribution createDistributionForDataSet(DataSet dataSet, String apiPath) {
    Distribution distribution = new Distribution();
    distribution.setAccessUrl("http://localhost:8080" + apiPath);
    distribution.setApiType("SensorThings");
    distribution.setFormat("application/json");
    distribution.setAutoGenerated(true);
    distribution.setDataSet(dataSet);
    return distributionRepository.save(distribution);
  }

  private Catalog createInitialCatalog(String name) {
    Catalog catalog = new Catalog();
    catalog.setName(name + "_" + System.currentTimeMillis());
    catalog.setDescription("Test catalog for " + name);
    return catalogRepository.save(catalog);
  }

  private Assignment createAssignmentForDataSet(DataSet dataSet, String roleName) {
    Group group = new Group();
    group.setName("Test Group " + roleName + "_" + System.currentTimeMillis());
    group.setDescription("Test group for " + roleName);
    group = groupRepository.save(group);

    Role role = new Role();
    role.setName(roleName.toLowerCase().replace(" ", "_") + "_" + System.currentTimeMillis());
    role.setDescription("Test role for " + roleName);
    role.setRoleType(RoleType.DATA);
    role = roleRepository.save(role);

    Assignment assignment = new Assignment();
    assignment.setGroup(group);
    assignment.setRole(role);
    assignment.setScopeType(ScopeType.DATASET);
    assignment.setDataset(dataSet);
    return assignmentRepository.save(assignment);
  }

  @Nested
  @DisplayName("Publish DataSet Tests")
  class PublishDataSetTests {
    @Test
    @Transactional
    @DisplayName("Should publish dataset with pipelines")
    void shouldPublishDataSetWithPipelines() {
      DataSet dataSet = createInitialDataSet();
      Pipeline pipeline = createPipelineForDataSet(dataSet, "Pipeline for Publishing");
      dataSet.getPipelines().add(pipeline);
      dataSet = dataSetRepository.save(dataSet);

      DataSet publishedDataSet = dataSetService.publish(dataSet.getId());

      assertThat(publishedDataSet.getDataSetStatus()).isEqualTo(DataSetStatus.READY);

      assertThat(publishedDataSet.getDistributions())
          .isNotNull()
          .hasSizeGreaterThanOrEqualTo(2)
          .allMatch(distribution -> Boolean.TRUE.equals(distribution.getAutoGenerated()));

      List<String> accessUrls =
          publishedDataSet.getDistributions().stream().map(Distribution::getAccessUrl).toList();
      assertThat(accessUrls).contains("/api/v1/traffic", "/api/v1/weather");
    }

    @Test
    @Transactional
    @DisplayName("Should throw InvalidInputException when publishing already published dataset")
    void shouldThrowExceptionWhenPublishingAlreadyPublishedDataSet() {
      DataSet dataSet = createInitialDataSet();
      Pipeline pipeline = createPipelineForDataSet(dataSet, "Pipeline for Publishing");
      dataSet.getPipelines().add(pipeline);
      dataSet = dataSetRepository.save(dataSet);

      DataSet publishedDataSet = dataSetService.publish(dataSet.getId());
      UUID publishedDataSetId = publishedDataSet.getId();

      assertThatThrownBy(() -> dataSetService.publish(publishedDataSetId))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("DataSet is already published");
    }

    @Test
    @Transactional
    @DisplayName("Should throw InvalidInputException when publishing dataset without pipelines")
    void shouldThrowExceptionWhenPublishingDataSetWithoutPipelines() {
      DataSet dataSet = createInitialDataSet();

      assertThatThrownBy(() -> dataSetService.publish(dataSet.getId()))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("DataSet must contain at least one Pipeline before publishing");
    }
  }
}
