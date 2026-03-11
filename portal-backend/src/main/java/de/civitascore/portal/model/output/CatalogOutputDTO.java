package de.civitascore.portal.model.output;

import static de.civitascore.portal.util.Namespaces.DCAT;
import static de.civitascore.portal.util.Namespaces.DCT;

import de.civitascore.portal.model.annotations.JsonLDProperty;
import de.civitascore.portal.model.annotations.JsonLDResource;
import de.civitascore.portal.model.output.summary.CatalogSummaryDTO;
import de.civitascore.portal.model.output.summary.DataSetSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "Catalog details")
@Data
@EqualsAndHashCode(callSuper = true)
@JsonLDResource(DCAT + "Catalog")
public class CatalogOutputDTO extends BaseOutputDTO {

  @JsonLDProperty(nameSpace = DCT, localName = "title", language = "de")
  private String name;

  @JsonLDProperty(nameSpace = DCT, localName = "description")
  private String description;

  @JsonLDProperty(nameSpace = DCT, localName = "hasPart")
  private List<CatalogSummaryDTO> childCatalogs = new ArrayList<>();

  @JsonLDProperty(nameSpace = DCT, localName = "isPartOf")
  private List<CatalogSummaryDTO> parentCatalogs = new ArrayList<>();

  @JsonLDProperty(nameSpace = DCAT, localName = "dataset")
  private List<DataSetSummaryDTO> dataSets = new ArrayList<>();
}
