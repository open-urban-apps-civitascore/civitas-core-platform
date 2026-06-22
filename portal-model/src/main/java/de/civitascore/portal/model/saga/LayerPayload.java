package de.civitascore.portal.model.saga;

/**
 * A WFS/WMS layer carried in the dataset saga trigger payload. The config-adapter consumes these to
 * provision GeoServer feature types after the workspace and datastore exist.
 *
 * <p>This type lives in portal-model so it can be shared by both the portal-backend (which produces
 * the trigger) and the config-adapter (which consumes it).
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
 * </ul>
 */
public record LayerPayload(String id, String layerName, String nativeName, String crs) {}
