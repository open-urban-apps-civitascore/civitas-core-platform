package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.input.DataPoolInputDTO;
import de.civitascore.portal.model.output.DataPoolOutputDTO;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.RestPage;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@DisplayName("DataPool Controller Integration Tests")
class DataPoolControllerIntegrationTest
    extends BaseDataEntityControllerIntegrationTest<DataPoolInputDTO, DataPoolOutputDTO> {

  private static final String DATAPOOLS_ENDPOINT = "/datapools";

  @Autowired protected PortalTestDataFactory portalData;
  @Autowired private DataPoolRepository dataPoolRepository;
  @Autowired private DataSetRepository dataSetRepository;

  @Override
  protected String getEndpointPath() {
    return DATAPOOLS_ENDPOINT;
  }

  @Override
  protected void performAdditionalCleanup() {
    portalData.cleanAll();
  }

  @Override
  protected DataPoolInputDTO createValidInput() {
    DataPoolInputDTO input = new DataPoolInputDTO();
    input.setName("test_datapool_" + UUID.randomUUID().toString().substring(0, 8));
    input.setDescription("A test datapool for integration testing");
    return input;
  }

  @Override
  protected DataPoolInputDTO createInvalidInput() {
    DataPoolInputDTO input = new DataPoolInputDTO();
    input.setDescription("Invalid datapool without required name");
    return input;
  }

  @Override
  protected DataPoolInputDTO createUpdateInput() {
    DataPoolInputDTO input = new DataPoolInputDTO();
    input.setName("updated_datapool");
    input.setDescription("Updated description");
    return input;
  }

  @Override
  protected ParameterizedTypeReference<DataPoolOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<DataPoolOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected UUID getIdFromOutput(DataPoolOutputDTO output) {
    return output.getId();
  }

  // --- DataPool-specific tests ---

  @Nested
  @DisplayName("Delete DataPool")
  class DeleteTests {

    @Test
    @DisplayName("Should return 409 Conflict when datasets are still assigned to the datapool")
    void shouldReturn409WhenDatasetsAreAssigned() {
      DataPool dataPool = portalData.dataPool();
      portalData.dataSet(b -> b.dataPool(dataPool));

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              DATAPOOLS_ENDPOINT + "/" + dataPool.getId(),
              org.springframework.http.HttpMethod.DELETE,
              createAuthHeaders(),
              null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
  }

  @Nested
  @DisplayName("Contact Person")
  class ContactPersonTests {

    @Test
    @DisplayName("Should persist and return contact person when set on creation")
    void shouldReturnContactPersonAfterCreation() {
      User user = portalData.user();

      DataPoolInputDTO input = createValidInput();
      input.setContactPersonId(user.getId());

      ResponseEntity<DataPoolOutputDTO> response = performCreate(input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getContactPerson()).isNotNull();
      assertThat(response.getBody().getContactPerson().getId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("Should return 404 when contactPersonId references unknown user")
    void shouldReturn404WhenContactPersonIdUnknown() {
      DataPoolInputDTO input = createValidInput();
      input.setContactPersonId(UUID.randomUUID());

      ResponseEntity<ProblemDetail> response =
          exchangeForProblem(
              DATAPOOLS_ENDPOINT,
              org.springframework.http.HttpMethod.POST,
              createAuthHeaders(),
              input);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("Filter by name")
  class FilterTests {

    @Test
    @DisplayName("Should return only matching datapools when filtering by name")
    void shouldFilterByName() {
      DataPool matchingPool = portalData.dataPool(b -> b.name("mobility-pool-unique-xyz"));
      portalData.dataPool(b -> b.name("other-pool-abc"));

      ResponseEntity<RestPage<DataPoolOutputDTO>> response =
          performGetAll(java.util.Map.of("name", "mobility-pool-unique-xyz"));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getContent())
          .extracting(DataPoolOutputDTO::getId)
          .containsExactly(matchingPool.getId());
    }
  }
}
