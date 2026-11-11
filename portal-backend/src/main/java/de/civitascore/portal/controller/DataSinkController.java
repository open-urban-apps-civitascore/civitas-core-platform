package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.output.DataSinkOutputDTO;
import de.civitascore.portal.model.output.assembler.DataSinkAssembler;
import de.civitascore.portal.problem.ProblemType;
import de.civitascore.portal.problem.Problems;
import de.civitascore.portal.repository.specification.DataSinkSpec;
import de.civitascore.portal.service.DataSinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** REST controller for DataSink resources nested under a parent dataset. */
@RestController
@RequestMapping("/datasets/{dataSetId}/datasinks")
@RequiredArgsConstructor
@Tag(name = "DataSinks", description = "DataSink management endpoints")
public class DataSinkController {

  static final String CONFIRM_DATA_LOSS_DESCRIPTION =
      "Must be true when the request removes a provisioned Data sink: its stored data is deleted"
          + " at the next release. Layers and APIs that the request removes need no confirmation.";

  private final DataSinkService dataSinkService;
  private final DataSinkAssembler dataSinkAssembler;
  private final JsonMergePatch jsonMergePatch;

  @GetMapping
  @Operation(summary = "List all DataSinks of a dataset")
  public Page<DataSinkOutputDTO> listDataSinks(
      @PathVariable UUID dataSetId,
      @ParameterObject DataSinkSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return dataSinkAssembler.toOutput(dataSinkService.findAll(dataSetId, spec, pageable));
  }

  @GetMapping("/{dataSinkId}")
  @Operation(summary = "Get a DataSink by ID")
  @Problems(ProblemType.NOT_FOUND)
  public DataSinkOutputDTO getDataSink(
      @PathVariable UUID dataSetId, @PathVariable UUID dataSinkId) {
    return dataSinkAssembler.toOutput(dataSinkService.findOwned(dataSetId, dataSinkId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a new DataSink")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.DATASET_NOT_EDITABLE,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public ResponseEntity<DataSinkOutputDTO> createDataSink(
      @PathVariable UUID dataSetId, @Valid @RequestBody DataSinkInputDTO input) {
    DataSink created = dataSinkService.create(dataSetId, input);
    return ResponseEntity.created(Locations.ofCreated(created.getId()))
        .body(dataSinkAssembler.toOutput(created));
  }

  @PutMapping("/{dataSinkId}")
  @Operation(summary = "Replace a DataSink")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.DATASET_NOT_EDITABLE,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION,
    ProblemType.RESOURCE_IN_USE,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public DataSinkOutputDTO updateDataSink(
      @PathVariable UUID dataSetId,
      @PathVariable UUID dataSinkId,
      @Valid @RequestBody DataSinkInputDTO input) {
    return dataSinkAssembler.toOutput(dataSinkService.update(dataSetId, dataSinkId, input));
  }

  @PatchMapping("/{dataSinkId}")
  @Operation(
      summary = "Partially update a DataSink",
      description = JsonMergePatch.OPERATION_DESCRIPTION)
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.DATASET_NOT_EDITABLE,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.UNIQUE_CONSTRAINT_VIOLATION,
    ProblemType.RESOURCE_IN_USE,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public DataSinkOutputDTO patchDataSink(
      @PathVariable UUID dataSetId, @PathVariable UUID dataSinkId, @RequestBody JsonNode updates) {
    DataSinkInputDTO current =
        dataSinkAssembler.toInput(dataSinkService.findOwned(dataSetId, dataSinkId));
    DataSinkInputDTO patched = jsonMergePatch.apply(current, updates, dataSinkId);
    return dataSinkAssembler.toOutput(dataSinkService.update(dataSetId, dataSinkId, patched));
  }

  @DeleteMapping("/{dataSinkId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Delete a DataSink",
      description =
          "Deletes the DataSink with its Layers, and with the APIs it is the last backing DataSink"
              + " of. A DataSink that a Pipeline links answers 409 RESOURCE_IN_USE; remove it from"
              + " its Pipelines first. A provisioned DataSink is no longer listed; its stored data"
              + " is deleted at the next release.")
  @Problems({
    ProblemType.NOT_FOUND,
    ProblemType.DATASET_NOT_EDITABLE,
    ProblemType.SAGA_IN_FLIGHT,
    ProblemType.RESOURCE_IN_USE,
    ProblemType.REMOVAL_NOT_CONFIRMED,
    ProblemType.REGISTRY_UNAVAILABLE
  })
  public void deleteDataSink(
      @PathVariable UUID dataSetId,
      @PathVariable UUID dataSinkId,
      @Parameter(description = CONFIRM_DATA_LOSS_DESCRIPTION) @RequestParam(defaultValue = "false")
          boolean confirmDataLoss) {
    dataSinkService.delete(dataSetId, dataSinkId, confirmDataLoss);
  }
}
