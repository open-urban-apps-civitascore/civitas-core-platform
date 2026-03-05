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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.User;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.model.output.summary.UserSummaryDTO;
import de.civitascore.portal.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSetAssemblerTest {

  @Mock private DataSetMapper dataSetMapper;
  @Mock private UserRepository userRepository;
  @Mock private UserMapper userMapper;

  @InjectMocks private DataSetAssembler assembler;

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

    DataSetOutputDTO result = assembler.enrichDto(new DataSetOutputDTO(), dataSet);

    assertThat(result.getCreatedBy()).isNotNull();
    assertThat(result.getCreatedBy().getName()).isEqualTo("Max Mustermann");
  }

  @Test
  void createdByIsNullWhenCreatedByNotSet() {
    DataSetOutputDTO result = assembler.enrichDto(new DataSetOutputDTO(), new DataSet());

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

    DataSetOutputDTO result = assembler.enrichDto(new DataSetOutputDTO(), dataSet);

    assertThat(result.getCreatedBy()).isNull();
  }
}
