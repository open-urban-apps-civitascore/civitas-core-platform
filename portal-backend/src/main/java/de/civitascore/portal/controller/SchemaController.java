package de.civitascore.portal.controller;

import de.civitascore.portal.model.SchemaEntity;
import de.civitascore.portal.security.dto.PrincipalUserDetails;
import de.civitascore.portal.service.SchemaService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/schemas")
public class SchemaController {
  private final SchemaService service;

  @Autowired
  public SchemaController(SchemaService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasRole('SCHEMA_VIEW') or hasRole('SCHEMA_ADMIN')")
  public List<SchemaEntity> list(@AuthenticationPrincipal PrincipalUserDetails principal) {
    return service.list();
  }
}
