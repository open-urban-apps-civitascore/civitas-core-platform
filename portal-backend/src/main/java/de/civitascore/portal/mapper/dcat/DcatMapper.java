package de.civitascore.portal.mapper.dcat;

import de.civitascore.portal.model.output.BaseOutputDTO;
import de.civitascore.portal.util.Namespaces;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

public abstract class DcatMapper<T extends BaseOutputDTO> {

  public Model createEmptyModel() {
    Model model = ModelFactory.createDefaultModel();
    model.setNsPrefix("dcat", Namespaces.DCAT);
    model.setNsPrefix("dct", Namespaces.DCT);
    model.setNsPrefix("foaf", Namespaces.FOAF);
    model.setNsPrefix("dcatde", Namespaces.DCAT_DE);
    return model;
  }

  public abstract Model toModel(T dto);
}
