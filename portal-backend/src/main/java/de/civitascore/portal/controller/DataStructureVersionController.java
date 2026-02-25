package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.output.DataStructureVersionOutputDTO;
import de.civitascore.portal.model.output.assembler.DataStructureVersionAssembler;
import de.civitascore.portal.repository.specification.DataStructureVersionSpec;
import de.civitascore.portal.service.DataStructureVersionService;
import de.civitascore.portal.util.InvalidInputException;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.MethodNotAllowedException;

@RestController
@RequestMapping("/datastructures/{dataStructureId}/versions")
@RequiredArgsConstructor
@Tag(name = "Data Structure Versions", description = "Data structure version management endpoints")
public class DataStructureVersionController
    extends BaseController<
        DataStructureVersionInputDTO,
        DataStructureVersionOutputDTO,
        DataStructureVersion,
        DataStructureVersionSpec> {

  private final DataStructureVersionService dataStructureVersionService;
  private final DataStructureVersionAssembler dataStructureVersionAssembler;

  @Override
  public ResponseEntity<Page<DataStructureVersionOutputDTO>> getAll(
      @ParameterObject DataStructureVersionSpec spec, @ParameterObject Pageable pageable) {
    throw new MethodNotAllowedException(HttpMethod.GET, Collections.emptySet());
  }

  @Override
  protected DataStructureVersionService getService() {
    return dataStructureVersionService;
  }

  @Override
  protected DataStructureVersionAssembler getAssembler() {
    return dataStructureVersionAssembler;
  }

  @Override
  @GetMapping("/{id}")
  public ResponseEntity<DataStructureVersionOutputDTO> getById(@PathVariable UUID id) {
    DataStructureVersion entity = getService().findByIdOrThrow(id);
    DataStructureVersionOutputDTO output = getAssembler().toOutput(entity);
    getService().findModelForDataStructureVersion(entity).ifPresent(output::setModel);
    return ResponseEntity.ok(output);
  }

  @Override
  protected DataStructureVersionInputDTO preProcessInput(DataStructureVersionInputDTO input) {
    Optional.of(extractPathVariables().get("dataStructureId"))
        .map(UUID::fromString)
        .ifPresentOrElse(
            input::setDataStructureId,
            () -> {
              throw new InvalidInputException(
                  "DataStructureVersion",
                  "dataStructureId",
                  "Missing or invalid dataStructureId in path variables");
            });
    return super.preProcessInput(input);
  }
}
