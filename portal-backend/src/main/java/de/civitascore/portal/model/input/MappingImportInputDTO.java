package de.civitascore.portal.model.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

/**
 * One mapping inside a dataset import bundle: the field-to-field transform between two data
 * structures.
 *
 * <p>Deliberately not a {@link BaseDataEntityInputDTO}: unlike structures and sources, a mapping
 * has no host shell row, hence no scope assignments. It lives purely as a Model Forge artifact, and
 * this DTO is only the envelope around the opaque document — the host never interprets the mapping
 * rules.
 */
@Data
public class MappingImportInputDTO {

  @NotBlank(message = "Name is required") @Schema(description = "Display name, recorded in the install provenance")
  private String name;

  private String description;

  @NotBlank(message = "mappingUrn is required") @Size(max = 1024, message = "mappingUrn must not exceed 1024 characters") @Schema(
      description =
          "Logical CORE URN of artifact type 'mapping' that this mapping is stored under"
              + " (urn:core:<scope>:<owner>:mapping:<domain>:<name>:<disambiguator>). The bundle has"
              + " to bring it: created without one, Model Forge mints a random disambiguator, so"
              + " every re-install would add a duplicate mapping instead of resolving to the same"
              + " identity.")
  private String mappingUrn;

  @NotEmpty(message = "document is required") @Schema(
      description =
          "The mapping document. 'fields' is required; 'title', 'description', 'source', 'target'"
              + " and the editor-only 'positions' are optional. Do not author '$schema' or 'id' —"
              + " Model Forge stamps both on every write. When present, 'source' and 'target' must"
              + " be CORE URNs of artifact type 'datastructure' that this bundle ships or that are"
              + " already installed.")
  private Map<String, Object> document;
}
