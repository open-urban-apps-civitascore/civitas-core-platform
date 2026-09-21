package de.civitascore.portal.controller;

import de.civitascore.portal.frost.PortStructures;
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
 * The structures the FROST sink ports publish.
 *
 * <p>A structure says what a port accepts: the field names, the data types, the mandatory fields
 * and the reference block it resolves the entity by. It belongs to the platform, not to a Tenant —
 * every Tenant sees the same document and nobody edits it.
 */
@RestController
@RequestMapping("/frost-sink-ports")
@RequiredArgsConstructor
@Tag(name = "FROST sink ports", description = "The data structures the FROST sink ports publish")
public class FrostSinkPortController {

  private final PortStructures portStructures;

  @GetMapping
  @Operation(
      operationId = "listFrostSinkPorts",
      summary = "List the FROST sink ports that publish a data structure")
  public ResponseEntity<List<String>> list() {
    return ResponseEntity.ok(portStructures.ports());
  }

  @GetMapping("/{port}/structure")
  @Operation(
      operationId = "getFrostSinkPortStructure",
      summary = "Get the data structure a FROST sink port publishes")
  public ResponseEntity<Map<String, Object>> structure(@PathVariable String port) {
    return portStructures
        .model(port)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
