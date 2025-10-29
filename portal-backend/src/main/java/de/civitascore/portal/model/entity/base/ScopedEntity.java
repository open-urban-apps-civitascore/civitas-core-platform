package de.civitascore.portal.model.entity.base;

import de.civitascore.portal.model.embedded.ScopeType;
import jakarta.persistence.*;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class ScopedEntity<ID extends Serializable> extends TenantAwareEntity<ID> {

  @Enumerated(EnumType.STRING)
  @Column(name = "scope_type", nullable = false)
  private ScopeType scopeType;

  @Column(name = "scope_id")
  private String scopeId;

  @PreUpdate
  protected void validateOnUpdate() {
    validateScope();
  }

  protected void validateScope() {
    if (scopeType == ScopeType.DATASPACE || scopeType == ScopeType.DATASET) {
      if (scopeId == null || scopeId.isBlank()) {
        throw new IllegalStateException("Scope ID is required for scope type: " + scopeType);
      }
    }
  }
}
