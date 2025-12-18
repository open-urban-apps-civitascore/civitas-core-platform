package de.civitascore.portal.controller;

import java.io.StringWriter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

public abstract class DcatController {

  public String toJsonLd(Model model) {
    StringWriter writer = new StringWriter();
    RDFDataMgr.write(writer, model, Lang.JSONLD);
    return writer.toString();
  }
}
