package de.civitascore.portal.controller;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.output.DataSetOutputDTO;
import de.civitascore.portal.model.output.assembler.DataSetAssembler;
import de.civitascore.portal.repository.specification.DataSetSpec;
import de.civitascore.portal.service.DataSetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/datasets")
@RequiredArgsConstructor
@Tag(name = "DataSets", description = "Dataset management endpoints")
public class DataSetController
    extends BaseDataEntityController<DataSetInputDTO, DataSetOutputDTO, DataSet, DataSetSpec> {

  private final DataSetService dataSetService;
  private final DataSetAssembler dataSetAssembler;

  @Parameters({
    @Parameter(
        name = "name",
        description = "Filter by name (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "sensor-data")),
    @Parameter(
        name = "description",
        description = "Filter by description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "Temperature sensor readings")),
    @Parameter(
        name = "q",
        description = "Search in name or description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "sensor"))
  })
  @Override
  public ResponseEntity<Page<DataSetOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") DataSetSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  @Override
  protected DataSetService getService() {
    return dataSetService;
  }

  @Override
  protected DataSetAssembler getAssembler() {
    return dataSetAssembler;
  }

  @Override
  @PutMapping("/{id}")
  @Operation(
      summary = "Update a DRAFT dataset",
      description =
          "Updates a dataset in DRAFT status. For published datasets (READY or AVAILABLE), use PUT /datasets/{id}/published/meta instead.")
  public ResponseEntity<DataSetOutputDTO> update(
      @PathVariable UUID id, @Valid @RequestBody DataSetInputDTO input) {
    return super.update(id, input);
  }

  @PutMapping("/{id}/published/meta")
  @Operation(
      summary = "Update metadata of a published dataset",
      description =
          "Updates only the metadata (name, description) of a published dataset (READY or AVAILABLE status). Cannot modify persistenceId or pipelines. For DRAFT datasets, use PUT /datasets/{id} instead.")
  public ResponseEntity<DataSetOutputDTO> updatePublishedMeta(
      @PathVariable UUID id, @Valid @RequestBody DataSetInputDTO input) {
    DataSetInputDTO preProcessedInput = preProcessInput(input);
    DataSet updated = dataSetService.updatePublishedMeta(id, preProcessedInput);
    DataSetOutputDTO output = dataSetAssembler.toOutput(updated);
    return ResponseEntity.ok(output);
  }

  @Override
  protected ScopeType getScopeType() {
    return ScopeType.DATASET;
  }

  @PostMapping("/{id}/publish")
  @Operation(
      summary = "Publish a dataset",
      description =
          "Publishes a dataset by generating distributions from pipeline APIs and setting status to READY. Requires at least one pipeline to be present in the dataset.")
  public ResponseEntity<DataSetOutputDTO> publishDataSet(@PathVariable UUID id) {
    DataSet published = dataSetService.publish(id);
    DataSetOutputDTO output = dataSetAssembler.toOutput(published);
    return ResponseEntity.ok(output);
  }

  @PostMapping("/{id}/unpublish")
  @Operation(
      summary = "Unpublish a dataset",
      description =
          "Unpublishes a dataset by removing auto-generated distributions and reverting status from READY to DRAFT.")
  public ResponseEntity<DataSetOutputDTO> unpublishDataSet(@PathVariable UUID id) {
    DataSet unpublished = dataSetService.unpublish(id);
    DataSetOutputDTO output = dataSetAssembler.toOutput(unpublished);
    return ResponseEntity.ok(output);
  }

  @PostMapping("/{id}/release")
  @Operation(
      summary = "Release a dataset",
      description =
          "Releases a dataset by transitioning it from READY to AVAILABLE and triggering infrastructure provisioning via saga.")
  public ResponseEntity<DataSetOutputDTO> releaseDataSet(@PathVariable UUID id) {
    DataSet released = dataSetService.release(id);
    DataSetOutputDTO output = dataSetAssembler.toOutput(released);
    return ResponseEntity.accepted().body(output);
  }

  @PostMapping("/{id}/unrelease")
  @Operation(
      summary = "Unrelease a dataset",
      description =
          "Unreleases a dataset by triggering infrastructure teardown via saga. The dataset transitions from AVAILABLE to READY after the saga completes.")
  public ResponseEntity<DataSetOutputDTO> unreleaseDataSet(@PathVariable UUID id) {
    DataSet unreleased = dataSetService.unrelease(id);
    DataSetOutputDTO output = dataSetAssembler.toOutput(unreleased);
    return ResponseEntity.accepted().body(output);
  }

  @Override
  @DeleteMapping("/{id}")
  @Operation(
      summary = "Delete a dataset",
      description =
          "Deletes a DRAFT dataset immediately (204 No Content). "
              + "READY datasets cannot be deleted — unpublish first. "
              + "AVAILABLE datasets cannot be deleted directly — unrelease first (POST /{id}/unrelease) to tear down infrastructure, then delete.")
  public void delete(@PathVariable UUID id) {
    dataSetService.deleteById(id);
  }
}
