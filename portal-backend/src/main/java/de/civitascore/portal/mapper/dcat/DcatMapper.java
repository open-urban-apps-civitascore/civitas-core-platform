package de.civitascore.portal.mapper.dcat;

import de.civitascore.portal.model.output.BaseOutputDTO;
import de.civitascore.portal.util.Namespaces;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 * Abstract base class for DCAT-AP mappers that convert output DTOs to Apache Jena RDF models.
 * Provides a pre-configured empty model with standard DCAT namespace prefixes.
 *
 * @param <T> the output DTO type to convert
 */
public abstract class DcatMapper<T extends BaseOutputDTO> {

  /**
   * Creates an empty Jena RDF model pre-configured with standard DCAT-AP namespace prefixes (dcat,
   * dct, foaf, dcatde).
   *
   * @return a new empty model with namespace prefixes set
   */
  public Model createEmptyModel() {
    Model model = ModelFactory.createDefaultModel();
    model.setNsPrefix("dcat", Namespaces.DCAT);
    model.setNsPrefix("dct", Namespaces.DCT);
    model.setNsPrefix("foaf", Namespaces.FOAF);
    model.setNsPrefix("dcatde", Namespaces.DCAT_DE);
    return model;
  }

  /**
   * Converts the given output DTO to an Apache Jena RDF model following the DCAT-AP specification.
   *
   * @param dto the output DTO to convert
   * @return the RDF model representation
   */
  public abstract Model toModel(T dto);
}
