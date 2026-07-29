package de.civitascore.portal.model.saga;

/**
 * An SLD style carried at the dataset level in the saga trigger payload. The config-adapter uploads
 * the SLD to GeoServer and layers reference it by {@link #name() name} via {@link
 * LayerPayload#defaultStyle()} and {@link LayerPayload#alternativeStyles()}, so the SLD body is
 * carried only once even when several layers use it.
 *
 * <p>This type lives in portal-model so it can be shared by both the portal-backend (which produces
 * the trigger) and the config-adapter (which consumes it).
 *
 * <ul>
 *   <li>{@code name} — required; the style name unique within the dataset. Layers reference styles
 *       by this name. The consumer enforces a character whitelist on the name; portal-backend's
 *       style entity validation must produce names that satisfy it.
 *   <li>{@code sldContent} — required; the SLD XML body forwarded verbatim to GeoServer. It is
 *       user-supplied — input sanitisation (size limits, XXE guard, well-formedness) is a {@code
 *       StyleService} input concern and lives outside this wire shape.
 * </ul>
 */
public record StylePayload(String name, String sldContent) {}
