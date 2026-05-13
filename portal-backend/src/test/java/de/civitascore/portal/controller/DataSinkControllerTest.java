package de.civitascore.portal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.output.DataSinkOutputDTO;
import de.civitascore.portal.model.output.assembler.DataSinkAssembler;
import de.civitascore.portal.security.AllowedScopes;
import de.civitascore.portal.security.dto.PrincipalUserDetails;
import de.civitascore.portal.service.DataSinkService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = DataSinkController.class,
    excludeAutoConfiguration = {OAuth2ResourceServerAutoConfiguration.class})
@ContextConfiguration(classes = {DataSinkController.class})
class DataSinkControllerTest {

  private static final UUID DATASET_ID = UUID.randomUUID();
  private static final UUID SINK_ID = UUID.randomUUID();
  private static final UUID PIPELINE_ID = UUID.randomUUID();
  private static final String BASE_PATH = "/datasets/" + DATASET_ID + "/datasinks";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DataSinkService dataSinkService;
  @MockitoBean private DataSinkAssembler dataSinkAssembler;
  @MockitoBean private AllowedScopes allowedScopes;

  private Authentication auth;
  private DataSinkOutputDTO sampleOutput;

  @BeforeEach
  void setUp() {
    PrincipalUserDetails userDetails =
        PrincipalUserDetails.builder()
            .userId(UUID.randomUUID())
            .username("testuser")
            .email("test@example.com")
            .givenName("Test")
            .familyName("User")
            .authorities(List.of())
            .build();
    auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

    sampleOutput = new DataSinkOutputDTO();
    sampleOutput.setId(SINK_ID);
    sampleOutput.setDataSetId(DATASET_ID);
    sampleOutput.setPipelineId(PIPELINE_ID);
    sampleOutput.setDataSinkType(DataSinkType.FROST);

    doCallRealMethod().when(dataSinkAssembler).toOutput(any(Page.class));
  }

  @Nested
  @DisplayName("GET /datasinks")
  class ListDataSinks {

    @Test
    @DisplayName("Should return 200 with a page of DataSinks")
    void shouldReturnPage() throws Exception {
      when(dataSinkService.findAll(any(), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of(new DataSink())));
      when(dataSinkAssembler.toOutput(any(DataSink.class))).thenReturn(sampleOutput);

      mockMvc
          .perform(
              get(BASE_PATH).with(authentication(auth)).contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[0].id").value(SINK_ID.toString()))
          .andExpect(jsonPath("$.content[0].dataSinkType").value("FROST"));
    }
  }

  @Nested
  @DisplayName("GET /datasinks/{id}")
  class GetDataSink {

    @Test
    @DisplayName("Should return 200 with the DataSink")
    void shouldReturnDataSink() throws Exception {
      DataSink sink = new DataSink();
      sink.setId(SINK_ID);

      when(dataSinkService.findByIdAndDataSetOrThrow(SINK_ID, DATASET_ID)).thenReturn(sink);
      when(dataSinkAssembler.toOutput(sink)).thenReturn(sampleOutput);

      mockMvc
          .perform(
              get(BASE_PATH + "/" + SINK_ID)
                  .with(authentication(auth))
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(SINK_ID.toString()))
          .andExpect(jsonPath("$.pipelineId").value(PIPELINE_ID.toString()));
    }
  }

  @Nested
  @DisplayName("POST /datasinks")
  class CreateDataSink {

    @Test
    @DisplayName("Should return 201 with location header and created DataSink")
    void shouldCreateAndReturn201() throws Exception {
      DataSink created = new DataSink();
      created.setId(SINK_ID);

      when(dataSinkService.create(any())).thenReturn(created);
      when(dataSinkAssembler.toOutput(created)).thenReturn(sampleOutput);

      String body =
          """
          {
            "dataSinkType": "FROST",
            "pipelineId": "%s",
            "configuration": {}
          }
          """
              .formatted(PIPELINE_ID);

      mockMvc
          .perform(
              post(BASE_PATH)
                  .with(authentication(auth))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").value(SINK_ID.toString()))
          .andExpect(jsonPath("$.dataSinkType").value("FROST"));
    }
  }

  @Nested
  @DisplayName("PUT /datasinks/{id}")
  class UpdateDataSink {

    @Test
    @DisplayName("Should return 200 with updated DataSink")
    void shouldUpdateAndReturn200() throws Exception {
      DataSink existing = new DataSink();
      existing.setId(SINK_ID);
      DataSink updated = new DataSink();
      updated.setId(SINK_ID);

      when(dataSinkService.findByIdAndDataSetOrThrow(SINK_ID, DATASET_ID)).thenReturn(existing);
      when(dataSinkService.update(any(), any())).thenReturn(updated);
      when(dataSinkAssembler.toOutput(updated)).thenReturn(sampleOutput);

      String body =
          """
          {
            "dataSinkType": "FROST",
            "pipelineId": "%s",
            "configuration": {}
          }
          """
              .formatted(PIPELINE_ID);

      mockMvc
          .perform(
              put(BASE_PATH + "/" + SINK_ID)
                  .with(authentication(auth))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(SINK_ID.toString()));
    }
  }

  @Nested
  @DisplayName("DELETE /datasinks/{id}")
  class DeleteDataSink {

    @Test
    @DisplayName("Should return 204 after deletion")
    void shouldDeleteAndReturn204() throws Exception {
      DataSink existing = new DataSink();
      existing.setId(SINK_ID);

      when(dataSinkService.findByIdAndDataSetOrThrow(SINK_ID, DATASET_ID)).thenReturn(existing);
      doNothing().when(dataSinkService).deleteById(SINK_ID);

      mockMvc
          .perform(
              delete(BASE_PATH + "/" + SINK_ID)
                  .with(authentication(auth))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isNoContent());
    }
  }

  @Nested
  @DisplayName("PATCH /datasinks/{id}")
  class PatchDataSink {

    @Test
    @DisplayName("Should return 405 since PATCH is not supported")
    void shouldReturn405ForPatch() throws Exception {
      mockMvc
          .perform(
              patch(BASE_PATH + "/" + SINK_ID)
                  .with(authentication(auth))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isMethodNotAllowed());
    }
  }
}
