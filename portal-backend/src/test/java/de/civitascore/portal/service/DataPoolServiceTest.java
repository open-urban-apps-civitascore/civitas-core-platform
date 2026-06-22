package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataPoolMapper;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.DataPoolInputDTO;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.UserRepository;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataPoolService Tests")
class DataPoolServiceTest {

  @Mock private DataPoolRepository dataPoolRepository;
  @Mock private DataPoolMapper dataPoolMapper;
  @Mock private DataSetRepository dataSetRepository;
  @Mock private DataSourceRepository dataSourceRepository;
  @Mock private UserRepository userRepository;
  @Mock private AssignmentFactory assignmentFactory;

  @InjectMocks private DataPoolService dataPoolService;

  @Nested
  @DisplayName("postConvertToEntity — contact person resolution")
  class PostConvertToEntityTests {

    @Test
    @DisplayName("Should set contact person when valid contactPersonId is provided")
    void shouldSetContactPersonWhenValidId() {
      UUID userId = UUID.randomUUID();
      User user = new User();
      user.setId(userId);

      DataPoolInputDTO input = new DataPoolInputDTO();
      input.setName("Pool A");
      input.setDescription("desc");
      input.setContactPersonId(userId);

      DataPool entity = new DataPool();
      when(dataPoolMapper.toEntity(any())).thenReturn(entity);
      when(dataPoolRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(userRepository.findById(userId)).thenReturn(Optional.of(user));

      DataPool result = dataPoolService.create(input);

      assertThat(result.getContactPerson()).isEqualTo(user);
    }

    @Test
    @DisplayName("Should set contact person to null when contactPersonId is null")
    void shouldSetContactPersonToNullWhenIdIsNull() {
      DataPoolInputDTO input = new DataPoolInputDTO();
      input.setName("Pool B");
      input.setDescription("desc");
      input.setContactPersonId(null);

      DataPool entity = new DataPool();
      when(dataPoolMapper.toEntity(any())).thenReturn(entity);
      when(dataPoolRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataPool result = dataPoolService.create(input);

      assertThat(result.getContactPerson()).isNull();
      verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName(
        "Should throw ResourceNotFoundException when contactPersonId references unknown user")
    void shouldThrowWhenContactPersonIdUnknown() {
      UUID unknownId = UUID.randomUUID();

      DataPoolInputDTO input = new DataPoolInputDTO();
      input.setName("Pool C");
      input.setDescription("desc");
      input.setContactPersonId(unknownId);

      DataPool entity = new DataPool();
      when(dataPoolMapper.toEntity(any())).thenReturn(entity);
      when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> dataPoolService.create(input))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("update — contact person resolution")
  class UpdateContactPersonTests {

    @Test
    @DisplayName("Should set contact person when valid contactPersonId is provided")
    void shouldSetContactPersonWhenValidId() {
      UUID id = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      User user = new User();
      user.setId(userId);

      DataPool existing = new DataPool();
      existing.setId(id);

      DataPoolInputDTO input = new DataPoolInputDTO();
      input.setName("Pool A updated");
      input.setContactPersonId(userId);

      when(dataPoolRepository.findById(id)).thenReturn(Optional.of(existing));
      when(dataPoolRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(userRepository.findById(userId)).thenReturn(Optional.of(user));

      DataPool result = dataPoolService.update(id, input);

      assertThat(result.getContactPerson()).isEqualTo(user);
    }

    @Test
    @DisplayName("Should clear contact person when contactPersonId is null")
    void shouldClearContactPersonWhenIdIsNull() {
      UUID id = UUID.randomUUID();

      DataPool existing = new DataPool();
      existing.setId(id);
      User previousContact = new User();
      previousContact.setId(UUID.randomUUID());
      existing.setContactPerson(previousContact);

      DataPoolInputDTO input = new DataPoolInputDTO();
      input.setName("Pool B updated");
      input.setContactPersonId(null);

      when(dataPoolRepository.findById(id)).thenReturn(Optional.of(existing));
      when(dataPoolRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataPool result = dataPoolService.update(id, input);

      assertThat(result.getContactPerson()).isNull();
      verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName(
        "Should throw ResourceNotFoundException when contactPersonId references unknown user")
    void shouldThrowWhenContactPersonIdUnknown() {
      UUID id = UUID.randomUUID();
      UUID unknownId = UUID.randomUUID();

      DataPool existing = new DataPool();
      existing.setId(id);

      DataPoolInputDTO input = new DataPoolInputDTO();
      input.setName("Pool C updated");
      input.setContactPersonId(unknownId);

      when(dataPoolRepository.findById(id)).thenReturn(Optional.of(existing));
      when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> dataPoolService.update(id, input))
          .isInstanceOf(ResourceNotFoundException.class);

      verify(dataPoolRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("deleteById — dataset assignment constraint")
  class DeleteByIdTests {

    @Test
    @DisplayName("Should throw ResourceNotFoundException when pool does not exist")
    void shouldThrowWhenPoolNotFound() {
      UUID id = UUID.randomUUID();
      when(dataPoolRepository.findById(id)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> dataPoolService.deleteById(id))
          .isInstanceOf(ResourceNotFoundException.class);

      verify(dataSetRepository, never()).existsByDataPoolId(any());
      verify(dataPoolRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Should throw ResourceInUseException when datasets are still assigned")
    void shouldThrowWhenDatasetsAreAssigned() {
      UUID id = UUID.randomUUID();
      DataPool pool = new DataPool();
      pool.setId(id);

      when(dataPoolRepository.findById(id)).thenReturn(Optional.of(pool));
      when(dataSetRepository.existsByDataPoolId(id)).thenReturn(true);

      assertThatThrownBy(() -> dataPoolService.deleteById(id))
          .isInstanceOf(ResourceInUseException.class)
          .hasMessageContaining("Dataset");

      verify(dataPoolRepository, never()).deleteById(id);
    }

    @Test
    @DisplayName("Should delete successfully when no datasets are assigned")
    void shouldDeleteSuccessfullyWhenNoDatasetsAssigned() {
      UUID id = UUID.randomUUID();
      DataPool pool = new DataPool();
      pool.setId(id);

      when(dataPoolRepository.findById(id)).thenReturn(Optional.of(pool));
      when(dataSetRepository.existsByDataPoolId(id)).thenReturn(false);
      when(dataSourceRepository.existsByScopedDataPools_Id(id)).thenReturn(true);

      assertThatThrownBy(() -> dataPoolService.deleteById(id))
          .isInstanceOf(ResourceInUseException.class)
          .hasMessageContaining("DataSource");

      verify(dataPoolRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("deletes DataPool when no constraints are violated")
    void deletesPool_whenNoConstraintsViolated() {
      UUID id = UUID.randomUUID();
      DataPool pool = new DataPool();
      pool.setId(id);

      when(dataPoolRepository.findById(id)).thenReturn(Optional.of(pool));
      when(dataSetRepository.existsByDataPoolId(id)).thenReturn(false);
      when(dataSourceRepository.existsByScopedDataPools_Id(id)).thenReturn(false);
      when(dataPoolRepository.existsById(id)).thenReturn(true);

      assertThatNoException().isThrownBy(() -> dataPoolService.deleteById(id));

      verify(dataPoolRepository).deleteById(id);
    }
  }
}
