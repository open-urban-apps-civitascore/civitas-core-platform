package de.civitascore.portal.model.datasink;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Configuration shape for a {@code FROST} DataSink. Must be empty — no fields are used. */
@Schema(description = "Configuration for a FROST data sink (must be empty)")
@Data
public class FrostConfiguration implements DataSinkConfiguration {}
