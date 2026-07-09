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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Formula;

/**
 * Represents a platform user with personal information, authentication details, and {@link Group}
 * memberships. Manages the bidirectional relationship with groups.
 *
 * @see Group
 * @see UserTitleType
 */
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
@SuperBuilder
@NoArgsConstructor
public class User extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "title", nullable = false)
  @Builder.Default
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
  @Builder.Default
  private Boolean active = true;

  @ManyToMany(fetch = FetchType.LAZY, mappedBy = "members")
  @Setter(AccessLevel.NONE) // setGroups overwritten to handle the bidirectional relationship
  @Builder.Default
  private Set<Group> groups = new HashSet<>();

  @Setter(AccessLevel.NONE)
  @Formula("first_name || ' ' || last_name")
  private String fullName;

  /**
   * Adds a group to the user's memberships. Since Group owns the ManyToMany relationship (via
   * group_members join table), this method properly updates both sides of the bidirectional
   * relationship.
   *
   * @param group the group to add
   */
  public void addGroup(Group group) {
    if (group != null && !this.groups.contains(group)) {
      group.getMembers().add(this);
      this.groups.add(group);
    }
  }

  /**
   * Removes a group from the user's memberships. Since Group owns the ManyToMany relationship (via
   * group_members join table), this method properly updates both sides of the bidirectional
   * relationship.
   *
   * @param group the group to remove
   */
  public void removeGroup(Group group) {
    if (group != null && this.groups.contains(group)) {
      group.getMembers().remove(this);
      this.groups.remove(group);
    }
  }

  /**
   * Replaces all of the user's group memberships with the provided set of groups. This method uses
   * {@link #addGroup} and {@link #removeGroup} to properly handle the bidirectional relationship.
   *
   * @param newGroups the new set of groups the user should belong to
   */
  public void setGroups(Set<Group> newGroups) {
    Set<Group> groupsToProcess = newGroups != null ? newGroups : new HashSet<>();

    // Remove groups that are no longer needed
    Set<Group> groupsToRemove = new HashSet<>(this.groups);
    groupsToRemove.removeAll(groupsToProcess);
    for (Group group : groupsToRemove) {
      removeGroup(group);
    }

    // Add new groups
    for (Group group : groupsToProcess) {
      addGroup(group);
    }
  }
}
