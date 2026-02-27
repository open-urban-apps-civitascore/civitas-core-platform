package de.civitascore.portal.model.entity.base;

import de.civitascore.portal.model.entity.Assignment;
import jakarta.persistence.MappedSuperclass;
import java.util.Set;

@MappedSuperclass
public abstract class BaseDataEntity extends NamedEntity {

  public abstract Set<Assignment> getAssignments();

  public void setAssignments(Set<Assignment> assignments) {
    getAssignments().clear();
    if (assignments != null) {
      getAssignments().addAll(assignments);
      assignments.forEach(this::linkAssignment);
    }
  }

  protected abstract void linkAssignment(Assignment assignment);
}
