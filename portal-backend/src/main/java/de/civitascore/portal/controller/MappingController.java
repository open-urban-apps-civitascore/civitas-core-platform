package de.civitascore.portal.controller;

import de.civitascore.portal.model.input.MappingInputDTO;
import de.civitascore.portal.model.output.MappingOutputDTO;
import de.civitascore.portal.model.output.MappingSummaryDTO;
import de.civitascore.portal.model.output.assembler.MappingAssembler;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import de.civitascore.portal.service.MappingService;
import de.civitascore.portal.service.MappingService.StoredMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for the Mappings of a Dataset, addressed by the UUID of their CORE URN. */
@RestController
@RequestMapping("/datasets/{dataSetId}/mappings")
@RequiredArgsConstructor
@Tag(
    name = "Mappings",
    description = "Mappings of a Dataset; their content is stored in Model Forge")
public class MappingController {

  private final MappingService mappingService;
  private final MappingAssembler mappingAssembler;

  @GetMapping
  @Operation(summary = "List the Mappings of a Dataset")
  @Problems({ProblemType.NOT_FOUND, ProblemType.REGISTRY_UNAVAILABLE})
  public List<MappingSummaryDTO> listMappings(@PathVariable UUID dataSetId) {
    return mappingService.list(dataSetId).stream().map(mappingAssembler::toSummary).toList();
  }

  @GetMapping("/{mappingId}")
  @Operation(summary = "Get the latest version of a Mapping")
  @Problems({ProblemType.NOT_FOUND, ProblemType.REGISTRY_UNAVAILABLE})
  public MappingOutputDTO getMapping(@PathVariable UUID dataSetId, @PathVariable UUID mappingId) {
    return mappingAssembler.toOutput(mappingService.get(dataSetId, mappingId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a Mapping")
  @Problems({ProblemType.NOT_FOUND, ProblemType.INVALID_INPUT, ProblemType.REGISTRY_UNAVAILABLE})
  public ResponseEntity<MappingOutputDTO> createMapping(
      @PathVariable UUID dataSetId, @Valid @RequestBody MappingInputDTO input) {
    StoredMapping stored = mappingService.create(dataSetId, mappingAssembler.toDocument(input));
    return ResponseEntity.created(Locations.ofCreated(stored.mappingId()))
        .body(mappingAssembler.toOutput(stored));
  }

  @PutMapping("/{mappingId}")
  @Operation(
      summary = "Store a new version of a Mapping",
      description = "Every request stores a new version, also when the content did not change.")
  @Problems({ProblemType.NOT_FOUND, ProblemType.INVALID_INPUT, ProblemType.REGISTRY_UNAVAILABLE})
  public MappingOutputDTO updateMapping(
      @PathVariable UUID dataSetId,
      @PathVariable UUID mappingId,
      @Valid @RequestBody MappingInputDTO input) {
    return mappingAssembler.toOutput(
        mappingService.update(dataSetId, mappingId, mappingAssembler.toDocument(input)));
  }

  @DeleteMapping("/{mappingId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Delete a Mapping with all its versions",
      description =
          "Rejected while another artifact still references the mapping — a pipeline's mappingRef,"
              + " for instance. Remove that reference at the artifact holding it first; deleting a"
              + " mapping a pipeline still uses would leave the pipeline pointing at nothing.")
  @Problems({ProblemType.NOT_FOUND, ProblemType.RESOURCE_IN_USE, ProblemType.REGISTRY_UNAVAILABLE})
  public void deleteMapping(@PathVariable UUID dataSetId, @PathVariable UUID mappingId) {
    mappingService.delete(dataSetId, mappingId);
  }
}
