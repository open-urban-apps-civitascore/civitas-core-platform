package de.civitascore.portal.model.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Abstract entity that adds a mandatory {@code name} and an optional {@code description} to {@link
 * BaseEntity}.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@MappedSuperclass
public abstract class NamedEntity extends BaseEntity {

  @NotBlank @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;
}
