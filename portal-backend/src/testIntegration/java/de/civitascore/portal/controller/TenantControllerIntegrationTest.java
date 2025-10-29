package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.entity.Tenant;
import de.civitascore.portal.model.input.TenantInputDTO;
import de.civitascore.portal.model.output.TenantOutputDTO;
import de.civitascore.portal.repository.TenantRepository;
import de.civitascore.portal.util.RestPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("Tenant Controller Integration Tests")
class TenantControllerIntegrationTest
    extends BaseControllerIntegrationTest<TenantInputDTO, TenantOutputDTO, String> {

  @Autowired private TenantRepository tenantRepository;

  @Override
  protected String getEndpointPath() {
    return "/tenants";
  }

  @Override
  protected TenantInputDTO createValidInput() {
    TenantInputDTO input = new TenantInputDTO();
    input.setName("Test Tenant " + System.currentTimeMillis());
    input.setDescription("A test tenant for integration testing");
    input.setActive(true);
    return input;
  }

  @Override
  protected TenantInputDTO createInvalidInput() {
    TenantInputDTO input = new TenantInputDTO();
    input.setDescription("Invalid tenant without name");
    return input;
  }

  @Override
  protected TenantInputDTO createUpdateInput() {
    TenantInputDTO input = new TenantInputDTO();
    input.setName("Updated Tenant " + System.currentTimeMillis());
    input.setDescription("Updated description");
    input.setActive(true);
    return input;
  }

  @Override
  protected ParameterizedTypeReference<TenantOutputDTO> getOutputTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected ParameterizedTypeReference<RestPage<TenantOutputDTO>> getPageTypeReference() {
    return new ParameterizedTypeReference<>() {};
  }

  @Override
  protected String getIdFromOutput(TenantOutputDTO output) {
    return output.getId();
  }

  @Override
  protected String createTestEntity() {
    Tenant tenant = new Tenant();
    tenant.setName("Test Tenant " + System.currentTimeMillis());
    tenant.setDescription("A test tenant for integration testing");
    tenant.setActive(true);
    Tenant saved = tenantRepository.save(tenant);
    createdEntityIds.add(saved.getId());
    return saved.getId();
  }

  @Test
  @DisplayName("Should return NO_CONTENT when creating tenant")
  void shouldCreateTenant() {
    TenantInputDTO input = createValidInput();
    ResponseEntity<TenantOutputDTO> response = performCreate(input, false);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  @DisplayName("Should retrieve tenant by ID")
  void shouldGetTenantById() {
    String tenantId = createTestEntity();

    ResponseEntity<TenantOutputDTO> response = performGetById(tenantId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getId()).isEqualTo(tenantId);
  }

  @Test
  @DisplayName("Should retrieve all tenants")
  void shouldGetAllTenants() {
    createTestEntity();

    ResponseEntity<RestPage<TenantOutputDTO>> response = performGetAll();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getContent()).isNotEmpty();
  }

  @Test
  @DisplayName("Should update tenant")
  void shouldUpdateTenant() {
    String tenantId = createTestEntity();
    TenantInputDTO updateInput = createUpdateInput();

    ResponseEntity<TenantOutputDTO> response = performUpdate(tenantId, updateInput);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getName()).isEqualTo(updateInput.getName());
  }

  @Test
  @DisplayName("Should delete tenant")
  void shouldDeleteTenant() {
    String tenantId = createTestEntity();

    ResponseEntity<Void> response = performDelete(tenantId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(performGetById(tenantId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("Should fail with invalid input")
  void shouldFailWithInvalidInput() {
    TenantInputDTO input = createInvalidInput();

    ResponseEntity<TenantOutputDTO> response = performCreate(input, false);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
