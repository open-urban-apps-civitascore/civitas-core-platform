package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.NamedEntity;
import jakarta.persistence.CascadeType;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "groups",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_group_name",
            columnNames = {"name"}),
    indexes = {@Index(name = "idx_group_contact", columnList = "contact_user_id")})
@Getter
@Setter
public class Group extends NamedEntity {

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "group_members",
      joinColumns = @JoinColumn(name = "group_id"),
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  private Set<User> members = new HashSet<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contact_user_id")
  private User contactUser;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_group_id")
  private Group parentGroup;

  @OneToMany(
      mappedBy = "parentGroup",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Setter(AccessLevel.NONE) // Custom setter needed for orphanRemoval
  private Set<Group> childGroups = new HashSet<>();

  @OneToMany(mappedBy = "group", fetch = FetchType.LAZY)
  private Set<Assignment> assignments = new HashSet<>();

  /**
   * Custom setter for childGroups to properly handle orphanRemoval. Hibernate requires that the
   * collection instance remains the same, while only its contents are modified.
   */
  public void setChildGroups(Collection<Group> childGroups) {
    this.childGroups.clear();
    if (childGroups != null) {
      this.childGroups.addAll(childGroups);
    }
  }

  /**
   * Recursively collects all child groups in the hierarchy.
   *
   * @return a set of all child groups
   */
  // TODO SECURITY (low): No cycle detection — a cycle in parentGroup/childGroups
  // (e.g. from data corruption) causes unbounded recursion (StackOverflowError).
  // Also triggers N+1 lazy-loading queries per recursion level. Consider adding a
  // visited-set or depth limit if group hierarchies grow.
  public Set<Group> getChildGroupsRecursive() {
    Set<Group> allChildGroups = new HashSet<>(childGroups);
    for (Group child : childGroups) {
      allChildGroups.addAll(child.getChildGroupsRecursive());
    }
    return allChildGroups;
  }
}
