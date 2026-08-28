package de.civitascore.portal.controller;

import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.service.MappingService;
import de.civitascore.portal.util.InvalidInputException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * First-class CORE Mapping artifacts, nested under the DataSet they belong to. The frontend authors
 * a mapping (a CORE Mapping document) and persists it here to obtain its URN, which it then
 * references from pipeline nodes by {@code mappingRef}. The backend is a thin pass-through: it
 * forwards the opaque document to Model Forge (which validates it against {@code
 * mapping.schema.json} and mints its URN) and never inspects the mapping's contents.
 *
 * <p>Nesting under the DataSet is what scopes access: the DataSet-typed route grant covers every
 * mapping of that DataSet, and each operation additionally verifies the addressed mapping is a
 * member of it, so a caller authorized on one DataSet cannot reach another's mappings.
 */
@RestController
@RequestMapping("/datasets/{dataSetId}/mappings")
@RequiredArgsConstructor
@Tag(
    name = "Mappings",
    description = "First-class CORE Mapping artifacts (content stored in Model Forge)")
public class MappingController {

  private final MappingService mappingService;

  @PostMapping
  @Operation(
      summary = "Create a Mapping artifact from a CORE mapping document; returns its URN pins")
  public ResponseEntity<Map<String, String>> create(
      @PathVariable UUID dataSetId, @RequestBody Map<String, Object> doc) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(pins(mappingService.store(dataSetId, null, doc)));
  }

  @PutMapping
  @Operation(summary = "Version an existing Mapping artifact (body carries its logicalUrn)")
  public ResponseEntity<Map<String, String>> update(
      @PathVariable UUID dataSetId, @RequestBody Map<String, Object> body) {
    // This verb versions an existing artifact, so the URN naming it is required: without it the
    // store would mint a second mapping from the same document instead.
    if (!(body.get("logicalUrn") instanceof String logicalUrn) || logicalUrn.isBlank()) {
      throw new InvalidInputException(
          "Mapping", "logicalUrn", "logicalUrn is required to version a Mapping");
    }
    return ResponseEntity.ok(pins(mappingService.store(dataSetId, logicalUrn, body)));
  }

  @GetMapping
  @Operation(summary = "Fetch a Mapping artifact's content by (logical or versioned) CORE URN")
  public ResponseEntity<Map<String, Object>> get(
      @PathVariable UUID dataSetId, @RequestParam("urn") String urn) {
    return mappingService
        .get(dataSetId, urn)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @DeleteMapping
  @Operation(
      summary = "Delete a Mapping artifact by its (logical or versioned) CORE URN",
      description =
          "Without force=true the delete is rejected while another artifact (e.g. a pipeline's"
              + " mappingRef) still references the mapping; force=true unlinks it from any DataSets"
              + " and deletes regardless.")
  public ResponseEntity<Void> delete(
      @PathVariable UUID dataSetId,
      @RequestParam("urn") String urn,
      @RequestParam(value = "force", defaultValue = "false") boolean force) {
    mappingService.delete(dataSetId, urn, force);
    return ResponseEntity.noContent().build();
  }

  private static Map<String, String> pins(ModelRegistryGateway.ModelPin pin) {
    Map<String, String> out = new LinkedHashMap<>();
    out.put("logicalUrn", pin.logicalUrn());
    out.put("versionedUrn", pin.versionedUrn());
    out.put("version", pin.version());
    return out;
  }
}
