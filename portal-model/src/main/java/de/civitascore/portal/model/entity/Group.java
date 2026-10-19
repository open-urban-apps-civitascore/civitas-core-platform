package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.AssignableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Represents a group of {@link User Users} that can be assigned {@link Role Roles} via {@link
 * Assignment Assignments}. Groups may be nested in a parent-child hierarchy.
 */
@Entity
@Table(
    name = "groups",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_group_name",
          columnNames = {"name"}),
      @UniqueConstraint(
          name = "uk_group_external_id",
          columnNames = {"external_id"})
    },
    indexes = {@Index(name = "idx_group_contact", columnList = "contact_user_id")})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Group extends AssignableEntity {

  @Column(name = "external_id", length = 255)
  private String externalId;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "group_members",
      joinColumns = @JoinColumn(name = "group_id"),
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  @Builder.Default
  private Set<User> members = new HashSet<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contact_user_id")
  private User contactUser;

  @OneToMany(
      mappedBy = "group",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Setter(AccessLevel.NONE)
  @Builder.Default
  private Set<Assignment> assignments = new HashSet<>();

  /** {@inheritDoc} Links the assignment to this group by setting its group reference. */
  @Override
  protected void linkAssignment(Assignment assignment) {
    assignment.setGroup(this);
  }
}
