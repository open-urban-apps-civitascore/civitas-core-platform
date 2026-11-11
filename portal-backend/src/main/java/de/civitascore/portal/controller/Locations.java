package de.civitascore.portal.controller;

import java.net.URI;
import java.util.UUID;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Builds the {@code Location} header of a created resource. */
final class Locations {

  private Locations() {}

  /** The URI of the current request with the id of the created resource appended. */
  static URI ofCreated(UUID id) {
    return ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(id)
        .toUri();
  }
}
