package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.UserTitleType;
import de.civitascore.portal.model.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;

@Entity
@Table(
    name = "users",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_user_email",
            columnNames = {"email"}),
    indexes = {
      @Index(name = "idx_user_email", columnList = "email"),
      @Index(name = "idx_user_active", columnList = "active"),
      @Index(name = "idx_user_external_id", columnList = "external_id")
    })
@Getter
@Setter
public class User extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "title", nullable = false)
  private UserTitleType title = UserTitleType.OTHER;

  @NotBlank @Column(name = "first_name", nullable = false)
  private String firstName;

  @NotBlank @Column(name = "last_name", nullable = false)
  private String lastName;

  @Email @NotBlank @Column(nullable = false)
  private String email;

  @Column(name = "phone")
  private String phone;

  @Column(name = "external_id")
  private String externalId;

  @Column(nullable = false)
  private Boolean active = true;

  @ManyToMany(fetch = FetchType.LAZY, mappedBy = "members")
  private Set<Group> groups = new HashSet<>();

  @Setter(AccessLevel.NONE)
  @Formula("first_name || ' ' || last_name")
  private String fullName;
}
