package de.civitascore.portal.model.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class TenantAwareEntity extends BaseEntity {

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private String tenantId;

  @PrePersist
  protected void validateBeforePersist() {
    if (tenantId == null) {
      throw new IllegalStateException("Tenant ID must be set before persisting entity");
    }
  }
}
