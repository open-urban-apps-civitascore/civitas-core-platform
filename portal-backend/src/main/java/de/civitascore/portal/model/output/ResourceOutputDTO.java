package de.civitascore.portal.model.output;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Output DTO representing a resource for API responses. */
@Schema(description = "Resource details")
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceOutputDTO extends BaseOutputDTO {}
