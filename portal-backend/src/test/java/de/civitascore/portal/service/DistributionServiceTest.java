package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DistributionMapper;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.Distribution;
import de.civitascore.portal.model.entity.Resource;
import de.civitascore.portal.model.input.DistributionInputDTO;
import de.civitascore.portal.repository.DistributionRepository;
import de.civitascore.portal.repository.ResourceRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DistributionService Tests")
class DistributionServiceTest {

  @Mock private DistributionRepository distributionRepository;
  @Mock private DistributionMapper distributionMapper;
  @Mock private ResourceRepository resourceRepository;

  private DistributionService createService() {
    return new DistributionService(distributionRepository, distributionMapper, resourceRepository);
  }

  private DataSet dataSetWithId(UUID id) {
    DataSet dataSet = new DataSet();
    dataSet.setId(id);
    dataSet.setName("Test DataSet");
    return dataSet;
  }

  // ---------------------------------------------------------------------------
  // createFromApiUrlAndDataSet — URL/Regex parsing
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("createFromApiUrlAndDataSet() — URL parsing")
  class CreateFromApiUrlTests {

    @Test
    @DisplayName("Should strip full URL with version prefix")
    void shouldStripFullUrlWithVersionPrefix() {
      DistributionService service = createService();
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      when(distributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      Distribution result =
          service.createFromApiUrlAndDataSet("http://frost:8080/FROST-Server/v1.1/Things", dataSet);

      assertThat(result.getAccessUrl()).isEqualTo("/Things");
    }

    @Test
    @DisplayName("Should strip relative path with version")
    void shouldStripRelativePathWithVersion() {
      DistributionService service = createService();
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      when(distributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      Distribution result = service.createFromApiUrlAndDataSet("/v1.1/Things", dataSet);

      assertThat(result.getAccessUrl()).isEqualTo("/Things");
    }

    @Test
    @DisplayName("Should handle nested SensorThings path")
    void shouldHandleNestedSensorThingsPath() {
      DistributionService service = createService();
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      when(distributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      Distribution result =
          service.createFromApiUrlAndDataSet(
              "http://frost:8080/FROST-Server/v1.1/Things/Datastreams", dataSet);

      assertThat(result.getAccessUrl()).isEqualTo("/Things/Datastreams");
    }

    @Test
    @DisplayName("Should handle different version numbers")
    void shouldHandleDifferentVersionNumbers() {
      DistributionService service = createService();
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      when(distributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      Distribution result = service.createFromApiUrlAndDataSet("/v2.0/Sensors", dataSet);

      assertThat(result.getAccessUrl()).isEqualTo("/Sensors");
    }

    @Test
    @DisplayName("Should return slash when only version in path")
    void shouldReturnSlashWhenOnlyVersionInPath() {
      DistributionService service = createService();
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      when(distributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      Distribution result = service.createFromApiUrlAndDataSet("/v1.1", dataSet);

      assertThat(result.getAccessUrl()).isEqualTo("/");
    }

    @Test
    @DisplayName("Should preserve path when no version match")
    void shouldPreservePathWhenNoVersionMatch() {
      DistributionService service = createService();
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      when(distributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      Distribution result = service.createFromApiUrlAndDataSet("/api/data", dataSet);

      assertThat(result.getAccessUrl()).isEqualTo("/api/data");
    }

    @Test
    @DisplayName("Should prepend slash if missing after regex")
    void shouldPrependSlashIfMissing() {
      DistributionService service = createService();
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      when(distributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // "api/data" has no leading slash and no version match
      Distribution result = service.createFromApiUrlAndDataSet("api/data", dataSet);

      assertThat(result.getAccessUrl()).startsWith("/");
    }

    @Test
    @DisplayName("Should handle full URL without resource path after version")
    void shouldHandleFullUrlWithVersionOnly() {
      DistributionService service = createService();
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      when(distributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      Distribution result =
          service.createFromApiUrlAndDataSet("http://frost:8080/FROST-Server/v1.1", dataSet);

      assertThat(result.getAccessUrl()).isEqualTo("/");
    }
  }

  // ---------------------------------------------------------------------------
  // createFromApiUrlAndDataSet — properties and persistence
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("createFromApiUrlAndDataSet() — properties")
  class CreateFromApiUrlPropertiesTests {

    @Test
    @DisplayName("Should set apiType to SensorThings")
    void shouldSetApiTypeToSensorThings() {
      DistributionService service = createService();
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      when(distributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      Distribution result = service.createFromApiUrlAndDataSet("/v1.1/Things", dataSet);

      assertThat(result.getApiType()).isEqualTo("SensorThings");
    }

    @Test
    @DisplayName("Should set format to application/json")
    void shouldSetFormatToApplicationJson() {
      DistributionService service = createService();
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      when(distributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      Distribution result = service.createFromApiUrlAndDataSet("/v1.1/Things", dataSet);

      assertThat(result.getFormat()).isEqualTo("application/json");
    }

    @Test
    @DisplayName("Should mark distribution as auto-generated")
    void shouldSetAutoGeneratedTrue() {
      DistributionService service = createService();
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      when(distributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      Distribution result = service.createFromApiUrlAndDataSet("/v1.1/Things", dataSet);

      assertThat(result.getAutoGenerated()).isTrue();
    }

    @Test
    @DisplayName("Should associate distribution with dataset")
    void shouldAssociateWithDataSet() {
      DistributionService service = createService();
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      when(distributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      Distribution result = service.createFromApiUrlAndDataSet("/v1.1/Things", dataSet);

      assertThat(result.getDataSet()).isSameAs(dataSet);
    }

    @Test
    @DisplayName("Should save and return distribution")
    void shouldSaveAndReturnDistribution() {
      DistributionService service = createService();
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      when(distributionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      Distribution result = service.createFromApiUrlAndDataSet("/v1.1/Things", dataSet);

      ArgumentCaptor<Distribution> captor = ArgumentCaptor.forClass(Distribution.class);
      verify(distributionRepository).save(captor.capture());
      assertThat(captor.getValue()).isSameAs(result);
    }
  }

  // ---------------------------------------------------------------------------
  // postConvertToEntity — resource association
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("postConvertToEntity()")
  class PostConvertToEntityTests {

    @Test
    @DisplayName("Should set resource when resourceId is provided")
    void shouldSetResourceFromInput() {
      DistributionService service = createService();
      UUID resourceId = UUID.randomUUID();
      Resource resource = new Resource();
      resource.setId(resourceId);

      Distribution entity = new Distribution();
      DistributionInputDTO input = new DistributionInputDTO();
      input.setResourceId(resourceId);
      when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));

      Distribution result = service.postConvertToEntity(entity, input);

      assertThat(result.getResource()).isSameAs(resource);
    }

    @Test
    @DisplayName("Should set resource to null when resourceId is null")
    void shouldSetResourceNullWhenResourceIdNull() {
      DistributionService service = createService();
      Distribution entity = new Distribution();
      DistributionInputDTO input = new DistributionInputDTO();
      input.setResourceId(null);

      Distribution result = service.postConvertToEntity(entity, input);

      assertThat(result.getResource()).isNull();
    }
  }
}
