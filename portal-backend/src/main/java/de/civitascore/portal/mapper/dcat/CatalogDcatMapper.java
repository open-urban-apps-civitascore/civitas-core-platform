package de.civitascore.portal.mapper.dcat;

import static de.civitascore.portal.util.Namespaces.DCAT;
import static de.civitascore.portal.util.Namespaces.DCT;

import de.civitascore.portal.model.output.CatalogOutputDTO;
import java.util.Optional;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.springframework.stereotype.Component;

/**
 * DCAT-AP mapper for converting {@link CatalogOutputDTO} to an Apache Jena RDF {@link Model}.
 * Produces DCAT Catalog resources with title, description, child/parent relationships, and dataset
 * references.
 */
@Component
public class CatalogDcatMapper extends DcatMapper<CatalogOutputDTO> {

  /**
   * {@inheritDoc} Produces a DCAT Catalog resource with title, description, {@code dct:hasPart} /
   * {@code dct:isPartOf} relationships, and {@code dcat:dataset} references.
   */
  @Override
  public Model toModel(CatalogOutputDTO dto) {
    Model model = createEmptyModel();
    Resource catalogResource = model.createResource(dto.getId().toString());
    Resource dcatCatalog = model.createResource(DCAT + "Catalog");

    catalogResource.addProperty(RDF.type, dcatCatalog);

    Optional.ofNullable(dto.getName())
        .ifPresent(
            (name) ->
                catalogResource.addProperty(
                    model.createProperty(DCT, "title"), model.createLiteral(name, "de")));

    Optional.ofNullable(dto.getDescription())
        .ifPresent(
            (description) ->
                catalogResource.addProperty(
                    model.createProperty(DCT, "description"),
                    model.createLiteral(description, "de")));

    dto.getChildCatalogs().stream()
        .map(childCatalog -> model.createResource(childCatalog.getId().toString()))
        .forEach(
            childCatalogRes ->
                catalogResource.addProperty(model.createProperty(DCT, "hasPart"), childCatalogRes));

    dto.getParentCatalogs().stream()
        .map(parentCatalog -> model.createResource(parentCatalog.getId().toString()))
        .forEach(
            parentCatalogRes ->
                catalogResource.addProperty(
                    model.createProperty(DCT, "isPartOf"), parentCatalogRes));

    dto.getDataSets().stream()
        .map(dataSet -> model.createResource(dataSet.getId().toString()))
        .forEach(
            dataSetRes ->
                catalogResource.addProperty(model.createProperty(DCAT, "dataset"), dataSetRes));

    return model;
  }
}
