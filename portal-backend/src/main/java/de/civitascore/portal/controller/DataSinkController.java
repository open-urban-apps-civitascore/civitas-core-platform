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
    extends DataSetSubEntityController<
        DataSinkInputDTO, DataSinkOutputDTO, DataSink, DataSinkSpec> {

  private final DataSinkService dataSinkService;
  private final DataSinkAssembler dataSinkAssembler;

  @Override
  protected DataSinkService getService() {
    return dataSinkService;
  }

  @Override
  protected DataSinkAssembler getAssembler() {
    return dataSinkAssembler;
  }

  @Override
  protected Class<DataSink> getEntityClass() {
    return DataSink.class;
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

  @Override
  @Operation(operationId = "getDataSink", summary = "Get DataSink by ID")
  public ResponseEntity<DataSinkOutputDTO> getById(@PathVariable UUID id) {
    return super.getById(id);
  }

  @Override
  @Operation(operationId = "createDataSink", summary = "Create a new DataSink")
  public ResponseEntity<DataSinkOutputDTO> create(@Valid @RequestBody DataSinkInputDTO input) {
    return super.create(input);
  }

  @Override
  @Operation(operationId = "updateDataSink", summary = "Replace a DataSink")
  public ResponseEntity<DataSinkOutputDTO> update(
      @PathVariable UUID id, @Valid @RequestBody DataSinkInputDTO input) {
    return super.update(id, input);
  }

  @Override
  @Operation(operationId = "patchDataSink", summary = "Partially update a DataSink")
  public ResponseEntity<DataSinkOutputDTO> patch(
      @PathVariable UUID id, @RequestBody JsonNode updates) throws IOException {
    return super.patch(id, updates);
  }

  @Override
  @Operation(operationId = "deleteDataSink", summary = "Delete a DataSink")
  public void delete(@PathVariable UUID id) {
    super.delete(id);
  }
}
