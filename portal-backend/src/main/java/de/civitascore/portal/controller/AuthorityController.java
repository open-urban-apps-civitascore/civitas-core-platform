package de.civitascore.portal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stub controller for authorities endpoint.
 *
 * <p>This is a temporary placeholder that returns an empty list. The authorities feature is not
 * implemented in v2. This controller exists to prevent 500 errors from the frontend which expects
 * this endpoint to exist.
 *
 * <p>TODO: Remove this controller when the frontend removes the authorities dependency, or
 * implement a full Authority feature if needed in a future version.
 */
@RestController
@RequestMapping(path = "/authorities")
@Tag(name = "Authorities", description = "Authority management API (stub)")
public class AuthorityController {

  /**
   * Returns an empty page of authorities as a stub response.
   *
   * @param pageable pagination parameters
   * @return an empty page with HTTP 200 status
   */
  @GetMapping
  @Operation(
      summary = "List authorities (stub)",
      description = "Returns an empty list. Authorities feature is not implemented in v2.")
  public ResponseEntity<Page<Object>> getAll(Pageable pageable) {
    Page<Object> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
    return ResponseEntity.ok(emptyPage);
  }
}
