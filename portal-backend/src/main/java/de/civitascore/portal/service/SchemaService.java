package de.civitascore.portal.service;

import de.civitascore.portal.model.SchemaEntity;
import de.civitascore.portal.repository.SchemaRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SchemaService {
  private final SchemaRepository repo;

  public SchemaService(SchemaRepository repo) {
    this.repo = repo;
  }

  public List<SchemaEntity> list() {
    return List.of(
        SchemaEntity.builder()
            .id(UUID.randomUUID())
            .name("Test Schema")
            .type("Test Type")
            .content("{}")
            .build());
  }
}
