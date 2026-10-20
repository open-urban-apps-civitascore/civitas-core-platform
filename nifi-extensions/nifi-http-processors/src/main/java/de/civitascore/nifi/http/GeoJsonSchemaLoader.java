/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.http;

import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.resource.InputStreamSource;
import com.networknt.schema.resource.SchemaLoader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Serves the GeoJSON geometry schemas from the copies in the NAR, so that a Data structure keeps
 * its {@code $ref} to {@code https://geojson.org/schema/<Type>.json} and NiFi never fetches it.
 *
 * <p>The map is exact and not a prefix: a relative segment in a reference would otherwise read any
 * resource of the class path.
 */
final class GeoJsonSchemaLoader implements SchemaLoader {

  private static final Map<String, String> RESOURCES =
      Map.of(
          iri("Point"), resource("Point"),
          iri("LineString"), resource("LineString"),
          iri("Polygon"), resource("Polygon"),
          iri("MultiPoint"), resource("MultiPoint"),
          iri("MultiLineString"), resource("MultiLineString"),
          iri("MultiPolygon"), resource("MultiPolygon"),
          iri("GeometryCollection"), resource("GeometryCollection"));

  @Override
  public InputStreamSource getSchema(AbsoluteIri iri) {
    String resource = iri == null ? null : RESOURCES.get(iri.toString());
    return resource == null ? null : () -> open(resource);
  }

  private static InputStream open(String resource) throws IOException {
    InputStream stream = GeoJsonSchemaLoader.class.getResourceAsStream(resource);
    if (stream == null) {
      throw new IOException("The NAR holds no " + resource);
    }
    return stream;
  }

  private static String iri(String type) {
    return "https://geojson.org/schema/" + type + ".json";
  }

  private static String resource(String type) {
    return "/geojson/" + type + ".json";
  }
}
