package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.model.entity.Catalog;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.repository.CatalogRepository;
import de.civitascore.portal.repository.DataSetRepository;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@Slf4j
@ActiveProfiles({"test-integration", "preview"})
@DisplayName("CatalogDcatController TestContainer Integration Tests")
class CatalogControllerIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired protected PortalTestDataFactory portalData;

  @Autowired private CatalogRepository catalogRepository;

  @Autowired private DataSetRepository dataSetRepository;

  @Autowired private ObjectMapper objectMapper;

  private UUID catalogId1;
  private UUID catalogId2;
  private UUID dataSetId1;
  private String catalog1CreatedAt;
  private String catalog1ModifiedAt;

  @BeforeEach
  void initTestData() {
    DataSet dataSet1 =
        portalData
            .dataSet()
            .withName("Test Dataset 1")
            .withDescription("A test dataset associated with catalog 1")
            .build();
    dataSetId1 = dataSet1.getId();

    Catalog catalog1 = new Catalog();
    catalog1.setName("Test Catalog 1");
    catalog1.setDescription("This is the first test catalog for DCAT transformation");
    catalog1.getDataSets().add(dataSet1);
    catalog1 = catalogRepository.save(catalog1);
    catalogId1 = catalog1.getId();
    catalog1CreatedAt = catalog1.getCreatedAt().toString();
    catalog1ModifiedAt = catalog1.getModifiedAt().toString();

    Catalog catalog2 = new Catalog();
    catalog2.setName("Test Catalog 2");
    catalog2.setDescription("This is the second test catalog with a parent-child relationship");
    catalog2.getChildCatalogs().add(catalog1);
    catalog2 = catalogRepository.save(catalog2);
    catalogId2 = catalog2.getId();
  }

  @AfterEach
  void cleanup() {
    portalData.cleanAll();
  }

  @Test
  @DisplayName("Should retrieve catalog as JSON")
  void shouldGetCatalogAsJson() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Accept", "application/json");
    headers.setBearerAuth(getValidAccessToken());

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/catalogs/" + catalogId1, HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).as("Response body should not be null").isNotNull();

    // Parse JSON and validate field by field
    var actual = objectMapper.readTree(response.getBody());

    assertThat(actual.get("id").asText()).isEqualTo(catalogId1.toString());
    assertThat(actual.get("name").asText()).isEqualTo("Test Catalog 1");
    assertThat(actual.get("description").asText())
        .isEqualTo("This is the first test catalog for DCAT transformation");

    // Verify timestamps (ignoring nanosecond precision)
    assertThat(actual.get("createdAt").asText()).startsWith(catalog1CreatedAt.substring(0, 19));
    assertThat(actual.get("modifiedAt").asText()).startsWith(catalog1ModifiedAt.substring(0, 19));

    // Verify empty childCatalogs array
    assertThat(actual.get("childCatalogs").isArray()).isTrue();
    assertThat(actual.get("childCatalogs").size()).isEqualTo(0);

    // Verify parentCatalogs
    assertThat(actual.get("parentCatalogs").isArray()).isTrue();
    assertThat(actual.get("parentCatalogs").size()).isEqualTo(1);
    assertThat(actual.get("parentCatalogs").get(0).get("id").asText())
        .isEqualTo(catalogId2.toString());
    assertThat(actual.get("parentCatalogs").get(0).get("name").asText())
        .isEqualTo("Test Catalog 2");

    // Verify dataSets
    assertThat(actual.get("dataSets").isArray()).isTrue();
    assertThat(actual.get("dataSets").size()).isEqualTo(1);
    assertThat(actual.get("dataSets").get(0).get("id").asText()).isEqualTo(dataSetId1.toString());
    assertThat(actual.get("dataSets").get(0).get("name").asText()).isEqualTo("Test Dataset 1");
  }

  @Test
  @DisplayName("Should retrieve catalog as JSON-LD from DCAT endpoint")
  void shouldGetCatalogAsJsonLd() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Accept", "application/ld+json");
    headers.setBearerAuth(getValidAccessToken());

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/catalogs/" + catalogId1, HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).as("Response body should not be null").isNotNull();

    // Parse JSON-LD and validate structure
    var actual = objectMapper.readTree(response.getBody());

    // Verify @context
    var context = actual.get("@context");
    assertThat(context).as("@context should exist").isNotNull();
    assertThat(context.get("dct").asText()).isEqualTo("http://purl.org/dc/terms/");
    assertThat(context.get("dcat").asText()).isEqualTo("http://www.w3.org/ns/dcat#");
    assertThat(context.get("dcatde").asText()).isEqualTo("http://dcat-ap.de/def/dcatde/");
    assertThat(context.get("foaf").asText()).isEqualTo("http://xmlns.com/foaf/0.1/");

    // Verify @graph array
    var graph = actual.get("@graph");
    assertThat(graph).as("@graph should exist").isNotNull();
    assertThat(graph.isArray()).as("@graph should be an array").isTrue();
    assertThat(graph.size()).as("@graph should contain 3 nodes").isEqualTo(3);

    // Find nodes by @id (order may vary)
    var catalogNode = findNodeById(graph, catalogId1.toString());
    var datasetNode = findNodeById(graph, dataSetId1.toString());
    var parentCatalogNode = findNodeById(graph, catalogId2.toString());

    // Verify main catalog node
    assertThat(catalogNode).as("Catalog node should exist").isNotNull();
    assertThat(catalogNode.get("@type").asText()).isEqualTo("dcat:Catalog");
    assertThat(catalogNode.get("dct:description").asText())
        .isEqualTo("This is the first test catalog for DCAT transformation");
    assertThat(catalogNode.get("dct:title").get("@language").asText()).isEqualTo("de");
    assertThat(catalogNode.get("dct:title").get("@value").asText()).isEqualTo("Test Catalog 1");
    assertThat(catalogNode.get("dcat:dataset").get("@id").asText())
        .isEqualTo(dataSetId1.toString());
    assertThat(catalogNode.get("dct:isPartOf").get("@id").asText())
        .isEqualTo(catalogId2.toString());

    // Verify timestamps (ignoring nanosecond precision)
    var createdAtValue =
        catalogNode.get("urn:field:CatalogOutputDTO#createdAt").get("@value").asText();
    var modifiedAtValue =
        catalogNode.get("urn:field:CatalogOutputDTO#modifiedAt").get("@value").asText();
    assertThat(createdAtValue).startsWith(catalog1CreatedAt.substring(0, 19));
    assertThat(modifiedAtValue).startsWith(catalog1ModifiedAt.substring(0, 19));
    assertThat(catalogNode.get("urn:field:CatalogOutputDTO#createdAt").get("@type").asText())
        .isEqualTo("java:java.time.LocalDateTime");
    assertThat(catalogNode.get("urn:field:CatalogOutputDTO#modifiedAt").get("@type").asText())
        .isEqualTo("java:java.time.LocalDateTime");

    // Verify dataset node
    assertThat(datasetNode).as("Dataset node should exist").isNotNull();
    assertThat(datasetNode.get("urn:field:DataSetSummaryDTO#name").asText())
        .isEqualTo("Test Dataset 1");

    // Verify parent catalog node
    assertThat(parentCatalogNode).as("Parent catalog node should exist").isNotNull();
    assertThat(parentCatalogNode.get("urn:field:CatalogSummaryDTO#name").asText())
        .isEqualTo("Test Catalog 2");
  }

  private com.fasterxml.jackson.databind.JsonNode findNodeById(
      com.fasterxml.jackson.databind.JsonNode graph, String id) {
    for (var node : graph) {
      if (node.has("@id") && node.get("@id").asText().equals(id)) {
        return node;
      }
    }
    return null;
  }
}
