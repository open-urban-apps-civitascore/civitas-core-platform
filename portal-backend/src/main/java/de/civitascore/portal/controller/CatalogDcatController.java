package de.civitascore.portal.controller;

import de.civitascore.portal.mapper.CatalogMapper;
import de.civitascore.portal.mapper.dcat.CatalogDcatMapper;
import de.civitascore.portal.model.entity.Catalog;
import de.civitascore.portal.model.output.CatalogOutputDTO;
import de.civitascore.portal.service.CatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.jena.rdf.model.Model;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/catalogs")
@RequiredArgsConstructor
@Tag(name = "Catalogs", description = "Catalog management endpoints")
public class CatalogDcatController extends DcatController {

  private final CatalogService catalogService;
  private final CatalogMapper catalogMapper;
  private final CatalogDcatMapper catalogDcatMapper;

  @GetMapping("/{id}")
  public ResponseEntity<String> getById(@PathVariable UUID id) {
    Catalog catalog = catalogService.findByIdOrThrow(id);
    CatalogOutputDTO catalogOutputDTO = catalogMapper.toOutput(catalog);
    Model model = catalogDcatMapper.toModel(catalogOutputDTO);
    return ResponseEntity.ok().body(toJsonLd(model));
  }
}
