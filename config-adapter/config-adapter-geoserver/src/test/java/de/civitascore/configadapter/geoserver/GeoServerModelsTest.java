/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.geoserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.model.geoserver.BoundingBox;
import de.civitascore.configadapter.model.geoserver.DataStoreConfig;
import de.civitascore.configadapter.model.geoserver.FeatureTypeConfig;
import de.civitascore.configadapter.model.geoserver.LayerConfig;
import de.civitascore.configadapter.model.geoserver.LayerType;
import de.civitascore.configadapter.model.geoserver.StyleConfig;
import de.civitascore.configadapter.model.geoserver.WorkspaceConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GeoServerModelsTest {

  @Nested
  class WorkspaceConfigTest {

    @Test
    void toApiMapWrapsInWorkspaceKey() {
      WorkspaceConfig ws = new WorkspaceConfig();
      ws.setName("civitas_dataset1");

      Map<String, Object> result = ws.toApiMap();

      assertTrue(result.containsKey("workspace"));
      @SuppressWarnings("unchecked")
      Map<String, Object> inner = (Map<String, Object>) result.get("workspace");
      assertEquals("civitas_dataset1", inner.get("name"));
    }

    @Test
    void toApiMapIncludesIsolatedWhenSet() {
      WorkspaceConfig ws = new WorkspaceConfig();
      ws.setName("myws");
      ws.setIsolated(true);

      @SuppressWarnings("unchecked")
      Map<String, Object> inner = (Map<String, Object>) ws.toApiMap().get("workspace");
      assertEquals(true, inner.get("isolated"));
    }

    @Test
    void toApiMapOmitsNullFields() {
      WorkspaceConfig ws = new WorkspaceConfig();
      ws.setName("myws");

      @SuppressWarnings("unchecked")
      Map<String, Object> inner = (Map<String, Object>) ws.toApiMap().get("workspace");
      assertFalse(inner.containsKey("isolated"));
    }

    @Test
    void emptyWorkspaceProducesEmptyInnerMap() {
      @SuppressWarnings("unchecked")
      Map<String, Object> inner =
          (Map<String, Object>) new WorkspaceConfig().toApiMap().get("workspace");
      assertTrue(inner.isEmpty());
    }
  }

  @Nested
  class DataStoreConfigTest {

    @Test
    void toApiMapWrapsInDataStoreKey() {
      DataStoreConfig ds = new DataStoreConfig();
      ds.setName("civitas_postgis");

      assertTrue(ds.toApiMap().containsKey("dataStore"));
    }

    @Test
    void toApiMapIncludesDefaultTypePostGIS() {
      DataStoreConfig ds = new DataStoreConfig();
      ds.setName("myds");

      @SuppressWarnings("unchecked")
      Map<String, Object> inner = (Map<String, Object>) ds.toApiMap().get("dataStore");
      assertEquals("PostGIS", inner.get("type"));
    }

    @Test
    void toApiMapBuildsConnectionParametersEntryList() {
      DataStoreConfig ds = new DataStoreConfig();
      ds.setName("myds");
      ds.setHost("db.example.com");
      ds.setPort("5432");
      ds.setDatabase("civitas_geo");
      ds.setSchema("public");
      ds.setUser("geo_user");
      ds.setPasswd("secret");
      ds.setExposePrimaryKeys(true);

      @SuppressWarnings("unchecked")
      Map<String, Object> inner = (Map<String, Object>) ds.toApiMap().get("dataStore");
      assertNotNull(inner.get("connectionParameters"));

      @SuppressWarnings("unchecked")
      Map<String, Object> connParams = (Map<String, Object>) inner.get("connectionParameters");
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> entries = (List<Map<String, Object>>) connParams.get("entry");

      assertNotNull(entries);
      assertTrue(
          entries.stream()
              .anyMatch(e -> "host".equals(e.get("@key")) && "db.example.com".equals(e.get("$"))));
      assertTrue(
          entries.stream()
              .anyMatch(e -> "port".equals(e.get("@key")) && "5432".equals(e.get("$"))));
      assertTrue(
          entries.stream()
              .anyMatch(e -> "database".equals(e.get("@key")) && "civitas_geo".equals(e.get("$"))));
      assertTrue(
          entries.stream()
              .anyMatch(e -> "dbtype".equals(e.get("@key")) && "postgis".equals(e.get("$"))));
      assertTrue(
          entries.stream()
              .anyMatch(
                  e -> "Expose primary keys".equals(e.get("@key")) && "true".equals(e.get("$"))));
    }

    @Test
    void toApiMapOmitsConnectionParametersWhenAllNull() {
      DataStoreConfig ds = new DataStoreConfig();
      ds.setName("myds");
      ds.setDbtype(null);

      @SuppressWarnings("unchecked")
      Map<String, Object> inner = (Map<String, Object>) ds.toApiMap().get("dataStore");
      assertFalse(inner.containsKey("connectionParameters"));
    }
  }

  @Nested
  class FeatureTypeConfigTest {

    @Test
    void toApiMapWrapsInFeatureTypeKey() {
      FeatureTypeConfig ft = new FeatureTypeConfig();
      ft.setName("traffic_counts");
      ft.setNativeName("traffic_counts");
      ft.setSrs("EPSG:4326");

      Map<String, Object> result = ft.toApiMap();

      assertTrue(result.containsKey("featureType"));
      @SuppressWarnings("unchecked")
      Map<String, Object> inner = (Map<String, Object>) result.get("featureType");
      assertEquals("traffic_counts", inner.get("name"));
      assertEquals("traffic_counts", inner.get("nativeName"));
      assertEquals("EPSG:4326", inner.get("srs"));
    }

    @Test
    void toApiMapMapsAbstractFieldToJsonKey() {
      FeatureTypeConfig ft = new FeatureTypeConfig();
      ft.setName("myft");
      ft.setAbstractText("A test feature type");

      @SuppressWarnings("unchecked")
      Map<String, Object> inner = (Map<String, Object>) ft.toApiMap().get("featureType");
      assertEquals("A test feature type", inner.get("abstract"));
      assertFalse(inner.containsKey("abstractText"));
    }

    @Test
    void toApiMapIncludesBoundingBox() {
      FeatureTypeConfig ft = new FeatureTypeConfig();
      ft.setName("myft");
      ft.setNativeBoundingBox(new BoundingBox(-180.0, 180.0, -90.0, 90.0, "EPSG:4326"));
      ft.setLatLonBoundingBox(new BoundingBox(-180.0, 180.0, -90.0, 90.0, "EPSG:4326"));

      @SuppressWarnings("unchecked")
      Map<String, Object> inner = (Map<String, Object>) ft.toApiMap().get("featureType");
      @SuppressWarnings("unchecked")
      Map<String, Object> nativeBbox = (Map<String, Object>) inner.get("nativeBoundingBox");
      assertEquals(-180.0, nativeBbox.get("minx"));
      assertEquals("EPSG:4326", nativeBbox.get("crs"));
    }

    @Test
    void toApiMapIncludesProjectionPolicy() {
      FeatureTypeConfig ft = new FeatureTypeConfig();
      ft.setName("myft");
      ft.setProjectionPolicy("REPROJECT_TO_DECLARED");

      @SuppressWarnings("unchecked")
      Map<String, Object> inner = (Map<String, Object>) ft.toApiMap().get("featureType");
      assertEquals("REPROJECT_TO_DECLARED", inner.get("projectionPolicy"));
    }
  }

  @Nested
  class LayerConfigTest {

    @Test
    void toApiMapWrapsInLayerKey() {
      LayerConfig layer = new LayerConfig();
      layer.setName("traffic_counts");
      layer.setType(LayerType.VECTOR);
      layer.setDefaultStyle("traffic_style");

      Map<String, Object> result = layer.toApiMap();

      assertTrue(result.containsKey("layer"));
      @SuppressWarnings("unchecked")
      Map<String, Object> inner = (Map<String, Object>) result.get("layer");
      assertEquals("traffic_counts", inner.get("name"));
      assertEquals("VECTOR", inner.get("type"));
    }

    @Test
    void toApiMapWrapsDefaultStyleInNameObject() {
      LayerConfig layer = new LayerConfig();
      layer.setName("myLayer");
      layer.setDefaultStyle("my_style");

      @SuppressWarnings("unchecked")
      Map<String, Object> inner = (Map<String, Object>) layer.toApiMap().get("layer");
      @SuppressWarnings("unchecked")
      Map<String, Object> styleRef = (Map<String, Object>) inner.get("defaultStyle");
      assertEquals("my_style", styleRef.get("name"));
    }

    @Test
    void toApiMapOmitsNullDefaultStyle() {
      LayerConfig layer = new LayerConfig();
      layer.setName("myLayer");

      @SuppressWarnings("unchecked")
      Map<String, Object> inner = (Map<String, Object>) layer.toApiMap().get("layer");
      assertFalse(inner.containsKey("defaultStyle"));
    }
  }

  @Nested
  class StyleConfigTest {

    @Test
    void toApiMapWrapsInStyleKey() {
      StyleConfig style = new StyleConfig();
      style.setName("traffic_style");
      style.setFilename("traffic_style.sld");
      style.setFormat("sld");

      Map<String, Object> result = style.toApiMap();

      assertTrue(result.containsKey("style"));
      @SuppressWarnings("unchecked")
      Map<String, Object> inner = (Map<String, Object>) result.get("style");
      assertEquals("traffic_style", inner.get("name"));
      assertEquals("traffic_style.sld", inner.get("filename"));
      assertEquals("sld", inner.get("format"));
    }

    @Test
    void toApiMapWrapsLanguageVersionInVersionObject() {
      StyleConfig style = new StyleConfig();
      style.setName("mystyle");
      style.setLanguageVersion("1.0.0");

      @SuppressWarnings("unchecked")
      Map<String, Object> inner = (Map<String, Object>) style.toApiMap().get("style");
      @SuppressWarnings("unchecked")
      Map<String, Object> langVersion = (Map<String, Object>) inner.get("languageVersion");
      assertEquals("1.0.0", langVersion.get("version"));
    }

    @Test
    void toApiMapWrapsWorkspaceInNameObjectForScopedStyles() {
      StyleConfig style = new StyleConfig();
      style.setName("scoped_style");
      style.setWorkspace("civitas_dataset1");

      @SuppressWarnings("unchecked")
      Map<String, Object> inner = (Map<String, Object>) style.toApiMap().get("style");
      @SuppressWarnings("unchecked")
      Map<String, Object> wsRef = (Map<String, Object>) inner.get("workspace");
      assertEquals("civitas_dataset1", wsRef.get("name"));
    }

    @Test
    void toApiMapOmitsWorkspaceForGlobalStyles() {
      StyleConfig style = new StyleConfig();
      style.setName("global_style");

      @SuppressWarnings("unchecked")
      Map<String, Object> inner = (Map<String, Object>) style.toApiMap().get("style");
      assertFalse(inner.containsKey("workspace"));
    }
  }

  @Nested
  class BoundingBoxTest {

    @Test
    void toMapIncludesAllFields() {
      BoundingBox bbox = new BoundingBox(-180.0, 180.0, -90.0, 90.0, "EPSG:4326");
      Map<String, Object> result = bbox.toMap();

      assertEquals(-180.0, result.get("minx"));
      assertEquals(180.0, result.get("maxx"));
      assertEquals(-90.0, result.get("miny"));
      assertEquals(90.0, result.get("maxy"));
      assertEquals("EPSG:4326", result.get("crs"));
    }

    @Test
    void toMapOmitsNullFields() {
      BoundingBox bbox = new BoundingBox(null, null, null, null, "EPSG:4326");
      Map<String, Object> result = bbox.toMap();

      assertFalse(result.containsKey("minx"));
      assertEquals("EPSG:4326", result.get("crs"));
    }
  }
}
