package de.civitascore.portal.model.entity.base;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class TenantAwareEntity<ID extends Serializable> extends BaseEntity<ID> {

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private String tenantId;

  @PrePersist
  protected void validateBeforePersist() {
    if (tenantId == null) {
      throw new IllegalStateException("Tenant ID must be set before persisting entity");
    }
  }
}
