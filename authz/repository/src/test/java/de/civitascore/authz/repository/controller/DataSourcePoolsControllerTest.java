package de.civitascore.authz.repository.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.civitascore.authz.repository.model.dto.DataSourcePoolsResponse;
import de.civitascore.authz.repository.service.DataSourcePoolsService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins the wire contract of the datasource-pools endpoint.
 *
 * <p>The JSON field name {@code poolIds} is the sole coupling to the OPA policy, which reads it by
 * name from an untyped body, so a rename would silently deny every pool-inherited read. Asserted on
 * the raw response rather than through the DTO, which would change along with the field.
 */
@WebMvcTest(controllers = DataSourcePoolsController.class)
@ContextConfiguration(classes = {DataSourcePoolsController.class, ValidationExceptionHandler.class})
@DisplayName("DataSourcePools Controller Tests")
class DataSourcePoolsControllerTest {

  private static final String DATA_SOURCE_ID = "11111111-1111-1111-1111-111111111111";
  private static final UUID POOL_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DataSourcePoolsService dataSourcePoolsService;

  @Test
  @DisplayName("Serializes the assigned pools under the poolIds field OPA reads")
  void getDataSourcePools_whenAssigned_serializesPoolIdsField() throws Exception {
    when(dataSourcePoolsService.getDataSourcePools(any()))
        .thenReturn(Optional.of(new DataSourcePoolsResponse(List.of(POOL_ID))));

    mockMvc
        .perform(get("/api/v1/datasource-pools/{id}", DATA_SOURCE_ID))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"poolIds\":[\"" + POOL_ID + "\"]}", true));
  }

  @Test
  @DisplayName(
      "Serializes an unassigned data source as an empty poolIds array, not a missing field")
  void getDataSourcePools_whenUnassigned_serializesEmptyArray() throws Exception {
    when(dataSourcePoolsService.getDataSourcePools(any()))
        .thenReturn(Optional.of(new DataSourcePoolsResponse(List.of())));

    mockMvc
        .perform(get("/api/v1/datasource-pools/{id}", DATA_SOURCE_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.poolIds").isArray())
        .andExpect(jsonPath("$.poolIds").isEmpty());
  }

  @Test
  @DisplayName("Returns 404 for an unknown data source")
  void getDataSourcePools_whenUnknown_returnsNotFound() throws Exception {
    when(dataSourcePoolsService.getDataSourcePools(any())).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/datasource-pools/{id}", DATA_SOURCE_ID))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Rejects a malformed data source id before reaching the service")
  void getDataSourcePools_whenIdMalformed_returnsBadRequest() throws Exception {
    mockMvc
        .perform(get("/api/v1/datasource-pools/{id}", "not-a-uuid"))
        .andExpect(status().isBadRequest());
  }
}
