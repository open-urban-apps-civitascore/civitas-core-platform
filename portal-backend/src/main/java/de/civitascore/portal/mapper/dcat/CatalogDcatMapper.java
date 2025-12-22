package de.civitascore.portal.mapper.dcat;

import static de.civitascore.portal.util.Namespaces.DCAT;
import static de.civitascore.portal.util.Namespaces.DCT;

import de.civitascore.portal.model.output.CatalogOutputDTO;
import java.util.Optional;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.springframework.stereotype.Component;

@Component
public class CatalogDcatMapper extends DcatMapper<CatalogOutputDTO> {

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
        .map(childCatalog -> model.createResource(childCatalog.getId()))
        .forEach(
            childCatalogRes ->
                catalogResource.addProperty(model.createProperty(DCT, "hasPart"), childCatalogRes));

    dto.getParentCatalogs().stream()
        .map(parentCatalog -> model.createResource(parentCatalog.getId()))
        .forEach(
            parentCatalogRes ->
                catalogResource.addProperty(
                    model.createProperty(DCT, "isPartOf"), parentCatalogRes));

    dto.getDataSets().stream()
        .map(dataSet -> model.createResource(dataSet.getId()))
        .forEach(
            dataSetRes ->
                catalogResource.addProperty(model.createProperty(DCAT, "dataset"), dataSetRes));

    return model;
  }
}
