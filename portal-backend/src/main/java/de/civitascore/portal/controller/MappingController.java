package de.civitascore.portal.controller;

import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.service.MappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * First-class CORE Mapping artifacts. The frontend authors a mapping (a CORE Mapping document) and
 * persists it here to obtain its URN, which it then references from pipeline nodes by {@code
 * mappingRef}. The backend is a thin pass-through: it forwards the opaque document to Model Forge
 * (which validates it against {@code mapping.schema.json} and mints its URN) and never inspects the
 * mapping's contents.
 */
@RestController
@RequestMapping("/mappings")
@RequiredArgsConstructor
@Tag(
    name = "Mappings",
    description = "First-class CORE Mapping artifacts (content stored in Model Forge)")
public class MappingController {

  private final MappingService mappingService;

  @PostMapping
  @Operation(
      summary = "Create a Mapping artifact from a CORE mapping document; returns its URN pins")
  public ResponseEntity<Map<String, String>> create(@RequestBody Map<String, Object> doc) {
    return ResponseEntity.status(HttpStatus.CREATED).body(pins(mappingService.store(null, doc)));
  }

  @PutMapping
  @Operation(summary = "Version an existing Mapping artifact (body carries its logicalUrn)")
  public ResponseEntity<Map<String, String>> update(@RequestBody Map<String, Object> body) {
    Object logicalUrn = body.get("logicalUrn");
    return ResponseEntity.ok(
        pins(mappingService.store(logicalUrn instanceof String s ? s : null, body)));
  }

  @GetMapping
  @Operation(summary = "Fetch a Mapping artifact's content by (logical or versioned) CORE URN")
  public ResponseEntity<Map<String, Object>> get(@RequestParam("urn") String urn) {
    return mappingService
        .get(urn)
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
      @RequestParam("urn") String urn,
      @RequestParam(value = "force", defaultValue = "false") boolean force) {
    mappingService.delete(urn, force);
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
