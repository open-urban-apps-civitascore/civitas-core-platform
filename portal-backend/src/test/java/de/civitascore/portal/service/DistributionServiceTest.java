package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DistributionMapper;
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
    @DisplayName("Should set resource to null when resourceId not found in repository")
    void shouldSetResourceNullWhenResourceIdNotFound() {
      DistributionService service = createService();
      UUID resourceId = UUID.randomUUID();

      Distribution entity = new Distribution();
      DistributionInputDTO input = new DistributionInputDTO();
      input.setResourceId(resourceId);
      when(resourceRepository.findById(resourceId)).thenReturn(Optional.empty());

      Distribution result = service.postConvertToEntity(entity, input);

      assertThat(result.getResource()).isNull();
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
