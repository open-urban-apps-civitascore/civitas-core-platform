package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.output.DataSinkOutputDTO;
import de.civitascore.portal.model.output.assembler.DataSinkAssembler;
import de.civitascore.portal.repository.specification.DataSinkSpec;
import de.civitascore.portal.service.DataSinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only REST controller for DataSink resources nested under a parent dataset. */
@RestController
@RequestMapping("/datasets/{dataSetId}/datasinks")
@RequiredArgsConstructor
@Tag(name = "DataSinks", description = "DataSink management endpoints")
public class DataSinkController
    extends BaseReadOnlyController<DataSinkInputDTO, DataSinkOutputDTO, DataSink, DataSinkSpec> {

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
    UUID dataSetId = extractUUIDFromPathVariable("dataSetId", DataSink.class);
    DataSink sink = dataSinkService.findByIdAndDataSetOrThrow(id, dataSetId);
    return ResponseEntity.ok(dataSinkAssembler.toOutput(sink));
  }
}
