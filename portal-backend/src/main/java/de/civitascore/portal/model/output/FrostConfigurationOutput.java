package de.civitascore.portal.model.output;

import de.civitascore.portal.model.datasink.DataSinkConfigurationOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Output configuration shape for a {@code FROST} DataSink. */
@Schema(description = "Configuration for a FROST data sink (output)")
@Data
public class FrostConfigurationOutput implements DataSinkConfigurationOutput {}
