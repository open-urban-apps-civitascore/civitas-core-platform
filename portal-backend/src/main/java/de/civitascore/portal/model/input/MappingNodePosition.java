package de.civitascore.portal.model.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Position of a node in the mapping editor. */
@Schema(description = "Position of a node in the mapping editor")
public record MappingNodePosition(@NotNull Double x, @NotNull Double y) {}
