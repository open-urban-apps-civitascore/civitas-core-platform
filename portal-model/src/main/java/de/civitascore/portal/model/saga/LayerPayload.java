package de.civitascore.portal.model.saga;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * A WFS/WMS layer carried in the dataset saga trigger payload. The config-adapter consumes these to
 * provision GeoServer feature types after the workspace and datastore exist.
 *
 * <p>This type lives in portal-model so it can be shared by both the portal-backend (which produces
 * the trigger) and the config-adapter (which consumes it).
 *
 * <p>{@code @JsonInclude(NON_NULL)} is required on this nested record: the same annotation on the
 * outer {@link de.civitascore.portal.messaging.saga.SagaTrigger SagaTrigger} records only filters
 * the top-level fields, so null components here would otherwise serialize as literal {@code null}.
 * The config-adapter rejects a literal {@code null} on {@code alternativeStyles} (it must be either
 * absent or a list), so this annotation is load-bearing.
 *
 * <ul>
 *   <li>{@code id} — the layer's portal-backend UUID, used for diagnostic correlation.
 *   <li>{@code layerName} — required; the published WFS/WMS layer name, unique within the
 *       workspace.
 *   <li>{@code nativeName} — optional; the underlying table name in the data source. {@code null}
 *       when the layer is not attached to a POSTGIS sink; the adapter then falls back to the sole
 *       POSTGIS table on the dataset, or to {@code layerName}.
 *   <li>{@code crs} — optional; the coordinate reference system identifier (e.g. {@code
 *       EPSG:4326}). {@code null} lets the adapter apply its {@code DEFAULT_CRS}.
 *   <li>{@code defaultStyle} — optional; the name of a {@link StylePayload} carried on the same
 *       trigger. {@code null} lets GeoServer fall back to its generic style.
 *   <li>{@code alternativeStyles} — optional; further {@link StylePayload} names available on the
 *       layer in addition to the default. {@code null} when none are set.
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LayerPayload(
    String id,
    String layerName,
    String nativeName,
    String crs,
    String defaultStyle,
    List<String> alternativeStyles) {}
