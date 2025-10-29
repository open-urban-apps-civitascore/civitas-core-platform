package de.civitascore.portal.model.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class NamedEntity<ID extends Serializable> extends TenantAwareEntity<ID> {

  @NotBlank @Column(nullable = false)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;
}
