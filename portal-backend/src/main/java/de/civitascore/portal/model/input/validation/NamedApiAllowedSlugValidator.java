package de.civitascore.portal.model.input.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validator backing {@link NamedApiAllowedSlug}. Rejects slugs that fail the URL-safe shape regex
 * or hit the reserved-slug blocklist. Builds a context-specific violation message so the consumer
 * sees why a slug was rejected (shape vs. reserved).
 *
 * <p>Reserved slugs would otherwise collide with reserved platform paths under {@code
 * /v1/datasets/{id}/...}. Per concept #1384 + discovery endpoint in #1379:
 *
 * <ul>
 *   <li>{@code apis} — collides with the planned {@code GET /v1/datasets/{id}/apis} discovery
 *       endpoint
 *   <li>{@code api}, {@code v1}, {@code admin} — conservative reserves for future platform
 *       endpoints; refine with the team before lifting
 * </ul>
 *
 * <p>{@link #RESERVED} is a {@link List} so iteration order — and therefore {@code toString()} in
 * the error message — is type-level deterministic across JVMs.
 */
public class NamedApiAllowedSlugValidator
    implements ConstraintValidator<NamedApiAllowedSlug, String> {

  /**
   * Reserved slug blocklist. Insertion order is preserved for deterministic error-message
   * rendering.
   */
  public static final List<String> RESERVED = List.of("apis", "api", "v1", "admin");

  /**
   * URL-safe slug shape regex: lowercase alphanumeric with optional internal hyphens, no leading or
   * trailing hyphen. Combined with {@code @Size(max = 32)} at the DTO layer to cap length. Exposed
   * as a compile-time constant so {@code @Schema(pattern = ...)} on the input DTO can reference the
   * same source of truth.
   */
  public static final String SHAPE_REGEX = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$";

  private static final Pattern SHAPE = Pattern.compile(SHAPE_REGEX);

  @Override
  public boolean isValid(String slug, ConstraintValidatorContext ctx) {
    if (slug == null) {
      return true;
    }
    if (!SHAPE.matcher(slug).matches()) {
      replaceMessage(
          ctx,
          "Slug must be lowercase alphanumeric with internal hyphens (e.g. 'traffic-counter')");
      return false;
    }
    if (RESERVED.contains(slug)) {
      replaceMessage(ctx, "Slug '" + slug + "' is reserved (reserved: " + RESERVED + ")");
      return false;
    }
    return true;
  }

  private static void replaceMessage(ConstraintValidatorContext ctx, String message) {
    ctx.disableDefaultConstraintViolation();
    ctx.buildConstraintViolationWithTemplate(message).addConstraintViolation();
  }
}
