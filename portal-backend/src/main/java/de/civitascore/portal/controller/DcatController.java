package de.civitascore.portal.controller;

import java.io.StringWriter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

/**
 * Abstract base controller providing DCAT/RDF serialization utilities.
 *
 * <p>Subclasses can use {@link #toJsonLd(Model)} to convert an Apache Jena {@link Model} into its
 * JSON-LD string representation.
 */
public abstract class DcatController {

  /**
   * Serializes an Apache Jena RDF model to a JSON-LD string.
   *
   * @param model the Jena RDF model to serialize
   * @return the JSON-LD representation of the model
   */
  public String toJsonLd(Model model) {
    StringWriter writer = new StringWriter();
    RDFDataMgr.write(writer, model, Lang.JSONLD);
    return writer.toString();
  }
}
