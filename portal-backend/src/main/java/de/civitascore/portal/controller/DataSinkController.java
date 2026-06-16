package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.output.DataSinkOutputDTO;
import de.civitascore.portal.model.output.assembler.DataSinkAssembler;
import de.civitascore.portal.repository.specification.DataSinkSpec;
import de.civitascore.portal.service.DataSinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** REST controller for DataSink resources nested under a parent dataset. */
@RestController
@RequestMapping("/datasets/{dataSetId}/datasinks")
@RequiredArgsConstructor
@Tag(name = "DataSinks", description = "DataSink management endpoints")
public class DataSinkController
    extends BaseController<DataSinkInputDTO, DataSinkOutputDTO, DataSink, DataSinkSpec> {

  private final DataSinkService dataSinkService;
  private final DataSinkAssembler dataSinkAssembler;

  /** {@inheritDoc} */
  @Override
  protected DataSinkService getService() {
    return dataSinkService;
  }

  /** {@inheritDoc} */
  @Override
  protected DataSinkAssembler getAssembler() {
    return dataSinkAssembler;
  }

  /** {@inheritDoc} Lists all DataSinks scoped to the parent dataset. */
  @Override
  @Operation(operationId = "listDataSinks", summary = "List all DataSinks for a dataset")
  public ResponseEntity<Page<DataSinkOutputDTO>> getAll(
      @ParameterObject DataSinkSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }

  /** {@inheritDoc} Retrieves a DataSink by ID, verifying it belongs to the parent dataset. */
  @Override
  @Operation(operationId = "getDataSink", summary = "Get DataSink by ID")
  public ResponseEntity<DataSinkOutputDTO> getById(@PathVariable UUID id) {
    UUID dataSetId = extractDataSetId();
    DataSink sink = dataSinkService.findByIdAndDataSetOrThrow(id, dataSetId);
    return ResponseEntity.ok(dataSinkAssembler.toOutput(sink));
  }

  /** Creates a new DataSink under the parent dataset. */
  @Override
  @Operation(operationId = "createDataSink", summary = "Create a new DataSink")
  public ResponseEntity<DataSinkOutputDTO> create(@Valid @RequestBody DataSinkInputDTO input) {
    return super.create(input);
  }

  /** Replaces an existing DataSink after verifying it belongs to the parent dataset. */
  @Override
  @Operation(operationId = "updateDataSink", summary = "Replace a DataSink")
  public ResponseEntity<DataSinkOutputDTO> update(
      @PathVariable UUID id, @Valid @RequestBody DataSinkInputDTO input) {
    UUID dataSetId = extractDataSetId();
    dataSinkService.findByIdAndDataSetOrThrow(id, dataSetId);
    return super.update(id, input);
  }

  /** Partially updates a DataSink after verifying it belongs to the parent dataset. */
  @Override
  @Operation(operationId = "patchDataSink", summary = "Partially update a DataSink")
  public ResponseEntity<DataSinkOutputDTO> patch(
      @PathVariable UUID id, @RequestBody JsonNode updates) throws IOException {
    UUID dataSetId = extractDataSetId();
    dataSinkService.findByIdAndDataSetOrThrow(id, dataSetId);
    return super.patch(id, updates);
  }

  /** Deletes a DataSink after verifying it belongs to the parent dataset. */
  @Override
  @Operation(operationId = "deleteDataSink", summary = "Delete a DataSink")
  public void delete(@PathVariable UUID id) {
    UUID dataSetId = extractDataSetId();
    dataSinkService.findByIdAndDataSetOrThrow(id, dataSetId);
    super.delete(id);
  }

  @Override
  protected DataSinkInputDTO preProcessInput(DataSinkInputDTO input) {
    input.setDataSetId(extractDataSetId());
    return super.preProcessInput(input);
  }

  private UUID extractDataSetId() {
    return extractUUIDFromPathVariable("dataSetId", DataSink.class);
  }
}
