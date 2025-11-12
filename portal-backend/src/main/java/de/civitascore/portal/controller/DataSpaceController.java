package de.civitascore.portal.controller;

import de.civitascore.portal.model.entity.DataSpace;
import de.civitascore.portal.model.input.DataSpaceInputDTO;
import de.civitascore.portal.model.output.DataSpaceOutputDTO;
import de.civitascore.portal.model.output.assembler.BaseAssembler;
import de.civitascore.portal.model.output.assembler.DataSpaceAssembler;
import de.civitascore.portal.repository.specification.DataSpaceSpec;
import de.civitascore.portal.service.BaseService;
import de.civitascore.portal.service.DataSpaceService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dataspaces")
@RequiredArgsConstructor
@Tag(name = "DataSpaces", description = "DataSpace management endpoints")
public class DataSpaceController
    extends BaseController<
        DataSpaceInputDTO, DataSpaceOutputDTO, DataSpace, DataSpaceSpec, String> {

  private final DataSpaceService dataSpaceService;
  private final DataSpaceAssembler dataSpaceAssembler;

  @Override
  BaseService<DataSpace, String, DataSpaceInputDTO> getService() {
    return dataSpaceService;
  }

  @Override
  protected BaseAssembler<DataSpace, DataSpaceOutputDTO, String> getAssembler() {
    return dataSpaceAssembler;
  }

  @Parameters({
    @Parameter(
        name = "name",
        description = "Filter by name (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "production")),
    @Parameter(
        name = "description",
        description = "Filter by description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "Production environment data")),
    @Parameter(
        name = "q",
        description = "Search in title or description (partial match, case-insensitive).",
        in = ParameterIn.QUERY,
        schema = @Schema(type = "string", example = "data"))
  })
  @Override
  public ResponseEntity<Page<DataSpaceOutputDTO>> getAll(
      @ParameterObject @Parameter(description = "Search/filter spec") DataSpaceSpec spec,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return super.getAll(spec, pageable);
  }
}
