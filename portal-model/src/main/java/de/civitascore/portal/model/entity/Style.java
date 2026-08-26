package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.DataSetOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Represents a named SLD style definition belonging to a {@link DataSet}. */
@Entity
@Table(name = "styles")
@Getter
@Setter
public class Style extends DataSetOwnedEntity {

  @NotBlank @Column(name = "name", nullable = false)
  private String name;

  @NotBlank @Column(name = "sld_content", columnDefinition = "TEXT", nullable = false)
  private String sldContent;
}
