package de.civitascore.portal.model.output;

import de.civitascore.portal.service.validation.ClosureFinding;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * The member a closure rejection adds to its problem detail. Documents the wire shape only; the
 * response is a {@link org.springframework.http.ProblemDetail} carrying this as an extension.
 */
@Schema(name = "PipelineClosureFindings")
public record PipelineClosureFindings(
    @Schema(description = "One entry per problem, across every pipeline of the dataset")
        List<Finding> findings) {

  /** One reason a flow cannot be released. */
  @Schema(name = "ClosureFinding")
  public record Finding(
      @Schema(example = "3c674c37-1bd9-4fbf-8cff-d6585061914f") UUID pipelineId,
      @Schema(
              description =
                  "Absent unless naming the artifact discloses nothing to this caller — a CORE URN"
                      + " spells the model's name",
              example = "urn:core:platform:civitas:element:common:Messstationen:5yxlkvllmh:1.0.0")
          String artifactUrn,
      @Schema(example = "NOT_RELEASED") ClosureFinding.Reason reason) {}
}
