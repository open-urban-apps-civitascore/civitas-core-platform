package de.civitascore.portal.configuration;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Bounds for the participating-artifact closure walk run before a dataset is staged or released,
 * bound from the {@code dataset.closure-validation.*} namespace.
 *
 * <p>Both bounds exist because the traversal is work performed on a caller's request: a
 * deliberately deep or wide dependency graph must not be usable to occupy the backend. {@code
 * maxDepth} is passed to Model Forge's level-bounded walk; {@code maxArtifacts} caps the
 * per-artifact registry and database work the walk's result triggers.
 */
@Validated
@ConfigurationProperties(prefix = "dataset.closure-validation")
public record PipelineClosureValidationProperties(
    @Positive @DefaultValue("10") int maxDepth, @Positive @DefaultValue("500") int maxArtifacts) {}
