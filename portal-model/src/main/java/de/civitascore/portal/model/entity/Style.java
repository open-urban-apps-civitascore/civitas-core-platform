package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Represents a named SLD style definition belonging to a {@link DataSet}. */
@Entity
@Table(name = "styles")
@Getter
@Setter
public class Style extends BaseEntity implements DataSetOwned {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dataset_id", nullable = false)
  @NotNull private DataSet dataSet;

  @NotBlank @Column(name = "name", nullable = false)
  private String name;

  @NotBlank @Column(name = "sld_content", columnDefinition = "TEXT", nullable = false)
  private String sldContent;
}
