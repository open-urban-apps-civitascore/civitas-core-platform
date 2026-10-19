package de.civitascore.portal.model.datasink;

import de.civitascore.portal.model.output.FrostConfigurationOutput;
import de.civitascore.portal.model.output.PostgisConfigurationOutput;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Marker interface for all DataSink configuration shapes used in API output.
 *
 * <p>{@code subTypes} is what makes springdoc emit the implementations as components; without it
 * the {@code oneOf} on {@code DataSinkOutputDTO.configuration} exports as unresolvable refs.
 */
@Schema(subTypes = {PostgisConfigurationOutput.class, FrostConfigurationOutput.class})
public interface DataSinkConfigurationOutput {}
