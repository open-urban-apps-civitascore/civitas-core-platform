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
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "groups",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_group_name_tenant",
            columnNames = {"title", "tenant_id"}),
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

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "group_system_roles",
      joinColumns = @JoinColumn(name = "group_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  private Set<Role> systemRoles = new HashSet<>();

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
  private Set<Group> childGroups = new HashSet<>();
}
