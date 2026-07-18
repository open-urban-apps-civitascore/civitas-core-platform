package de.civitascore.portal.controller;

import de.civitascore.portal.service.DataSetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DataSet membership maintenance: explicitly assign/unassign reusable artifacts to/from a dataset
 * (the "Beides" explicit-assignment path, on top of the automatic pipeline-closure linking), and
 * find artifacts that belong to no dataset (orphans by type). Membership is maintained in Model
 * Forge as {@code dataset-ref} edges; see the deletion-policy concept.
 */
@RestController
@RequiredArgsConstructor
@Tag(
    name = "DataSet membership",
    description = "Assign/unassign dataset members; find orphans by type")
public class DataSetMembershipController {

  private final DataSetService dataSetService;

  @GetMapping("/orphans")
  @Operation(summary = "CORE URNs of artifacts of the given type that belong to no dataset")
  public ResponseEntity<List<String>> orphans(@RequestParam String type) {
    return ResponseEntity.ok(dataSetService.orphans(type));
  }

  @PostMapping("/datasets/{datasetId}/members")
  @Operation(summary = "Explicitly add a reusable artifact (body {artifactUrn}) to a dataset")
  public ResponseEntity<Void> link(
      @PathVariable UUID datasetId, @RequestBody Map<String, String> body) {
    dataSetService.linkMember(datasetId, body.get("artifactUrn"));
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/datasets/{datasetId}/members")
  @Operation(summary = "Explicitly remove an artifact (query artifactUrn) from a dataset")
  public ResponseEntity<Void> unlink(
      @PathVariable UUID datasetId, @RequestParam String artifactUrn) {
    dataSetService.unlinkMember(datasetId, artifactUrn);
    return ResponseEntity.noContent().build();
  }
}
