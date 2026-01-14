package de.civitascore.portal.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
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

@Slf4j
@DisplayName("CatalogDcatController TestContainer Integration Tests")
class CatalogDcatControllerIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private CatalogRepository catalogRepository;

  @Autowired private DataSetRepository dataSetRepository;

  private UUID catalogId1;

  private final String expectedCatalog1Response =
      """
          {
              "@id": "afc6fa68-b7ac-4c2f-a59a-2d152f68ae35",
              "dcat:dataset": {
                  "@id": "549f9bbc-f9bc-4860-afbc-f176576e996d"
              },
              "dct:isPartOf": {
                  "@id": "753ebfdd-b6da-46f0-8dc7-016edb5eb857"
              },
              "dct:description": {
                  "@language": "de",
                  "@value": "This is the first test catalog for DCAT transformation"
              },
              "dct:title": {
                  "@language": "de",
                  "@value": "Test Catalog 1"
              },
              "@type": "dcat:Catalog",
              "@context": {
                  "dct": "http://purl.org/dc/terms/",
                  "dcat": "http://www.w3.org/ns/dcat#",
                  "dcatde": "http://dcat-ap.de/def/dcatde/",
                  "foaf": "http://xmlns.com/foaf/0.1/"
              }
          }
          """;

  @BeforeEach
  void initTestData() {
    Catalog catalog1 = new Catalog();
    catalog1.setName("Test Catalog 1");
    catalog1.setDescription("This is the first test catalog for DCAT transformation");

    DataSet dataSet1 = new DataSet();
    dataSet1.setName("Test Dataset 1");
    dataSet1.setDescription("A test dataset associated with catalog 1");
    dataSet1.setIdentifier("dataset-1-identifier");
    dataSet1.setVersion("1.0.0");
    dataSet1 = dataSetRepository.save(dataSet1);

    catalog1.getDataSets().add(dataSet1);
    catalog1 = catalogRepository.save(catalog1);
    catalogId1 = catalog1.getId();

    Catalog catalog2 = new Catalog();
    catalog2.setName("Test Catalog 2");
    catalog2.setDescription("This is the second test catalog with a parent-child relationship");

    DataSet dataSet2 = new DataSet();
    dataSet2.setName("Test Dataset 2");
    dataSet2.setDescription("A test dataset associated with catalog 2");
    dataSet2.setIdentifier("dataset-2-identifier");
    dataSet2.setVersion("2.0.0");
    dataSet2 = dataSetRepository.save(dataSet2);

    catalog2.getDataSets().add(dataSet2);
    catalog2.getChildCatalogs().add(catalog1);

    catalogRepository.save(catalog2);
  }

  @AfterEach
  void cleanup() {
    catalogRepository.deleteAll();
    dataSetRepository.deleteAll();
  }

  @Test
  @DisplayName("Should retrieve catalog as JSON-LD from DCAT endpoint")
  void shouldGetCatalogAsJsonLd() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getValidAccessToken());

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/catalogs/" + catalogId1, HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).as("Should return OK status").isEqualTo(HttpStatus.OK);

    assertThat(response.getBody()).as("Response body should not be null").isNotNull();
    assertThat(expectedCatalog1Response.equals(response.getBody()));
  }
}
