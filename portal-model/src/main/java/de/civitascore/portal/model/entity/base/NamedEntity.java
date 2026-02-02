package de.civitascore.portal.model.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class NamedEntity extends BaseEntity {

  @NotBlank @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;
}
