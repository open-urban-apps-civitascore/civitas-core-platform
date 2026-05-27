package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.output.DataSinkOutputDTO;
import de.civitascore.portal.model.output.assembler.DataSinkAssembler;
import de.civitascore.portal.repository.specification.DataSinkSpec;
import de.civitascore.portal.service.DataSinkService;
import de.civitascore.portal.util.InvalidInputException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.MethodNotAllowedException;
import tools.jackson.databind.JsonNode;

/** REST controller for managing DataSink resources nested under a parent dataset. */
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

  /** {@inheritDoc} Creates a new DataSink under the parent dataset. */
  @Override
  @Operation(operationId = "createDataSink", summary = "Create a new DataSink")
  public ResponseEntity<DataSinkOutputDTO> create(@Valid @RequestBody DataSinkInputDTO input) {
    return super.create(input);
  }

  /** {@inheritDoc} Fully replaces an existing DataSink with the provided input. */
  @Override
  @Operation(operationId = "updateDataSink", summary = "Replace a DataSink")
  public ResponseEntity<DataSinkOutputDTO> update(
      @PathVariable UUID id, @Valid @RequestBody DataSinkInputDTO input) {
    UUID dataSetId = extractDataSetId();
    dataSinkService.findByIdAndDataSetOrThrow(id, dataSetId);
    return super.update(id, input);
  }

  /** {@inheritDoc} Deletes a DataSink after verifying it belongs to the parent dataset. */
  @Override
  @Operation(operationId = "deleteDataSink", summary = "Delete a DataSink")
  public void delete(@PathVariable UUID id) {
    UUID dataSetId = extractDataSetId();
    dataSinkService.findByIdAndDataSetOrThrow(id, dataSetId);
    super.delete(id);
  }

  /** PATCH is not supported for DataSinks. */
  @Override
  public ResponseEntity<DataSinkOutputDTO> patch(
      @PathVariable UUID id, @RequestBody JsonNode updates) {
    throw new MethodNotAllowedException(HttpMethod.PATCH, Collections.emptySet());
  }

  /**
   * Injects the parent {@code dataSetId} path variable into the input DTO before create/update.
   *
   * @throws InvalidInputException if the path variable is missing or not a valid UUID
   */
  @Override
  protected DataSinkInputDTO preProcessInput(DataSinkInputDTO input) {
    input.setDataSetId(extractUUIDFromPathVariable("dataSetId", DataSink.class));
    return super.preProcessInput(input);
  }

  private UUID extractDataSetId() {
    return extractUUIDFromPathVariable("dataSetId", DataSink.class);
  }
}
