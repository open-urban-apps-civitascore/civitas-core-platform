package de.civitascore.portal.model.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Abstract base class for all JPA entities, providing a UUID primary key and standard audit fields
 * (created/modified timestamps and user references).
 */
@Getter
@Setter
@SuperBuilder
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor
public abstract class BaseEntity implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  @Setter(AccessLevel.PUBLIC)
  protected UUID id;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  protected LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "modified_at")
  protected LocalDateTime modifiedAt;

  @CreatedBy
  @Column(name = "created_by", updatable = false)
  protected UUID createdBy;

  @LastModifiedBy
  @Column(name = "modified_by")
  protected UUID modifiedBy;

  /** {@inheritDoc} Entities are equal if they share the same non-null {@link #id}. */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BaseEntity that = (BaseEntity) o;
    return id != null && id.equals(that.id);
  }

  /** {@inheritDoc} Returns a class-based hash code to remain consistent across entity states. */
  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
