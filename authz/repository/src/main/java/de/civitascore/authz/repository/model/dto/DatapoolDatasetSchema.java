package de.civitascore.authz.repository.model.dto;

import java.util.UUID;

/** Dataset identity and its canonical FROST schema name. */
public record DatapoolDatasetSchema(UUID datasetId, String schemaName) {}
