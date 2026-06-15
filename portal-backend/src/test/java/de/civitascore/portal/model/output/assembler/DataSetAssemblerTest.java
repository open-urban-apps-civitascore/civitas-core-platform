/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors:
 *
 * <p>Copyright (c) 2025 ORGANISATION/PERSON and others.
 */
package de.civitascore.portal.model.output.assembler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.civitascore.portal.configuration.CivitasProperties;
import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.model.output.NamedApiOutputDTO;
import de.civitascore.portal.model.output.summary.UserSummaryDTO;
import de.civitascore.portal.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSetAssemblerTest {

  @Mock private DataSetMapper dataSetMapper;
  @Mock private UserRepository userRepository;
  @Mock private UserMapper userMapper;

  private final CivitasProperties civitasProperties =
      new CivitasProperties("test-key", new CivitasProperties.Api("https://api.example.com"));

  private DataSetAssembler assembler() {
    return new DataSetAssembler(dataSetMapper, userRepository, userMapper, civitasProperties);
  }

  private NamedApiOutputDTO namedApiDto(String slug) {
    NamedApiOutputDTO dto = new NamedApiOutputDTO();
    dto.setName("API " + slug);
    dto.setSlug(slug);
    dto.setStandard(ApiStandard.STA);
    return dto;
  }

  @Test
  void mapsCreatedByWhenUserExists() {
    UUID creatorKeycloakId = UUID.randomUUID();
    DataSet dataSet = new DataSet();
    dataSet.setCreatedBy(creatorKeycloakId);

    User user = new User();
    user.setFirstName("Max");
    user.setLastName("Mustermann");

    UserSummaryDTO summary = new UserSummaryDTO();
    summary.setName("Max Mustermann");

    when(userRepository.findByExternalId(creatorKeycloakId.toString()))
        .thenReturn(Optional.of(user));
    when(userMapper.toSummary(user)).thenReturn(summary);

    DataSetOutputDTO result = assembler().enrichDto(new DataSetOutputDTO(), dataSet);

    assertThat(result.getCreatedBy()).isNotNull();
    assertThat(result.getCreatedBy().getName()).isEqualTo("Max Mustermann");
  }

  @Test
  void createdByIsNullWhenCreatedByNotSet() {
    DataSetOutputDTO result = assembler().enrichDto(new DataSetOutputDTO(), new DataSet());

    assertThat(result.getCreatedBy()).isNull();
    verifyNoInteractions(userRepository, userMapper);
  }

  @Test
  void createdByIsNullWhenUserNotFound() {
    UUID creatorKeycloakId = UUID.randomUUID();
    DataSet dataSet = new DataSet();
    dataSet.setCreatedBy(creatorKeycloakId);

    when(userRepository.findByExternalId(creatorKeycloakId.toString()))
        .thenReturn(Optional.empty());

    DataSetOutputDTO result = assembler().enrichDto(new DataSetOutputDTO(), dataSet);

    assertThat(result.getCreatedBy()).isNull();
  }

  @Test
  void populatesPerSlugPreviewUrlOnEachNamedApi() {
    UUID dataSetId = UUID.fromString("b7c8b5d4-3d9c-4e3b-9a12-6b7c3f1d9e2a");
    DataSet dataSet = new DataSet();
    dataSet.setId(dataSetId);

    DataSetOutputDTO dto = new DataSetOutputDTO();
    dto.setNamedApis(List.of(namedApiDto("traffic"), namedApiDto("weather")));

    DataSetOutputDTO enriched = assembler().enrichDto(dto, dataSet);

    assertThat(enriched.getNamedApis())
        .extracting(NamedApiOutputDTO::getSlug, NamedApiOutputDTO::getPreviewUrl)
        .containsExactlyInAnyOrder(
            tuple(
                "traffic",
                "https://api.example.com/v1/datasets/b7c8b5d4-3d9c-4e3b-9a12-6b7c3f1d9e2a/traffic"),
            tuple(
                "weather",
                "https://api.example.com/v1/datasets/b7c8b5d4-3d9c-4e3b-9a12-6b7c3f1d9e2a/weather"));
  }

  @Test
  void prefersSagaProvisionedPublicUrlOverBaseUrl() {
    UUID dataSetId = UUID.fromString("b7c8b5d4-3d9c-4e3b-9a12-6b7c3f1d9e2a");
    DataSet dataSet = new DataSet();
    dataSet.setId(dataSetId);
    // The saga provisioned the APISIX route under APISIX_API_PUBLIC_URL, which can differ from the
    // backend-local civitas.api.base-url (https://api.example.com here). The authoritative public
    // base must win so preview/discovery links point at the real gateway route, not a divergent
    // backend-configured host.
    dataSet.setPublicUrl("https://api.localhost:9080/v1/datasets/" + dataSetId);

    DataSetOutputDTO dto = new DataSetOutputDTO();
    dto.setNamedApis(List.of(namedApiDto("traffic"), namedApiDto("weather")));

    assembler().enrichDto(dto, dataSet);

    assertThat(dto.getNamedApis())
        .extracting(NamedApiOutputDTO::getSlug, NamedApiOutputDTO::getPreviewUrl)
        .containsExactlyInAnyOrder(
            tuple("traffic", "https://api.localhost:9080/v1/datasets/" + dataSetId + "/traffic"),
            tuple("weather", "https://api.localhost:9080/v1/datasets/" + dataSetId + "/weather"));
  }

  @Test
  void fallsBackToBaseUrlWhenPublicUrlAbsent() {
    UUID dataSetId = UUID.fromString("b7c8b5d4-3d9c-4e3b-9a12-6b7c3f1d9e2a");
    DataSet dataSet = new DataSet();
    dataSet.setId(dataSetId);
    // publicUrl null (e.g. dataset predates the saga) → fall back to civitas.api.base-url.

    DataSetOutputDTO dto = new DataSetOutputDTO();
    dto.setNamedApis(List.of(namedApiDto("traffic")));

    assembler().enrichDto(dto, dataSet);

    assertThat(dto.getNamedApis().get(0).getPreviewUrl())
        .isEqualTo("https://api.example.com/v1/datasets/" + dataSetId + "/traffic");
  }

  @Test
  void doesNotPopulatePreviewUrlWhenDataSetHasNoId() {
    DataSet dataSet = new DataSet(); // not persisted yet

    DataSetOutputDTO dto = new DataSetOutputDTO();
    dto.setNamedApis(List.of(namedApiDto("traffic")));

    DataSetOutputDTO enriched = assembler().enrichDto(dto, dataSet);

    assertThat(enriched.getNamedApis()).hasSize(1);
    assertThat(enriched.getNamedApis().get(0).getPreviewUrl())
        .as("previewUrl is absent until the dataset is persisted (no id, no URL)")
        .isNull();
  }

  @ParameterizedTest(name = "[{index}] baseUrl={0}")
  @CsvSource({
    "https://api.example.com,          https://api.example.com",
    "https://api.example.com:8443,     https://api.example.com:8443",
    "https://api.test.example.com,     https://api.test.example.com",
    "https://localhost:8089,           https://localhost:8089",
  })
  void buildsPreviewUrlAcrossSupportedBaseUrls(String baseUrl, String expectedPrefix) {
    // Locks the contract: civitas.api.base-url MUST be a fully-qualified HTTPS URL with no path
    // and no trailing slash. The assembler appends "/v1/datasets/..." to it directly; any other
    // shape would produce malformed URLs in production.
    CivitasProperties props = new CivitasProperties("test-key", new CivitasProperties.Api(baseUrl));
    DataSetAssembler customAssembler =
        new DataSetAssembler(dataSetMapper, userRepository, userMapper, props);

    UUID dataSetId = UUID.randomUUID();
    DataSet dataSet = new DataSet();
    dataSet.setId(dataSetId);

    DataSetOutputDTO dto = new DataSetOutputDTO();
    dto.setNamedApis(List.of(namedApiDto("traffic")));

    customAssembler.enrichDto(dto, dataSet);

    assertThat(dto.getNamedApis().get(0).getPreviewUrl())
        .isEqualTo(expectedPrefix + "/v1/datasets/" + dataSetId + "/traffic");
  }

  @Test
  void emptyNamedApisListIsLeftAlone() {
    DataSet dataSet = new DataSet();
    dataSet.setId(UUID.randomUUID());

    DataSetOutputDTO dto = new DataSetOutputDTO();
    // namedApis defaults to empty ArrayList from the DTO constructor

    DataSetOutputDTO enriched = assembler().enrichDto(dto, dataSet);

    assertThat(enriched.getNamedApis()).isEmpty();
  }
}
