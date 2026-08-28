package de.civitascore.modelforge.validation;

import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.resource.InputStreamSource;
import com.networknt.schema.resource.SchemaLoader;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Resolves a small set of third-party schema documents that ship on the classpath, so a reference to
 * one is honoured without the server fetching anything.
 *
 * <p>Currently the seven GeoJSON geometry schemas. A geometry attribute is authored as a reference to
 * its published GeoJSON schema, and the PostGIS and GeoServer adapters derive a geometry column and a
 * native CRS from that URL — so the reference has to stay in the stored document and still resolve
 * while validating. Vendoring keeps both: the reference means what it says, and the geometry is
 * actually validated.
 *
 * <p>The mapping is exact rather than a prefix: a prefix would let a relative segment in an authored
 * reference walk out of the vendored directory and turn this into a way to read arbitrary classpath
 * resources, whose content would come back inside validation messages. Anything not listed here is
 * left to {@code DisallowSchemaLoader}.
 *
 * <p>An added document must reference no other document — there is nothing to resolve a reference
 * out of a vendored copy with.
 */
final class VendoredSchemaLoader implements SchemaLoader {

    private static final String GEOJSON_IRI_PREFIX = "https://geojson.org/schema/";

    private static final String[] GEOJSON_GEOMETRY_TYPES = {
        "Point", "LineString", "Polygon", "MultiPoint", "MultiLineString", "MultiPolygon",
        "GeometryCollection",
    };

    /** Published IRI to classpath resource, mirroring {@code CoreSchemaValidator}'s schema map. */
    private static final Map<String, String> RESOURCE_FOR_IRI = geoJsonGeometrySchemas();

    private static Map<String, String> geoJsonGeometrySchemas() {
        Map<String, String> resources = new LinkedHashMap<>();
        for (String type : GEOJSON_GEOMETRY_TYPES) {
            resources.put(GEOJSON_IRI_PREFIX + type + ".json", "geojson/" + type + ".json");
        }
        return Map.copyOf(resources);
    }

    /** The IRIs this loader serves, for tests that assert properties of the vendored documents. */
    static Set<String> vendoredIris() {
        return RESOURCE_FOR_IRI.keySet();
    }

    /** The classpath resource behind {@code iri}, or {@code null} if it is not vendored. */
    static String resourceFor(String iri) {
        return RESOURCE_FOR_IRI.get(iri);
    }

    @Override
    public InputStreamSource getSchema(AbsoluteIri iri) {
        String resource = iri == null ? null : RESOURCE_FOR_IRI.get(iri.toString());
        return resource == null ? null : () -> open(resource);
    }

    private static InputStream open(String resource) throws IOException {
        InputStream stream = VendoredSchemaLoader.class.getResourceAsStream("/" + resource);
        if (stream == null) {
            throw new IOException("Missing vendored schema resource: " + resource);
        }
        return stream;
    }
}
