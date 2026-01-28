package de.civitascore.authz.repository.controller;

import de.civitascore.authz.repository.model.dto.UserContextResponse;
import de.civitascore.authz.repository.service.UserContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class UserContextController {

  private final UserContextService userContextService;

  @GetMapping("/user-context/{externalId}")
  public ResponseEntity<UserContextResponse> getUserContext(@PathVariable String externalId) {
    log.info("GET /api/v1/user-context/{}", externalId);

    return userContextService
        .getUserContext(externalId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}
