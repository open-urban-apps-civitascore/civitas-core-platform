package de.civitascore.portal.configuration;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * How far the participating-artifact walk run before release may follow references, bound from the
 * {@code dataset.closure-validation.*} namespace.
 *
 * <p>The bound exists because the traversal is work performed on a caller's request: a deliberately
 * deep dependency graph must not be usable to occupy the backend. It is passed to Model Forge's
 * level-bounded walk, which the registry requires a caller to state rather than defaulting.
 *
 * <p>It is therefore also a limit on how deep a flow may be and still be released: a flow reaching
 * past the bound is refused, because everything beyond it went unexamined.
 *
 * <p>The same bound limits the walk that collects a flow's artifacts into the saga payload on
 * release and update, so a flow too deep for that walk is refused too rather than published
 * incomplete.
 */
@Validated
@ConfigurationProperties(prefix = "dataset.closure-validation")
public record PipelineClosureValidationProperties(@Positive @DefaultValue("10") int maxDepth) {}
