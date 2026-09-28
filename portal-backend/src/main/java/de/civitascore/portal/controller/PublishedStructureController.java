package de.civitascore.portal.controller;

import de.civitascore.portal.frost.PortStructureCatalog;
import de.civitascore.portal.frost.PortStructureModel.PortStructure;
import de.civitascore.portal.frost.PortStructures;
import de.civitascore.portal.model.entity.PublishedStructure;
import de.civitascore.portal.repository.PublishedStructureRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Data structures the sinks of the platform publish.
 *
 * <p>A published structure says what a sink accepts: the field names, the data types, the mandatory
 * fields and the reference block it resolves an entity by. It belongs to the platform, not to a
 * Tenant — every Tenant sees the same document and nobody edits it, so the list carries no Dataset
 * and no Tenant.
 *
 * <p>The list is grouped by sink, and the structure editor renders the two levels as it finds them.
 * A sink that begins to publish structures, and a structure a sink adds, appear there without a
 * change in the editor.
 */
@RestController
@RequestMapping("/published-structures")
@RequiredArgsConstructor
@Tag(
    name = "Published structures",
    description = "The data structures the platform's sinks publish")
public class PublishedStructureController {

  private final PortStructures portStructures;
  private final PublishedStructureRepository publishedStructureRepository;

  /**
   * One structure of a sink.
   *
   * @param key how the structure is addressed; the second level of the menu shows {@code name}
   * @param urn the version the registry assigned, which an import pins. Null until the structure
   *     has been published, which happens on start-up.
   */
  public record PublishedStructureOutput(String key, String name, String urn) {}

  /** A sink and the structures it publishes. */
  public record PublishingSinkOutput(String sink, List<PublishedStructureOutput> structures) {}

  @GetMapping
  @Operation(
      operationId = "listPublishedStructures",
      summary = "List the sinks that publish data structures, with their structures")
  public ResponseEntity<List<PublishingSinkOutput>> list() {
    List<PublishedStructureOutput> structures =
        PortStructureCatalog.all().stream().map(this::toOutput).toList();
    return ResponseEntity.ok(
        List.of(new PublishingSinkOutput(PortStructureCatalog.SINK, structures)));
  }

  @GetMapping("/{key}")
  @Operation(
      operationId = "getPublishedStructure",
      summary = "Get the model document of a published data structure")
  public ResponseEntity<Map<String, Object>> structure(@PathVariable String key) {
    return portStructures
        .model(key)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  private PublishedStructureOutput toOutput(PortStructure structure) {
    return new PublishedStructureOutput(
        structure.port(),
        structure.port(),
        publishedStructureRepository
            .findByPort(structure.port())
            .map(PublishedStructure::getVersionedUrn)
            .orElse(null));
  }
}
