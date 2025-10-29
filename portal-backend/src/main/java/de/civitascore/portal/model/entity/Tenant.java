package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "tenants",
    indexes = {
      @Index(name = "idx_tenant_name", columnList = "name"),
      @Index(name = "idx_tenant_active", columnList = "active")
    })
@Getter
@Setter
public class Tenant extends BaseEntity<String> {

  @NotBlank @Column(nullable = false, unique = true)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false)
  private Boolean active = true;

  @Column(name = "external_id")
  private String externalId;

  @Column(name = "settings", columnDefinition = "TEXT")
  private String settings;
}
